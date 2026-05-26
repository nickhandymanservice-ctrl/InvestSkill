package com.investpro.app.data.api

import com.investpro.app.data.models.*
import kotlinx.serialization.Serializable
import retrofit2.http.*

interface InvestProApi {

    @POST("setup")
    suspend fun setup(@Body request: SetupRequest): SetupResponse

    @GET("setup/status")
    suspend fun setupStatus(): SetupStatusResponse

    @GET("quote/{symbol}")
    suspend fun getQuote(@Path("symbol") symbol: String): QuoteResponse

    @GET("candles/{symbol}")
    suspend fun getCandles(
        @Path("symbol") symbol: String,
        @Query("timeframe") timeframe: String = "1d",
        @Query("period") period: String = "6mo"
    ): List<CandleResponse>

    @GET("indicators/{symbol}")
    suspend fun getIndicators(@Path("symbol") symbol: String): IndicatorsResponse

    @GET("support-resistance/{symbol}")
    suspend fun getSupportResistance(@Path("symbol") symbol: String): List<SupportResistanceResponse>

    @GET("patterns/{symbol}")
    suspend fun getPatterns(@Path("symbol") symbol: String): List<ChartPatternResponse>

    @GET("signals/{symbol}")
    suspend fun getSignals(
        @Path("symbol") symbol: String,
        @Query("timeframe") timeframe: String = "1d"
    ): List<TradingSignalResponse>

    @GET("scan")
    suspend fun scanMarket(
        @Query("symbols") symbols: String = "AAPL,MSFT,NVDA,TSLA,AMZN,GOOGL,META,AMD,SPY,QQQ",
        @Query("timeframe") timeframe: String = "1d"
    ): List<TradingSignalResponse>

    @GET("power-score/{symbol}")
    suspend fun getPowerScore(@Path("symbol") symbol: String): PowerScoreResponse

    @GET("sentiment/{symbol}")
    suspend fun getSentiment(@Path("symbol") symbol: String): SentimentResponse

    @GET("ask")
    suspend fun askAssistant(@Query("query") query: String): AIAssistantResponse

    @GET("portfolio")
    suspend fun getPortfolio(): PortfolioResponse

    @GET("positions")
    suspend fun getPositions(): List<PositionResponse>

    @POST("orders")
    suspend fun placeOrder(@Body order: OrderRequest): OrderResponse

    @DELETE("orders/{orderId}")
    suspend fun cancelOrder(@Path("orderId") orderId: String)
}

@Serializable
data class SetupRequest(
    val user_id: String,
    val webull_device_id: String? = null,
    val webull_access_token: String? = null,
    val webull_refresh_token: String? = null,
    val webull_account_id: String? = null,
    val anthropic_api_key: String? = null,
    val openai_api_key: String? = null,
)

@Serializable
data class SetupResponse(
    val user_id: String,
    val has_webull: Boolean,
    val has_ai: Boolean,
    val message: String,
)

@Serializable
data class SetupStatusResponse(
    val user_id: String,
    val configured: Boolean,
)

