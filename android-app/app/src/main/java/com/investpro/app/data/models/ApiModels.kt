package com.investpro.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QuoteResponse(
    val symbol: String,
    val price: Double,
    val change: Double,
    @SerialName("change_percent") val changePercent: Double,
    val volume: Long,
    @SerialName("market_cap") val marketCap: Double? = null,
    val timestamp: String
)

@Serializable
data class CandleResponse(
    val timestamp: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

@Serializable
data class IndicatorsResponse(
    @SerialName("rsi_14") val rsi14: Double? = null,
    val macd: Double? = null,
    @SerialName("macd_signal") val macdSignal: Double? = null,
    @SerialName("macd_histogram") val macdHistogram: Double? = null,
    @SerialName("sma_20") val sma20: Double? = null,
    @SerialName("sma_50") val sma50: Double? = null,
    @SerialName("sma_200") val sma200: Double? = null,
    @SerialName("ema_12") val ema12: Double? = null,
    @SerialName("ema_26") val ema26: Double? = null,
    @SerialName("bb_upper") val bbUpper: Double? = null,
    @SerialName("bb_middle") val bbMiddle: Double? = null,
    @SerialName("bb_lower") val bbLower: Double? = null,
    @SerialName("atr_14") val atr14: Double? = null
)

@Serializable
data class SupportResistanceResponse(
    val level: Double,
    val strength: Int,
    val type: String,
    val touches: Int
)

@Serializable
data class ChartPatternResponse(
    val pattern: String,
    val confidence: Double,
    @SerialName("start_date") val startDate: String,
    @SerialName("target_price") val targetPrice: Double? = null,
    val direction: String
)

@Serializable
data class TradingSignalResponse(
    val symbol: String,
    @SerialName("signal_type") val signalType: String,
    val confidence: Double,
    @SerialName("entry_price") val entryPrice: Double,
    @SerialName("stop_loss") val stopLoss: Double? = null,
    @SerialName("take_profit") val takeProfit: Double? = null,
    val timeframe: String,
    val reasoning: String,
    @SerialName("backtested_win_rate") val backtestedWinRate: Double? = null,
    val timestamp: String
)

@Serializable
data class PowerScoreResponse(
    val symbol: String,
    @SerialName("overall_score") val overallScore: Double,
    @SerialName("technical_score") val technicalScore: Double,
    @SerialName("sentiment_score") val sentimentScore: Double,
    @SerialName("fundamental_score") val fundamentalScore: Double,
    @SerialName("momentum_score") val momentumScore: Double,
    val signal: String,
    @SerialName("key_factors") val keyFactors: List<String>,
    val timestamp: String
)

@Serializable
data class SentimentResponse(
    val symbol: String,
    @SerialName("bullish_count") val bullishCount: Int,
    @SerialName("bearish_count") val bearishCount: Int,
    @SerialName("total_mentions") val totalMentions: Int,
    @SerialName("sentiment_score") val sentimentScore: Double,
    val trending: Boolean,
    @SerialName("top_messages") val topMessages: List<String>,
    val source: String,
    val timestamp: String
)

@Serializable
data class AIAssistantResponse(
    val answer: String,
    val sources: List<String>,
    val confidence: Double,
    @SerialName("related_tickers") val relatedTickers: List<String>,
    val timestamp: String
)

@Serializable
data class PortfolioResponse(
    @SerialName("total_value") val totalValue: Double,
    @SerialName("cash_balance") val cashBalance: Double,
    @SerialName("day_pnl") val dayPnl: Double,
    @SerialName("day_pnl_percent") val dayPnlPercent: Double,
    @SerialName("total_pnl") val totalPnl: Double,
    @SerialName("total_pnl_percent") val totalPnlPercent: Double,
    val positions: List<PositionResponse>,
    @SerialName("buying_power") val buyingPower: Double
)

@Serializable
data class PositionResponse(
    val symbol: String,
    val quantity: Double,
    @SerialName("avg_cost") val avgCost: Double,
    @SerialName("current_price") val currentPrice: Double,
    @SerialName("market_value") val marketValue: Double,
    @SerialName("unrealized_pnl") val unrealizedPnl: Double,
    @SerialName("unrealized_pnl_percent") val unrealizedPnlPercent: Double,
    @SerialName("day_pnl") val dayPnl: Double
)

@Serializable
data class OrderRequest(
    val symbol: String,
    val side: String,
    @SerialName("order_type") val orderType: String,
    val quantity: Double,
    val price: Double? = null,
    @SerialName("stop_price") val stopPrice: Double? = null,
    @SerialName("time_in_force") val timeInForce: String = "day"
)

@Serializable
data class OrderResponse(
    @SerialName("order_id") val orderId: String,
    val symbol: String,
    val side: String,
    @SerialName("order_type") val orderType: String,
    val quantity: Double,
    val status: String,
    @SerialName("created_at") val createdAt: String
)
