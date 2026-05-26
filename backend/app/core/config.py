"""Process configuration. Per-user credentials live in ``credentials.py``."""
from __future__ import annotations

from typing import Optional

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = "InvestPro Trading API"
    version: str = "1.0.0"
    debug: bool = False

    # Yahoo Finance (no key needed by default)
    yahoo_finance_proxy: Optional[str] = None

    # Stocktwits API
    stocktwits_base_url: str = "https://api.stocktwits.com/api/2"

    # Optional global AI keys (used only when a user has not supplied their own)
    anthropic_api_key: Optional[str] = None
    openai_api_key: Optional[str] = None
    octagon_api_key: Optional[str] = None

    # Redis for caching
    redis_url: str = "redis://localhost:6379"

    # WebSocket
    ws_heartbeat_interval: int = 30

    # CORS — comma-separated list of allowed origins
    cors_allowed_origins: str = "https://investpro.nhsindy.com"

    # Per-user credential store
    credentials_master_key: Optional[str] = None
    credentials_db_path: str = "/data/credentials.sqlite"


settings = Settings()
