"""
Power Score Service - composite probability scoring (1-10) for individual tickers.
Aggregates: Technical Momentum + Social Sentiment + Fundamental Health.
Inspired by Kavout's K Score methodology.
"""
from datetime import datetime, timezone
from typing import Optional

from app.models.market import PowerScore, SignalType
from app.services.market_data_service import get_technical_indicators, get_candles, TimeFrame
from app.services.sentiment_service import get_combined_sentiment


def _technical_score(symbol: str) -> float:
    try:
        indicators = get_technical_indicators(symbol)
    except Exception:
        return 5.0

    score = 5.0
    factors = []

    # RSI scoring
    if indicators.rsi_14 is not None:
        if indicators.rsi_14 < 30:
            score += 1.5  # Oversold = bullish
        elif indicators.rsi_14 < 40:
            score += 0.5
        elif indicators.rsi_14 > 70:
            score -= 1.5  # Overbought = bearish
        elif indicators.rsi_14 > 60:
            score -= 0.5

    # MACD scoring
    if indicators.macd is not None and indicators.macd_signal is not None:
        if indicators.macd > indicators.macd_signal:
            score += 1.0
        else:
            score -= 1.0

        if indicators.macd_histogram is not None:
            if indicators.macd_histogram > 0:
                score += 0.5
            else:
                score -= 0.5

    # Moving average scoring
    try:
        candles = get_candles(symbol, TimeFrame.D1, period="1y")
        if candles:
            current = candles[-1].close
            if indicators.sma_50 and current > indicators.sma_50:
                score += 0.5
            elif indicators.sma_50:
                score -= 0.5

            if indicators.sma_200 and current > indicators.sma_200:
                score += 0.5
            elif indicators.sma_200:
                score -= 0.5

            # Trend strength: SMA50 > SMA200 (golden cross)
            if indicators.sma_50 and indicators.sma_200:
                if indicators.sma_50 > indicators.sma_200:
                    score += 0.5
                else:
                    score -= 0.5
    except Exception:
        pass

    # Bollinger band position
    if indicators.bb_upper and indicators.bb_lower and indicators.bb_middle:
        try:
            current = candles[-1].close if candles else 0
            bb_range = indicators.bb_upper - indicators.bb_lower
            if bb_range > 0:
                position = (current - indicators.bb_lower) / bb_range
                if position < 0.2:
                    score += 1.0  # Near lower band = potential bounce
                elif position > 0.8:
                    score -= 0.5  # Near upper band = stretched
        except Exception:
            pass

    return max(1.0, min(10.0, score))


def _sentiment_score(symbol: str) -> float:
    try:
        sentiment = get_combined_sentiment(symbol)
    except Exception:
        return 5.0

    # Convert -1 to 1 range to 1-10
    base_score = 5.0 + (sentiment.sentiment_score * 4.0)

    # Boost for high volume of mentions
    if sentiment.total_mentions > 50:
        if sentiment.sentiment_score > 0:
            base_score += 0.5
        else:
            base_score -= 0.5

    # Trending bonus
    if sentiment.trending and sentiment.sentiment_score > 0.3:
        base_score += 0.5

    return max(1.0, min(10.0, base_score))


def _fundamental_score(symbol: str) -> float:
    """
    Fundamental scoring based on available Yahoo Finance data.
    In production, this would pull from SEC filings via Octagon MCP.
    """
    try:
        import json
        import urllib.request
        url = f"https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=1y"
        req = urllib.request.Request(url, headers={"User-Agent": "InvestPro/1.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))

        meta = data["chart"]["result"][0]["meta"]
        quotes = data["chart"]["result"][0]["indicators"]["quote"][0]
        closes = [c for c in quotes["close"] if c is not None]

        score = 5.0

        if len(closes) > 50:
            # 6-month momentum
            six_mo_return = (closes[-1] - closes[-min(126, len(closes))]) / closes[-min(126, len(closes))]
            if six_mo_return > 0.2:
                score += 1.5
            elif six_mo_return > 0.1:
                score += 1.0
            elif six_mo_return > 0:
                score += 0.5
            elif six_mo_return < -0.2:
                score -= 1.5
            elif six_mo_return < -0.1:
                score -= 1.0
            else:
                score -= 0.5

            # Consistency (lower volatility is better for fundamental strength)
            returns = [(closes[i] - closes[i-1])/closes[i-1] for i in range(1, len(closes))]
            if returns:
                avg_return = sum(returns) / len(returns)
                volatility = (sum((r - avg_return)**2 for r in returns) / len(returns)) ** 0.5
                annualized_vol = volatility * (252 ** 0.5)
                if annualized_vol < 0.2:
                    score += 1.0
                elif annualized_vol < 0.3:
                    score += 0.5
                elif annualized_vol > 0.5:
                    score -= 1.0

            # 52-week high proximity
            high_52w = max(closes[-min(252, len(closes)):])
            proximity = closes[-1] / high_52w
            if proximity > 0.95:
                score += 0.5  # Near highs = strength
            elif proximity < 0.7:
                score -= 0.5  # Far from highs = weakness

        return max(1.0, min(10.0, score))

    except Exception:
        return 5.0


def _momentum_score(symbol: str) -> float:
    try:
        candles = get_candles(symbol, TimeFrame.D1, period="3mo")
        if len(candles) < 20:
            return 5.0

        closes = [c.close for c in candles]
        volumes = [c.volume for c in candles]

        score = 5.0

        # Price momentum (5, 10, 20 day returns)
        if len(closes) >= 5:
            ret_5d = (closes[-1] - closes[-5]) / closes[-5]
            if ret_5d > 0.05:
                score += 1.0
            elif ret_5d > 0.02:
                score += 0.5
            elif ret_5d < -0.05:
                score -= 1.0
            elif ret_5d < -0.02:
                score -= 0.5

        if len(closes) >= 20:
            ret_20d = (closes[-1] - closes[-20]) / closes[-20]
            if ret_20d > 0.1:
                score += 1.0
            elif ret_20d > 0.05:
                score += 0.5
            elif ret_20d < -0.1:
                score -= 1.0

        # Volume trend
        if len(volumes) >= 20:
            recent_vol = sum(volumes[-5:]) / 5
            avg_vol = sum(volumes[-20:]) / 20
            if avg_vol > 0:
                vol_ratio = recent_vol / avg_vol
                if vol_ratio > 1.5 and closes[-1] > closes[-5]:
                    score += 1.0  # Bullish volume expansion
                elif vol_ratio > 1.5 and closes[-1] < closes[-5]:
                    score -= 0.5  # Bearish volume expansion

        # Higher highs / higher lows pattern
        if len(candles) >= 10:
            recent_highs = [c.high for c in candles[-10:]]
            recent_lows = [c.low for c in candles[-10:]]
            if recent_highs[-1] > max(recent_highs[:-1]):
                score += 0.5
            if recent_lows[-1] > min(recent_lows[:5]):
                score += 0.5

        return max(1.0, min(10.0, score))
    except Exception:
        return 5.0


def calculate_power_score(symbol: str) -> PowerScore:
    tech = _technical_score(symbol)
    sent = _sentiment_score(symbol)
    fund = _fundamental_score(symbol)
    mom = _momentum_score(symbol)

    # Weighted composite
    overall = (tech * 0.30 + sent * 0.15 + fund * 0.25 + mom * 0.30)

    # Determine signal
    if overall >= 8.0:
        signal = SignalType.STRONG_BUY
    elif overall >= 6.5:
        signal = SignalType.BUY
    elif overall <= 3.0:
        signal = SignalType.STRONG_SELL
    elif overall <= 4.5:
        signal = SignalType.SELL
    else:
        signal = SignalType.HOLD

    # Key factors
    factors = []
    if tech >= 7:
        factors.append("Strong technical setup")
    elif tech <= 3:
        factors.append("Weak technicals")
    if sent >= 7:
        factors.append("Bullish social sentiment")
    elif sent <= 3:
        factors.append("Bearish social sentiment")
    if fund >= 7:
        factors.append("Solid fundamentals")
    elif fund <= 3:
        factors.append("Fundamental concerns")
    if mom >= 7:
        factors.append("Strong price momentum")
    elif mom <= 3:
        factors.append("Fading momentum")

    if not factors:
        factors.append("Mixed signals - neutral outlook")

    return PowerScore(
        symbol=symbol.upper(),
        overall_score=round(overall, 1),
        technical_score=round(tech, 1),
        sentiment_score=round(sent, 1),
        fundamental_score=round(fund, 1),
        momentum_score=round(mom, 1),
        signal=signal,
        key_factors=factors,
        timestamp=datetime.now(timezone.utc),
    )
