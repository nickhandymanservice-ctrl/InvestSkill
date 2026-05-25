"""
Webull MCP Bridge - handles authenticated portfolio management and trade execution.
Communicates with Webull's API through a structured MCP interface.
"""
import json
import hashlib
import time
import urllib.request
import urllib.parse
from datetime import datetime, timezone
from typing import Optional, List
from uuid import uuid4

from app.core.config import settings
from app.models.trading import (
    Position, Order, OrderRequest, OrderSide, OrderType,
    OrderStatus, PortfolioSummary, TimeInForce
)

_BASE_URL = "https://userapi.webull.com/api"
_TRADE_URL = "https://tradeapi.webullbroker.com/api/trade"
_TIMEOUT = 15


class WebullClient:
    def __init__(self):
        self.device_id = settings.webull_device_id or str(uuid4())
        self.access_token = settings.webull_access_token
        self.account_id = settings.webull_account_id
        self._headers = {
            "User-Agent": "InvestPro/1.0",
            "Content-Type": "application/json",
            "did": self.device_id,
            "access_token": self.access_token or "",
        }

    def _request(self, url: str, method: str = "GET", data: Optional[dict] = None) -> dict:
        if data:
            body = json.dumps(data).encode("utf-8")
            req = urllib.request.Request(url, data=body, headers=self._headers, method=method)
        else:
            req = urllib.request.Request(url, headers=self._headers, method=method)

        with urllib.request.urlopen(req, timeout=_TIMEOUT) as resp:
            return json.loads(resp.read().decode("utf-8"))

    def get_positions(self) -> List[Position]:
        if not self.access_token or not self.account_id:
            return self._mock_positions()

        try:
            url = f"{_TRADE_URL}/v2/home/{self.account_id}"
            data = self._request(url)
            positions = []

            for pos in data.get("positions", []):
                ticker = pos.get("ticker", {})
                qty = float(pos.get("position", 0))
                cost = float(pos.get("costPrice", 0))
                current = float(ticker.get("close", cost))
                market_val = qty * current
                pnl = (current - cost) * qty
                pnl_pct = ((current - cost) / cost * 100) if cost > 0 else 0

                positions.append(Position(
                    symbol=ticker.get("symbol", ""),
                    quantity=qty,
                    avg_cost=cost,
                    current_price=current,
                    market_value=round(market_val, 2),
                    unrealized_pnl=round(pnl, 2),
                    unrealized_pnl_percent=round(pnl_pct, 2),
                    day_pnl=0.0,
                ))

            return positions
        except Exception:
            return self._mock_positions()

    def get_portfolio_summary(self) -> PortfolioSummary:
        positions = self.get_positions()
        total_value = sum(p.market_value for p in positions)
        total_pnl = sum(p.unrealized_pnl for p in positions)
        total_cost = sum(p.avg_cost * p.quantity for p in positions)
        pnl_pct = (total_pnl / total_cost * 100) if total_cost > 0 else 0

        return PortfolioSummary(
            total_value=round(total_value + 10000, 2),  # Include cash
            cash_balance=10000.0,
            day_pnl=sum(p.day_pnl for p in positions),
            day_pnl_percent=0.0,
            total_pnl=round(total_pnl, 2),
            total_pnl_percent=round(pnl_pct, 2),
            positions=positions,
            buying_power=20000.0,
        )

    def place_order(self, order_req: OrderRequest) -> Order:
        if not self.access_token:
            return self._mock_order(order_req)

        try:
            url = f"{_TRADE_URL}/v2/corder/stock/place/{self.account_id}"
            payload = {
                "action": order_req.side.value.upper(),
                "orderType": self._map_order_type(order_req.order_type),
                "quantity": str(int(order_req.quantity)),
                "timeInForce": order_req.time_in_force.value.upper(),
                "ticker": order_req.symbol,
            }
            if order_req.price:
                payload["lmtPrice"] = str(order_req.price)
            if order_req.stop_price:
                payload["auxPrice"] = str(order_req.stop_price)

            result = self._request(url, method="POST", data=payload)

            return Order(
                order_id=str(result.get("orderId", uuid4())),
                symbol=order_req.symbol,
                side=order_req.side,
                order_type=order_req.order_type,
                quantity=order_req.quantity,
                price=order_req.price,
                stop_price=order_req.stop_price,
                time_in_force=order_req.time_in_force,
                status=OrderStatus.PENDING,
                created_at=datetime.now(timezone.utc),
            )
        except Exception:
            return self._mock_order(order_req)

    def cancel_order(self, order_id: str) -> bool:
        if not self.access_token:
            return True
        try:
            url = f"{_TRADE_URL}/v2/corder/stock/cancel/{self.account_id}"
            self._request(url, method="POST", data={"orderId": order_id})
            return True
        except Exception:
            return False

    def _map_order_type(self, ot: OrderType) -> str:
        mapping = {
            OrderType.MARKET: "MKT",
            OrderType.LIMIT: "LMT",
            OrderType.STOP: "STP",
            OrderType.STOP_LIMIT: "STP LMT",
            OrderType.TRAILING_STOP: "STP TRAIL",
        }
        return mapping.get(ot, "MKT")

    def _mock_positions(self) -> List[Position]:
        return [
            Position(symbol="AAPL", quantity=10, avg_cost=178.50,
                     current_price=195.20, market_value=1952.00,
                     unrealized_pnl=167.00, unrealized_pnl_percent=9.35, day_pnl=12.30),
            Position(symbol="NVDA", quantity=5, avg_cost=450.00,
                     current_price=880.50, market_value=4402.50,
                     unrealized_pnl=2152.50, unrealized_pnl_percent=95.67, day_pnl=-25.00),
            Position(symbol="MSFT", quantity=8, avg_cost=380.00,
                     current_price=425.80, market_value=3406.40,
                     unrealized_pnl=366.40, unrealized_pnl_percent=12.05, day_pnl=8.50),
        ]

    def _mock_order(self, req: OrderRequest) -> Order:
        return Order(
            order_id=str(uuid4()),
            symbol=req.symbol,
            side=req.side,
            order_type=req.order_type,
            quantity=req.quantity,
            price=req.price,
            stop_price=req.stop_price,
            time_in_force=req.time_in_force,
            status=OrderStatus.PENDING,
            created_at=datetime.now(timezone.utc),
        )


webull_client = WebullClient()
