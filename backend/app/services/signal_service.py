"""
Predictive Signal Service - generates high-probability entry/exit signals
by combining multiple technical strategies with backtested success rates.
Inspired by Trade Ideas scanner architecture.
"""
from datetime import datetime, timezone
from typing import List, Optional

from app.models.market import TradingSignal, SignalType, TimeFrame, BacktestResult
from app.services.market_data_service import get_candles, get_technical_indicators, _calc_rsi, _calc_ema, _calc_sma


def _backtest_strategy(candles, strategy_fn, lookback: int = 100) -> float:
    if len(candles) < lookback + 20:
        return 0.5

    wins = 0
    total = 0
    for i in range(lookback, len(candles) - 5):
        window = candles[max(0, i - 50):i + 1]
        signal = strategy_fn(window)
        if signal is None:
            continue

        future_return = (candles[min(i + 5, len(candles) - 1)].close - candles[i].close) / candles[i].close
        if signal == "buy" and future_return > 0.005:
            wins += 1
        elif signal == "sell" and future_return < -0.005:
            wins += 1
        total += 1

    return wins / total if total > 0 else 0.5


def _rsi_strategy(candles) -> Optional[str]:
    closes = [c.close for c in candles]
    rsi = _calc_rsi(closes)
    if rsi is None:
        return None
    if rsi < 30:
        return "buy"
    elif rsi > 70:
        return "sell"
    return None


def _ema_cross_strategy(candles) -> Optional[str]:
    closes = [c.close for c in candles]
    if len(closes) < 50:
        return None
    ema20 = _calc_ema(closes, 20)
    ema50 = _calc_ema(closes, 50)
    if ema20[-1] is None or ema50[-1] is None or ema20[-2] is None or ema50[-2] is None:
        return None
    if ema20[-2] < ema50[-2] and ema20[-1] > ema50[-1]:
        return "buy"
    elif ema20[-2] > ema50[-2] and ema20[-1] < ema50[-1]:
        return "sell"
    return None


def _macd_strategy(candles) -> Optional[str]:
    closes = [c.close for c in candles]
    if len(closes) < 35:
        return None
    ema12 = _calc_ema(closes, 12)
    ema26 = _calc_ema(closes, 26)
    macd_line = [None if e12 is None or e26 is None else e12 - e26
                 for e12, e26 in zip(ema12, ema26)]
    valid = [m for m in macd_line if m is not None]
    if len(valid) < 10:
        return None
    signal_line = _calc_ema(valid, 9)
    if signal_line[-1] is None or signal_line[-2] is None:
        return None
    if valid[-2] < signal_line[-2] and valid[-1] > signal_line[-1]:
        return "buy"
    elif valid[-2] > signal_line[-2] and valid[-1] < signal_line[-1]:
        return "sell"
    return None


def _bollinger_strategy(candles) -> Optional[str]:
    closes = [c.close for c in candles]
    if len(closes) < 20:
        return None
    window = closes[-20:]
    middle = sum(window) / 20
    std = (sum((p - middle) ** 2 for p in window) / 20) ** 0.5
    upper = middle + 2 * std
    lower = middle - 2 * std
    current = closes[-1]

    if current < lower:
        return "buy"
    elif current > upper:
        return "sell"
    return None


def _volume_breakout_strategy(candles) -> Optional[str]:
    if len(candles) < 21:
        return None
    avg_vol = sum(c.volume for c in candles[-21:-1]) / 20
    current_vol = candles[-1].volume
    price_change = (candles[-1].close - candles[-2].close) / candles[-2].close

    if current_vol > avg_vol * 2 and price_change > 0.02:
        return "buy"
    elif current_vol > avg_vol * 2 and price_change < -0.02:
        return "sell"
    return None


STRATEGIES = {
    "RSI Reversal": _rsi_strategy,
    "EMA Crossover": _ema_cross_strategy,
    "MACD Divergence": _macd_strategy,
    "Bollinger Squeeze": _bollinger_strategy,
    "Volume Breakout": _volume_breakout_strategy,
}


def generate_signals(symbol: str, timeframe: TimeFrame = TimeFrame.D1) -> List[TradingSignal]:
    candles = get_candles(symbol, timeframe)
    if len(candles) < 50:
        return []

    signals = []
    closes = [c.close for c in candles]
    current_price = closes[-1]

    for name, strategy_fn in STRATEGIES.items():
        direction = strategy_fn(candles)
        if direction is None:
            continue

        win_rate = _backtest_strategy(candles, strategy_fn)

        # Calculate stop loss and take profit
        atr_period = min(14, len(candles) - 1)
        trs = []
        for i in range(1, atr_period + 1):
            c = candles[-i]
            prev = candles[-i - 1]
            tr = max(c.high - c.low, abs(c.high - prev.close), abs(c.low - prev.close))
            trs.append(tr)
        atr = sum(trs) / len(trs) if trs else current_price * 0.02

        if direction == "buy":
            stop_loss = round(current_price - 2 * atr, 2)
            take_profit = round(current_price + 3 * atr, 2)
            signal_type = SignalType.STRONG_BUY if win_rate > 0.65 else SignalType.BUY
        else:
            stop_loss = round(current_price + 2 * atr, 2)
            take_profit = round(current_price - 3 * atr, 2)
            signal_type = SignalType.STRONG_SELL if win_rate > 0.65 else SignalType.SELL

        signals.append(TradingSignal(
            symbol=symbol.upper(),
            signal_type=signal_type,
            confidence=round(win_rate, 3),
            entry_price=round(current_price, 2),
            stop_loss=stop_loss,
            take_profit=take_profit,
            timeframe=timeframe,
            reasoning=f"{name} signal triggered. Backtested win rate: {win_rate:.1%} over recent history.",
            backtested_win_rate=round(win_rate, 3),
            timestamp=datetime.now(timezone.utc),
        ))

    return sorted(signals, key=lambda s: s.confidence, reverse=True)


def scan_market(symbols: List[str], timeframe: TimeFrame = TimeFrame.D1) -> List[TradingSignal]:
    all_signals = []
    for sym in symbols:
        try:
            sigs = generate_signals(sym, timeframe)
            all_signals.extend(sigs)
        except Exception:
            continue
    return sorted(all_signals, key=lambda s: s.confidence, reverse=True)[:20]
