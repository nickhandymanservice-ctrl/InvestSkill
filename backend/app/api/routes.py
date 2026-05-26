"""
FastAPI routes - exposes all services to the Android client.

Authentication model:
    - GET endpoints that only return public market data are anonymous.
    - Portfolio / order endpoints require ``X-User-Id`` and look up that
      user's encrypted Webull credentials via ``client_for_user``.
"""
from fastapi import APIRouter, Depends, HTTPException, Query, WebSocket, WebSocketDisconnect
from typing import List, Optional
import asyncio
import json

from app.api.auth_routes import require_user_id
from app.mcp_bridge.webull_bridge import client_for_user
from app.models.market import (
    Quote, Candle, TechnicalIndicators, SupportResistance,
    ChartPattern, TradingSignal, SentimentData, PowerScore,
    AIResponse, TimeFrame, BacktestResult
)
from app.models.trading import (
    Order, OrderRequest, PortfolioSummary, Position
)

router = APIRouter()


# ─── Market Data ─────────────────────────────────────────────────────────────

@router.get("/quote/{symbol}", response_model=Quote)
async def get_quote(symbol: str):
    from app.services.market_data_service import get_quote as _get_quote
    try:
        return _get_quote(symbol)
    except Exception as e:
        raise HTTPException(status_code=404, detail=f"Could not fetch quote for {symbol}: {str(e)}")


@router.get("/candles/{symbol}", response_model=List[Candle])
async def get_candles(
    symbol: str,
    timeframe: TimeFrame = TimeFrame.D1,
    period: str = "6mo"
):
    from app.services.market_data_service import get_candles as _get_candles
    try:
        return _get_candles(symbol, timeframe, period)
    except Exception as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.get("/indicators/{symbol}", response_model=TechnicalIndicators)
async def get_indicators(symbol: str):
    from app.services.market_data_service import get_technical_indicators
    try:
        return get_technical_indicators(symbol)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/support-resistance/{symbol}", response_model=List[SupportResistance])
async def get_support_resistance(symbol: str):
    from app.services.market_data_service import detect_support_resistance
    try:
        return detect_support_resistance(symbol)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/patterns/{symbol}", response_model=List[ChartPattern])
async def get_patterns(symbol: str):
    from app.services.market_data_service import detect_chart_patterns
    try:
        return detect_chart_patterns(symbol)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ─── Signals & Scoring ───────────────────────────────────────────────────────

@router.get("/signals/{symbol}", response_model=List[TradingSignal])
async def get_signals(symbol: str, timeframe: TimeFrame = TimeFrame.D1):
    from app.services.signal_service import generate_signals
    try:
        return generate_signals(symbol, timeframe)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/scan", response_model=List[TradingSignal])
async def scan_market(
    symbols: str = Query(default="AAPL,MSFT,NVDA,TSLA,AMZN,GOOGL,META,AMD,SPY,QQQ"),
    timeframe: TimeFrame = TimeFrame.D1
):
    from app.services.signal_service import scan_market as _scan
    symbol_list = [s.strip() for s in symbols.split(",")]
    try:
        return _scan(symbol_list, timeframe)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/power-score/{symbol}", response_model=PowerScore)
async def get_power_score(symbol: str):
    from app.services.power_score_service import calculate_power_score
    try:
        return calculate_power_score(symbol)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ─── Sentiment ───────────────────────────────────────────────────────────────

@router.get("/sentiment/{symbol}", response_model=SentimentData)
async def get_sentiment(symbol: str):
    from app.services.sentiment_service import get_combined_sentiment
    try:
        return get_combined_sentiment(symbol)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ─── AI Assistant ────────────────────────────────────────────────────────────

@router.post("/ask", response_model=AIResponse)
async def ask_ai(query: str = Query(..., description="Natural language question")):
    from app.services.ai_assistant_service import ask_assistant
    try:
        return ask_assistant(query)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/ask", response_model=AIResponse)
async def ask_ai_get(query: str = Query(..., description="Natural language question")):
    from app.services.ai_assistant_service import ask_assistant
    try:
        return ask_assistant(query)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ─── Portfolio & Trading (per-user, requires X-User-Id) ─────────────────────

@router.get("/portfolio", response_model=PortfolioSummary)
async def get_portfolio(user_id: str = Depends(require_user_id)):
    try:
        return client_for_user(user_id).get_portfolio_summary()
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/positions", response_model=List[Position])
async def get_positions(user_id: str = Depends(require_user_id)):
    try:
        return client_for_user(user_id).get_positions()
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/orders", response_model=Order)
async def place_order(order: OrderRequest, user_id: str = Depends(require_user_id)):
    try:
        return client_for_user(user_id).place_order(order)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/orders/{order_id}")
async def cancel_order(order_id: str, user_id: str = Depends(require_user_id)):
    success = client_for_user(user_id).cancel_order(order_id)
    if not success:
        raise HTTPException(status_code=400, detail="Failed to cancel order")
    return {"status": "cancelled", "order_id": order_id}


# ─── WebSocket for Real-Time Updates ────────────────────────────────────────

class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket: WebSocket):
        self.active_connections.remove(websocket)

    async def broadcast(self, message: str):
        for connection in self.active_connections:
            try:
                await connection.send_text(message)
            except Exception:
                pass


manager = ConnectionManager()


@router.websocket("/ws/quotes")
async def websocket_quotes(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        while True:
            data = await websocket.receive_text()
            symbols = json.loads(data).get("symbols", [])

            from app.services.market_data_service import get_quote as _get_quote
            quotes = []
            for sym in symbols[:10]:
                try:
                    q = _get_quote(sym)
                    quotes.append(q.model_dump(mode="json"))
                except Exception:
                    pass

            await websocket.send_text(json.dumps({"quotes": quotes}))
            await asyncio.sleep(5)
    except WebSocketDisconnect:
        manager.disconnect(websocket)
