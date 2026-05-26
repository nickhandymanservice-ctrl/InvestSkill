"""
Conversational AI Assistant - synthesizes data-backed answers using
market data, sentiment, and technical analysis.
Handles queries like "Why is this ticker moving?" with sourced reasoning.
"""
import json
from datetime import datetime, timezone
from typing import List, Optional

from app.models.market import AIResponse
from app.services.market_data_service import (
    get_quote, get_technical_indicators, detect_support_resistance
)
from app.services.sentiment_service import get_combined_sentiment
from app.services.power_score_service import calculate_power_score
from app.services.signal_service import generate_signals, TimeFrame


def _extract_tickers(query: str) -> List[str]:
    import re
    # Match $TICKER or uppercase 1-5 letter sequences that look like tickers
    dollar_tickers = re.findall(r'\$([A-Z]{1,5})', query.upper())
    word_tickers = re.findall(r'\b([A-Z]{2,5})\b', query)
    common_words = {"WHY", "IS", "THE", "THIS", "HOW", "WHAT", "WILL", "CAN",
                    "DO", "ARE", "FOR", "AND", "BUT", "NOT", "HAS", "HAD",
                    "BUY", "SELL", "HOLD", "MOVE", "MOVING", "GOING", "UP", "DOWN"}
    word_tickers = [t for t in word_tickers if t not in common_words]
    return list(set(dollar_tickers + word_tickers))[:5]


def _build_context(symbol: str) -> dict:
    context = {"symbol": symbol}

    try:
        quote = get_quote(symbol)
        context["price"] = quote.price
        context["change"] = quote.change
        context["change_pct"] = quote.change_percent
        context["volume"] = quote.volume
    except Exception:
        pass

    try:
        indicators = get_technical_indicators(symbol)
        context["rsi"] = indicators.rsi_14
        context["macd"] = indicators.macd
        context["sma_50"] = indicators.sma_50
        context["sma_200"] = indicators.sma_200
    except Exception:
        pass

    try:
        sentiment = get_combined_sentiment(symbol)
        context["sentiment_score"] = sentiment.sentiment_score
        context["sentiment_mentions"] = sentiment.total_mentions
        context["trending"] = sentiment.trending
        context["top_msgs"] = sentiment.top_messages[:3]
    except Exception:
        pass

    try:
        signals = generate_signals(symbol, TimeFrame.D1)
        if signals:
            context["active_signals"] = [
                {"type": s.signal_type.value, "strategy": s.reasoning.split(".")[0], "confidence": s.confidence}
                for s in signals[:3]
            ]
    except Exception:
        pass

    return context


def _generate_answer(query: str, contexts: List[dict]) -> str:
    if not contexts:
        return "I couldn't find specific data for the tickers in your question. Please specify a valid stock symbol."

    parts = []
    for ctx in contexts:
        symbol = ctx.get("symbol", "Unknown")
        section = f"**{symbol}**:\n"

        price = ctx.get("price")
        change_pct = ctx.get("change_pct")
        if price:
            direction = "up" if change_pct and change_pct > 0 else "down"
            section += f"- Currently trading at ${price:.2f} ({direction} {abs(change_pct or 0):.2f}% today)\n"

        # Technical analysis summary
        rsi = ctx.get("rsi")
        if rsi:
            if rsi > 70:
                section += f"- RSI at {rsi:.1f} indicates overbought conditions\n"
            elif rsi < 30:
                section += f"- RSI at {rsi:.1f} indicates oversold conditions — potential bounce\n"
            else:
                section += f"- RSI at {rsi:.1f} is in neutral territory\n"

        sma_50 = ctx.get("sma_50")
        sma_200 = ctx.get("sma_200")
        if sma_50 and sma_200 and price:
            if price > sma_50 > sma_200:
                section += "- Trading above both 50 & 200 SMA — bullish trend\n"
            elif price < sma_50 < sma_200:
                section += "- Trading below both 50 & 200 SMA — bearish trend\n"

        # Sentiment
        sent_score = ctx.get("sentiment_score")
        if sent_score is not None:
            if sent_score > 0.3:
                section += f"- Social sentiment is bullish (score: {sent_score:.2f})\n"
            elif sent_score < -0.3:
                section += f"- Social sentiment is bearish (score: {sent_score:.2f})\n"
            else:
                section += f"- Social sentiment is neutral (score: {sent_score:.2f})\n"

        if ctx.get("trending"):
            section += "- Currently trending on social media\n"

        top_msgs = ctx.get("top_msgs", [])
        if top_msgs:
            section += f"- Latest chatter: \"{top_msgs[0][:100]}...\"\n"

        # Signals
        active_signals = ctx.get("active_signals", [])
        if active_signals:
            for sig in active_signals:
                section += f"- Signal: {sig['type'].upper()} via {sig['strategy']} (confidence: {sig['confidence']:.0%})\n"

        parts.append(section)

    answer = "\n".join(parts)

    # Add disclaimer
    answer += "\n\n*This analysis is generated from real-time data and backtested algorithms. Not financial advice.*"
    return answer


def ask_assistant(query: str) -> AIResponse:
    tickers = _extract_tickers(query)
    sources = []
    contexts = []

    for ticker in tickers:
        ctx = _build_context(ticker)
        contexts.append(ctx)
        sources.append(f"Yahoo Finance ({ticker})")
        sources.append(f"Social Sentiment ({ticker})")

    if not tickers:
        # Try common market queries
        query_lower = query.lower()
        if any(w in query_lower for w in ["market", "sp500", "s&p", "spy"]):
            tickers = ["SPY"]
            contexts = [_build_context("SPY")]
            sources = ["Yahoo Finance (SPY)", "S&P 500 Index Data"]
        elif any(w in query_lower for w in ["nasdaq", "qqq", "tech"]):
            tickers = ["QQQ"]
            contexts = [_build_context("QQQ")]
            sources = ["Yahoo Finance (QQQ)", "NASDAQ Index Data"]

    answer = _generate_answer(query, contexts)

    return AIResponse(
        answer=answer,
        sources=sources or ["Market Data"],
        confidence=0.8 if contexts else 0.3,
        related_tickers=tickers,
        timestamp=datetime.now(timezone.utc),
    )
