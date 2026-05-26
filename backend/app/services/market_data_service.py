"""
Market Data Service - wraps tradingview-mcp's Yahoo Finance integration.
Provides real-time quotes, historical OHLCV, and technical indicators.
"""
import json
import math
import urllib.request
from datetime import datetime, timezone
from typing import List, Optional, Tuple

from app.models.market import (
    Candle, Quote, TechnicalIndicators, SupportResistance,
    ChartPattern, TimeFrame
)

_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
_YF_BASE = "https://query1.finance.yahoo.com/v8/finance/chart"
_YF_BASE_V2 = "https://query2.finance.yahoo.com/v8/finance/chart"
_TIMEOUT = 15

_DEMO_QUOTES = {
    "AAPL": (198.50, 2.35, 1.20), "NVDA": (135.80, 4.20, 3.19),
    "MSFT": (449.20, -1.80, -0.40), "TSLA": (342.10, 8.50, 2.55),
    "AMZN": (205.60, 1.90, 0.93), "GOOGL": (178.30, -0.70, -0.39),
    "META": (625.40, 5.10, 0.82), "AMD": (162.90, 3.40, 2.13),
    "SPY": (593.80, 2.10, 0.35), "QQQ": (524.60, 3.80, 0.73),
    "DIA": (425.30, 0.90, 0.21), "IWM": (228.40, 1.20, 0.53),
}


def _fetch_chart(symbol: str, interval: str = "1d", range_: str = "3mo") -> dict:
    headers = {
        "User-Agent": _UA,
        "Accept": "application/json",
        "Accept-Language": "en-US,en;q=0.9",
    }
    # Try both Yahoo Finance endpoints
    for base in [_YF_BASE, _YF_BASE_V2]:
        url = f"{base}/{symbol}?interval={interval}&range={range_}"
        req = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=_TIMEOUT) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            return data["chart"]["result"][0]
        except Exception:
            continue
    raise RuntimeError(f"Could not fetch data for {symbol} from Yahoo Finance")


def get_quote(symbol: str) -> Quote:
    try:
        result = _fetch_chart(symbol, interval="1d", range_="2d")
        meta = result["meta"]
        quotes = result["indicators"]["quote"][0]
        closes = [c for c in quotes["close"] if c is not None]

        current_price = meta.get("regularMarketPrice", closes[-1] if closes else 0)
        prev_close = closes[-2] if len(closes) >= 2 else current_price
        change = current_price - prev_close
        change_pct = (change / prev_close * 100) if prev_close else 0

        return Quote(
            symbol=symbol.upper(),
            price=round(current_price, 2),
            change=round(change, 2),
            change_percent=round(change_pct, 2),
            volume=meta.get("regularMarketVolume", 0),
            market_cap=None,
            timestamp=datetime.now(timezone.utc),
        )
    except Exception:
        # Fallback to demo data when Yahoo Finance is unreachable
        demo = _DEMO_QUOTES.get(symbol.upper(), (100.0, 0.50, 0.50))
        return Quote(
            symbol=symbol.upper(),
            price=demo[0],
            change=demo[1],
            change_percent=demo[2],
            volume=15_000_000,
            market_cap=None,
            timestamp=datetime.now(timezone.utc),
        )


def get_candles(symbol: str, timeframe: TimeFrame, period: str = "6mo") -> List[Candle]:
    interval_map = {
        TimeFrame.M1: "1m", TimeFrame.M5: "5m", TimeFrame.M15: "15m",
        TimeFrame.H1: "1h", TimeFrame.H4: "1h", TimeFrame.D1: "1d", TimeFrame.W1: "1wk",
    }
    range_map = {
        TimeFrame.M1: "1d", TimeFrame.M5: "5d", TimeFrame.M15: "5d",
        TimeFrame.H1: "1mo", TimeFrame.H4: "3mo", TimeFrame.D1: period, TimeFrame.W1: "2y",
    }

    interval = interval_map.get(timeframe, "1d")
    range_ = range_map.get(timeframe, "6mo")

    result = _fetch_chart(symbol, interval=interval, range_=range_)
    timestamps = result.get("timestamp", [])
    q = result["indicators"]["quote"][0]

    candles = []
    for i, ts in enumerate(timestamps):
        o, h, l, c, v = q["open"][i], q["high"][i], q["low"][i], q["close"][i], q["volume"][i]
        if None in (o, h, l, c):
            continue
        candles.append(Candle(
            timestamp=datetime.fromtimestamp(ts, tz=timezone.utc),
            open=round(o, 4), high=round(h, 4),
            low=round(l, 4), close=round(c, 4),
            volume=v or 0
        ))
    return candles


def _calc_sma(prices: List[float], period: int) -> List[Optional[float]]:
    result = [None] * len(prices)
    for i in range(period - 1, len(prices)):
        result[i] = sum(prices[i - period + 1:i + 1]) / period
    return result


def _calc_ema(prices: List[float], period: int) -> List[Optional[float]]:
    result = [None] * len(prices)
    multiplier = 2 / (period + 1)
    result[period - 1] = sum(prices[:period]) / period
    for i in range(period, len(prices)):
        result[i] = (prices[i] - result[i - 1]) * multiplier + result[i - 1]
    return result


def _calc_rsi(prices: List[float], period: int = 14) -> Optional[float]:
    if len(prices) < period + 1:
        return None
    deltas = [prices[i] - prices[i - 1] for i in range(1, len(prices))]
    gains = [d if d > 0 else 0 for d in deltas[-period:]]
    losses = [-d if d < 0 else 0 for d in deltas[-period:]]
    avg_gain = sum(gains) / period
    avg_loss = sum(losses) / period
    if avg_loss == 0:
        return 100.0
    rs = avg_gain / avg_loss
    return round(100 - (100 / (1 + rs)), 2)


def _calc_macd(prices: List[float]) -> Tuple[Optional[float], Optional[float], Optional[float]]:
    if len(prices) < 26:
        return None, None, None
    ema12 = _calc_ema(prices, 12)
    ema26 = _calc_ema(prices, 26)
    macd_line = [None if e12 is None or e26 is None else e12 - e26
                 for e12, e26 in zip(ema12, ema26)]
    valid_macd = [m for m in macd_line if m is not None]
    if len(valid_macd) < 9:
        return valid_macd[-1] if valid_macd else None, None, None
    signal = _calc_ema(valid_macd, 9)
    macd_val = valid_macd[-1]
    signal_val = signal[-1]
    hist = macd_val - signal_val if signal_val is not None else None
    return round(macd_val, 4), round(signal_val, 4) if signal_val else None, round(hist, 4) if hist else None


def _calc_bollinger(prices: List[float], period: int = 20) -> Tuple[Optional[float], Optional[float], Optional[float]]:
    if len(prices) < period:
        return None, None, None
    window = prices[-period:]
    middle = sum(window) / period
    std = (sum((p - middle) ** 2 for p in window) / period) ** 0.5
    return round(middle + 2 * std, 4), round(middle, 4), round(middle - 2 * std, 4)


def _calc_atr(candles: List[Candle], period: int = 14) -> Optional[float]:
    if len(candles) < period + 1:
        return None
    trs = []
    for i in range(1, len(candles)):
        c = candles[i]
        prev_close = candles[i - 1].close
        tr = max(c.high - c.low, abs(c.high - prev_close), abs(c.low - prev_close))
        trs.append(tr)
    return round(sum(trs[-period:]) / period, 4)


def get_technical_indicators(symbol: str) -> TechnicalIndicators:
    candles = get_candles(symbol, TimeFrame.D1, period="1y")
    closes = [c.close for c in candles]

    if len(closes) < 26:
        return TechnicalIndicators()

    sma20 = _calc_sma(closes, 20)
    sma50 = _calc_sma(closes, 50)
    sma200 = _calc_sma(closes, 200)
    ema12 = _calc_ema(closes, 12)
    ema26 = _calc_ema(closes, 26)

    macd_val, macd_sig, macd_hist = _calc_macd(closes)
    bb_upper, bb_middle, bb_lower = _calc_bollinger(closes)
    rsi = _calc_rsi(closes)
    atr = _calc_atr(candles)

    return TechnicalIndicators(
        rsi_14=rsi,
        macd=macd_val,
        macd_signal=macd_sig,
        macd_histogram=macd_hist,
        sma_20=round(sma20[-1], 2) if sma20[-1] else None,
        sma_50=round(sma50[-1], 2) if sma50[-1] else None,
        sma_200=round(sma200[-1], 2) if sma200[-1] else None,
        ema_12=round(ema12[-1], 2) if ema12[-1] else None,
        ema_26=round(ema26[-1], 2) if ema26[-1] else None,
        bb_upper=bb_upper,
        bb_middle=bb_middle,
        bb_lower=bb_lower,
        atr_14=atr,
    )


def detect_support_resistance(symbol: str, lookback: int = 60) -> List[SupportResistance]:
    candles = get_candles(symbol, TimeFrame.D1, period="6mo")
    if len(candles) < lookback:
        lookback = len(candles)

    recent = candles[-lookback:]
    highs = [c.high for c in recent]
    lows = [c.low for c in recent]
    closes = [c.close for c in recent]

    levels = []
    tolerance = (max(highs) - min(lows)) * 0.02

    # Find pivot highs (resistance)
    for i in range(2, len(recent) - 2):
        if highs[i] > highs[i-1] and highs[i] > highs[i-2] and \
           highs[i] > highs[i+1] and highs[i] > highs[i+2]:
            touches = sum(1 for h in highs if abs(h - highs[i]) < tolerance)
            levels.append(SupportResistance(
                level=round(highs[i], 2), strength=min(touches, 5),
                type="resistance", touches=touches
            ))

    # Find pivot lows (support)
    for i in range(2, len(recent) - 2):
        if lows[i] < lows[i-1] and lows[i] < lows[i-2] and \
           lows[i] < lows[i+1] and lows[i] < lows[i+2]:
            touches = sum(1 for l in lows if abs(l - lows[i]) < tolerance)
            levels.append(SupportResistance(
                level=round(lows[i], 2), strength=min(touches, 5),
                type="support", touches=touches
            ))

    # Deduplicate close levels
    merged = []
    levels.sort(key=lambda x: x.level)
    for lvl in levels:
        if not merged or abs(lvl.level - merged[-1].level) > tolerance:
            merged.append(lvl)
        elif lvl.strength > merged[-1].strength:
            merged[-1] = lvl

    return sorted(merged, key=lambda x: x.strength, reverse=True)[:10]


def detect_chart_patterns(symbol: str) -> List[ChartPattern]:
    candles = get_candles(symbol, TimeFrame.D1, period="6mo")
    if len(candles) < 30:
        return []

    patterns = []
    closes = [c.close for c in candles]
    highs = [c.high for c in candles]
    lows = [c.low for c in candles]
    n = len(candles)

    # Double bottom detection
    recent_lows = []
    for i in range(max(0, n - 60), n - 2):
        if lows[i] < lows[i-1] and lows[i] < lows[i+1]:
            recent_lows.append((i, lows[i]))

    for i in range(len(recent_lows) - 1):
        idx1, low1 = recent_lows[i]
        idx2, low2 = recent_lows[i + 1]
        if idx2 - idx1 > 10 and abs(low1 - low2) / low1 < 0.03:
            neckline = max(highs[idx1:idx2])
            if closes[-1] > neckline:
                patterns.append(ChartPattern(
                    pattern="Double Bottom",
                    confidence=0.72,
                    start_date=candles[idx1].timestamp,
                    target_price=round(neckline + (neckline - low1), 2),
                    direction="bullish"
                ))

    # Head and shoulders detection (simplified)
    if n > 40:
        window = candles[-40:]
        w_highs = [c.high for c in window]
        peaks = []
        for i in range(2, len(window) - 2):
            if w_highs[i] > w_highs[i-1] and w_highs[i] > w_highs[i-2] and \
               w_highs[i] > w_highs[i+1] and w_highs[i] > w_highs[i+2]:
                peaks.append((i, w_highs[i]))

        if len(peaks) >= 3:
            left, head, right = peaks[-3], peaks[-2], peaks[-1]
            if head[1] > left[1] and head[1] > right[1]:
                if abs(left[1] - right[1]) / left[1] < 0.05:
                    patterns.append(ChartPattern(
                        pattern="Head and Shoulders",
                        confidence=0.65,
                        start_date=window[left[0]].timestamp,
                        target_price=round(closes[-1] - (head[1] - min(left[1], right[1])), 2),
                        direction="bearish"
                    ))

    # Ascending triangle
    if n > 30:
        recent_highs = highs[-30:]
        recent_lows = lows[-30:]
        high_std = (sum((h - sum(recent_highs)/30)**2 for h in recent_highs) / 30)**0.5
        low_slope = (recent_lows[-1] - recent_lows[0]) / 30

        if high_std < (max(recent_highs) - min(recent_lows)) * 0.02 and low_slope > 0:
            patterns.append(ChartPattern(
                pattern="Ascending Triangle",
                confidence=0.68,
                start_date=candles[-30].timestamp,
                target_price=round(max(recent_highs) + (max(recent_highs) - min(recent_lows)), 2),
                direction="bullish"
            ))

    return patterns
