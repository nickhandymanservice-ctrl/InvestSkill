"""
Sentiment Analysis Service - integrates Stocktwits API and Reddit sentiment.
Provides real-time retail sentiment scoring for individual tickers.
"""
import json
import urllib.request
import urllib.parse
from datetime import datetime, timezone
from typing import List, Optional

from app.models.market import SentimentData

_UA = "InvestPro/1.0"
_TIMEOUT = 10

BULLISH_KEYWORDS = [
    "buy", "bull", "moon", "pump", "long", "call", "up", "gain",
    "strong", "breakout", "bullish", "rally", "surge", "upside",
    "accumulate", "undervalued", "support", "bottom", "recovery",
    "rocket", "diamond", "hands", "hold", "yolo",
]

BEARISH_KEYWORDS = [
    "sell", "bear", "dump", "short", "put", "down", "loss", "weak",
    "crash", "drop", "bearish", "tank", "decline", "downside",
    "overvalued", "resistance", "top", "overbought", "bubble",
    "bag", "rug", "exit", "fear",
]


def _score_text(text: str) -> float:
    text_lower = text.lower()
    bull = sum(1 for kw in BULLISH_KEYWORDS if kw in text_lower)
    bear = sum(1 for kw in BEARISH_KEYWORDS if kw in text_lower)
    total = bull + bear
    if total == 0:
        return 0.0
    return (bull - bear) / total


def get_stocktwits_sentiment(symbol: str) -> Optional[SentimentData]:
    url = f"https://api.stocktwits.com/api/2/streams/symbol/{symbol}.json"
    req = urllib.request.Request(url, headers={"User-Agent": _UA})

    try:
        with urllib.request.urlopen(req, timeout=_TIMEOUT) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except Exception:
        return None

    messages = data.get("messages", [])
    if not messages:
        return None

    bullish = 0
    bearish = 0
    top_msgs = []

    for msg in messages[:30]:
        sentiment = msg.get("entities", {}).get("sentiment", {})
        if sentiment:
            if sentiment.get("basic") == "Bullish":
                bullish += 1
            elif sentiment.get("basic") == "Bearish":
                bearish += 1
        else:
            score = _score_text(msg.get("body", ""))
            if score > 0.2:
                bullish += 1
            elif score < -0.2:
                bearish += 1

        if len(top_msgs) < 5:
            top_msgs.append(msg.get("body", "")[:200])

    total = bullish + bearish
    score = (bullish - bearish) / total if total > 0 else 0.0

    return SentimentData(
        symbol=symbol.upper(),
        bullish_count=bullish,
        bearish_count=bearish,
        total_mentions=len(messages),
        sentiment_score=round(score, 3),
        trending=len(messages) > 20,
        top_messages=top_msgs,
        source="stocktwits",
        timestamp=datetime.now(timezone.utc),
    )


def get_reddit_sentiment(symbol: str) -> Optional[SentimentData]:
    subreddits = ["wallstreetbets", "stocks", "investing", "StockMarket"]
    all_posts = []

    for sub in subreddits:
        url = (
            f"https://www.reddit.com/r/{sub}/search.json"
            f"?q={urllib.parse.quote(symbol)}&sort=new&t=week&limit=10"
        )
        req = urllib.request.Request(url, headers={"User-Agent": _UA})
        try:
            with urllib.request.urlopen(req, timeout=_TIMEOUT) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                posts = data.get("data", {}).get("children", [])
                all_posts.extend(posts)
        except Exception:
            continue

    if not all_posts:
        return None

    bullish = 0
    bearish = 0
    top_msgs = []

    for post in all_posts[:30]:
        post_data = post.get("data", {})
        title = post_data.get("title", "")
        selftext = post_data.get("selftext", "")[:500]
        combined = f"{title} {selftext}"

        score = _score_text(combined)
        if score > 0.2:
            bullish += 1
        elif score < -0.2:
            bearish += 1

        if len(top_msgs) < 5:
            top_msgs.append(title[:200])

    total = bullish + bearish
    score = (bullish - bearish) / total if total > 0 else 0.0

    return SentimentData(
        symbol=symbol.upper(),
        bullish_count=bullish,
        bearish_count=bearish,
        total_mentions=len(all_posts),
        sentiment_score=round(score, 3),
        trending=len(all_posts) > 15,
        top_messages=top_msgs,
        source="reddit",
        timestamp=datetime.now(timezone.utc),
    )


def get_combined_sentiment(symbol: str) -> SentimentData:
    stocktwits = get_stocktwits_sentiment(symbol)
    reddit = get_reddit_sentiment(symbol)

    sources = [s for s in [stocktwits, reddit] if s is not None]

    if not sources:
        return SentimentData(
            symbol=symbol.upper(),
            bullish_count=0, bearish_count=0, total_mentions=0,
            sentiment_score=0.0, trending=False, top_messages=[],
            source="combined", timestamp=datetime.now(timezone.utc),
        )

    total_bull = sum(s.bullish_count for s in sources)
    total_bear = sum(s.bearish_count for s in sources)
    total_mentions = sum(s.total_mentions for s in sources)
    avg_score = sum(s.sentiment_score for s in sources) / len(sources)
    trending = any(s.trending for s in sources)
    top_msgs = []
    for s in sources:
        top_msgs.extend(s.top_messages[:3])

    return SentimentData(
        symbol=symbol.upper(),
        bullish_count=total_bull,
        bearish_count=total_bear,
        total_mentions=total_mentions,
        sentiment_score=round(avg_score, 3),
        trending=trending,
        top_messages=top_msgs[:5],
        source="combined",
        timestamp=datetime.now(timezone.utc),
    )
