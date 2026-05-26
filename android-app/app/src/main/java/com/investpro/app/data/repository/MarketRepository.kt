package com.investpro.app.data.repository

import com.investpro.app.data.api.InvestProApi
import com.investpro.app.data.models.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketRepository @Inject constructor(
    private val api: InvestProApi
) {
    suspend fun getQuote(symbol: String): Result<QuoteResponse> = runCatching {
        api.getQuote(symbol)
    }

    suspend fun getCandles(symbol: String, timeframe: String = "1d", period: String = "6mo"): Result<List<CandleResponse>> = runCatching {
        api.getCandles(symbol, timeframe, period)
    }

    suspend fun getIndicators(symbol: String): Result<IndicatorsResponse> = runCatching {
        api.getIndicators(symbol)
    }

    suspend fun getSupportResistance(symbol: String): Result<List<SupportResistanceResponse>> = runCatching {
        api.getSupportResistance(symbol)
    }

    suspend fun getPatterns(symbol: String): Result<List<ChartPatternResponse>> = runCatching {
        api.getPatterns(symbol)
    }

    suspend fun getSignals(symbol: String, timeframe: String = "1d"): Result<List<TradingSignalResponse>> = runCatching {
        api.getSignals(symbol, timeframe)
    }

    suspend fun scanMarket(symbols: String): Result<List<TradingSignalResponse>> = runCatching {
        api.scanMarket(symbols)
    }

    suspend fun getPowerScore(symbol: String): Result<PowerScoreResponse> = runCatching {
        api.getPowerScore(symbol)
    }

    suspend fun getSentiment(symbol: String): Result<SentimentResponse> = runCatching {
        api.getSentiment(symbol)
    }

    suspend fun askAssistant(query: String): Result<AIAssistantResponse> = runCatching {
        api.askAssistant(query)
    }

    suspend fun getPortfolio(): Result<PortfolioResponse> = runCatching {
        api.getPortfolio()
    }

    suspend fun getPositions(): Result<List<PositionResponse>> = runCatching {
        api.getPositions()
    }

    suspend fun placeOrder(order: OrderRequest): Result<OrderResponse> = runCatching {
        api.placeOrder(order)
    }

    suspend fun cancelOrder(orderId: String): Result<Unit> = runCatching {
        api.cancelOrder(orderId)
    }
}
