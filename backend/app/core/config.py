from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    app_name: str = "InvestPro Trading API"
    version: str = "1.0.0"
    debug: bool = False

    # Yahoo Finance (no key needed, uses tradingview-mcp)
    yahoo_finance_proxy: Optional[str] = None

    # Stocktwits API
    stocktwits_base_url: str = "https://api.stocktwits.com/api/2"

    # Webull MCP
    webull_device_id: Optional[str] = None
    webull_access_token: Optional[str] = None
    webull_refresh_token: Optional[str] = None
    webull_account_id: Optional[str] = None

    # Octagon MCP
    octagon_api_key: Optional[str] = None

    # AI/LLM
    anthropic_api_key: Optional[str] = None
    openai_api_key: Optional[str] = None

    # Redis for caching
    redis_url: str = "redis://localhost:6379"

    # WebSocket
    ws_heartbeat_interval: int = 30

    class Config:
        env_file = ".env"


settings = Settings()
