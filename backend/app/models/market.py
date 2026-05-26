from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime
from enum import Enum


class TimeFrame(str, Enum):
    M1 = "1m"
    M5 = "5m"
    M15 = "15m"
    H1 = "1h"
    H4 = "4h"
    D1 = "1d"
    W1 = "1w"


class SignalType(str, Enum):
    BUY = "buy"
    SELL = "sell"
    HOLD = "hold"
    STRONG_BUY = "strong_buy"
    STRONG_SELL = "strong_sell"


class Candle(BaseModel):
    timestamp: datetime
    open: float
    high: float
    low: float
    close: float
    volume: int


class Quote(BaseModel):
    symbol: str
    price: float
    change: float
    change_percent: float
    volume: int
    market_cap: Optional[float] = None
    pe_ratio: Optional[float] = None
    high_52w: Optional[float] = None
    low_52w: Optional[float] = None
    timestamp: datetime


class TechnicalIndicators(BaseModel):
    rsi_14: Optional[float] = None
    macd: Optional[float] = None
    macd_signal: Optional[float] = None
    macd_histogram: Optional[float] = None
    sma_20: Optional[float] = None
    sma_50: Optional[float] = None
    sma_200: Optional[float] = None
    ema_12: Optional[float] = None
    ema_26: Optional[float] = None
    bb_upper: Optional[float] = None
    bb_middle: Optional[float] = None
    bb_lower: Optional[float] = None
    atr_14: Optional[float] = None
    adx: Optional[float] = None
    stoch_k: Optional[float] = None
    stoch_d: Optional[float] = None
    vwap: Optional[float] = None


class SupportResistance(BaseModel):
    level: float
    strength: int  # 1-5
    type: str  # "support" or "resistance"
    touches: int


class ChartPattern(BaseModel):
    pattern: str
    confidence: float
    start_date: datetime
    target_price: Optional[float] = None
    direction: str  # "bullish" or "bearish"


class TradingSignal(BaseModel):
    symbol: str
    signal_type: SignalType
    confidence: float
    entry_price: float
    stop_loss: Optional[float] = None
    take_profit: Optional[float] = None
    timeframe: TimeFrame
    reasoning: str
    backtested_win_rate: Optional[float] = None
    timestamp: datetime


class SentimentData(BaseModel):
    symbol: str
    bullish_count: int
    bearish_count: int
    total_mentions: int
    sentiment_score: float  # -1.0 to 1.0
    trending: bool
    top_messages: List[str]
    source: str  # "stocktwits", "reddit", etc.
    timestamp: datetime


class PowerScore(BaseModel):
    symbol: str
    overall_score: float  # 1-10
    technical_score: float  # 1-10
    sentiment_score: float  # 1-10
    fundamental_score: float  # 1-10
    momentum_score: float  # 1-10
    signal: SignalType
    key_factors: List[str]
    timestamp: datetime


class BacktestResult(BaseModel):
    strategy: str
    symbol: str
    period: str
    total_trades: int
    win_rate: float
    total_return: float
    sharpe_ratio: Optional[float] = None
    max_drawdown: float
    profit_factor: Optional[float] = None


class AIResponse(BaseModel):
    answer: str
    sources: List[str]
    confidence: float
    related_tickers: List[str]
    timestamp: datetime
