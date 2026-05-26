"""InvestPro Trading API - FastAPI application entry point."""
from __future__ import annotations

import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.auth_routes import auth_router
from app.api.routes import router
from app.core.config import settings

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


app = FastAPI(
    title=settings.app_name,
    version=settings.version,
    description=(
        "AI-powered stock trading backend with predictive signals, power "
        "scoring, and conversational analysis. Per-user Webull credentials "
        "are pushed once via /api/v1/setup and stored encrypted server-side."
    ),
)

# Locked-down CORS. Configure via CORS_ALLOWED_ORIGINS env, comma-separated.
_origins = [o.strip() for o in settings.cors_allowed_origins.split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_origins or ["https://investpro.nhsindy.com"],
    allow_credentials=True,
    allow_methods=["GET", "POST", "DELETE"],
    allow_headers=["Authorization", "Content-Type", "X-User-Id"],
)

app.include_router(auth_router, prefix="/api/v1")
app.include_router(router, prefix="/api/v1")


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "healthy", "version": settings.version}


@app.get("/")
async def root() -> dict[str, object]:
    return {
        "app": settings.app_name,
        "version": settings.version,
        "endpoints": {
            "setup": "POST /api/v1/setup",
            "setup_status": "GET /api/v1/setup/status (X-User-Id)",
            "delete_setup": "DELETE /api/v1/setup (X-User-Id)",
            "quote": "/api/v1/quote/{symbol}",
            "candles": "/api/v1/candles/{symbol}",
            "indicators": "/api/v1/indicators/{symbol}",
            "signals": "/api/v1/signals/{symbol}",
            "power_score": "/api/v1/power-score/{symbol}",
            "sentiment": "/api/v1/sentiment/{symbol}",
            "patterns": "/api/v1/patterns/{symbol}",
            "support_resistance": "/api/v1/support-resistance/{symbol}",
            "ask_ai": "/api/v1/ask?query=...",
            "portfolio": "/api/v1/portfolio (X-User-Id)",
            "positions": "/api/v1/positions (X-User-Id)",
            "place_order": "POST /api/v1/orders (X-User-Id)",
            "scan": "/api/v1/scan?symbols=AAPL,MSFT",
            "ws_quotes": "ws://host/api/v1/ws/quotes",
        },
    }
