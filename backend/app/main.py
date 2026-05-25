"""
InvestPro Trading API - FastAPI application entry point.
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.api.routes import router

app = FastAPI(
    title=settings.app_name,
    version=settings.version,
    description="AI-powered stock trading backend with predictive signals, power scoring, and conversational analysis",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router, prefix="/api/v1")


@app.get("/health")
async def health():
    return {"status": "healthy", "version": settings.version}


@app.get("/")
async def root():
    return {
        "app": settings.app_name,
        "version": settings.version,
        "endpoints": {
            "quote": "/api/v1/quote/{symbol}",
            "candles": "/api/v1/candles/{symbol}",
            "indicators": "/api/v1/indicators/{symbol}",
            "signals": "/api/v1/signals/{symbol}",
            "power_score": "/api/v1/power-score/{symbol}",
            "sentiment": "/api/v1/sentiment/{symbol}",
            "patterns": "/api/v1/patterns/{symbol}",
            "support_resistance": "/api/v1/support-resistance/{symbol}",
            "ask_ai": "/api/v1/ask?query=...",
            "portfolio": "/api/v1/portfolio",
            "positions": "/api/v1/positions",
            "place_order": "POST /api/v1/orders",
            "scan": "/api/v1/scan?symbols=AAPL,MSFT",
            "ws_quotes": "ws://host/api/v1/ws/quotes",
        },
    }
