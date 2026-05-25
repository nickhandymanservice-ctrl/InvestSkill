package com.investpro.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.investpro.app.data.models.*
import com.investpro.app.data.repository.MarketRepository
import com.investpro.app.ui.theme.AccentBlue
import com.investpro.app.ui.theme.AccentGold
import com.investpro.app.ui.theme.GainGreen
import com.investpro.app.ui.theme.LossRed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TickerDetailState(
    val isLoading: Boolean = true,
    val quote: QuoteResponse? = null,
    val indicators: IndicatorsResponse? = null,
    val powerScore: PowerScoreResponse? = null,
    val signals: List<TradingSignalResponse> = emptyList(),
    val sentiment: SentimentResponse? = null,
    val supportResistance: List<SupportResistanceResponse> = emptyList(),
    val patterns: List<ChartPatternResponse> = emptyList()
)

@HiltViewModel
class TickerDetailViewModel @Inject constructor(
    private val repository: MarketRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val symbol: String = savedStateHandle.get<String>("symbol") ?: "AAPL"

    private val _state = MutableStateFlow(TickerDetailState())
    val state: StateFlow<TickerDetailState> = _state.asStateFlow()

    init {
        loadTickerData()
    }

    fun loadTickerData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val quote = async { repository.getQuote(symbol).getOrNull() }
            val indicators = async { repository.getIndicators(symbol).getOrNull() }
            val powerScore = async { repository.getPowerScore(symbol).getOrNull() }
            val signals = async { repository.getSignals(symbol).getOrDefault(emptyList()) }
            val sentiment = async { repository.getSentiment(symbol).getOrNull() }
            val sr = async { repository.getSupportResistance(symbol).getOrDefault(emptyList()) }
            val patterns = async { repository.getPatterns(symbol).getOrDefault(emptyList()) }

            _state.value = TickerDetailState(
                isLoading = false,
                quote = quote.await(),
                indicators = indicators.await(),
                powerScore = powerScore.await(),
                signals = signals.await(),
                sentiment = sentiment.await(),
                supportResistance = sr.await(),
                patterns = patterns.await()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TickerDetailScreen(
    symbol: String,
    navController: NavController,
    viewModel: TickerDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(symbol, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Price header
                state.quote?.let { quote ->
                    item { PriceHeader(quote) }
                }

                // Power Score
                state.powerScore?.let { ps ->
                    item { PowerScoreCard(ps) }
                }

                // Technical Indicators
                state.indicators?.let { ind ->
                    item { TechnicalIndicatorsCard(ind) }
                }

                // Sentiment
                state.sentiment?.let { sent ->
                    item { SentimentCard(sent) }
                }

                // Chart Patterns
                if (state.patterns.isNotEmpty()) {
                    item {
                        Text("Chart Patterns", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(state.patterns) { pattern ->
                        PatternCard(pattern)
                    }
                }

                // Support/Resistance
                if (state.supportResistance.isNotEmpty()) {
                    item {
                        Text("Key Levels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(state.supportResistance.take(6)) { level ->
                        LevelRow(level)
                    }
                }

                // Signals
                if (state.signals.isNotEmpty()) {
                    item {
                        Text("Active Signals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(state.signals) { signal ->
                        SignalCard(signal)
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun PriceHeader(quote: QuoteResponse) {
    val isPositive = quote.changePercent >= 0

    Column {
        Text(
            "$${String.format("%.2f", quote.price)}",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isPositive) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                contentDescription = null,
                tint = if (isPositive) GainGreen else LossRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "${if (isPositive) "+" else ""}${String.format("%.2f", quote.change)} (${String.format("%.2f", quote.changePercent)}%)",
                color = if (isPositive) GainGreen else LossRed,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun PowerScoreCard(ps: PowerScoreResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Power Score", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                val scoreColor = when {
                    ps.overallScore >= 7.5 -> GainGreen
                    ps.overallScore >= 5.0 -> AccentGold
                    else -> LossRed
                }
                Text(
                    String.format("%.1f", ps.overallScore) + "/10",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            }

            Spacer(Modifier.height(12.dp))

            // Sub-scores
            ScoreBar("Technical", ps.technicalScore)
            ScoreBar("Sentiment", ps.sentimentScore)
            ScoreBar("Fundamental", ps.fundamentalScore)
            ScoreBar("Momentum", ps.momentumScore)

            Spacer(Modifier.height(8.dp))

            // Key factors
            ps.keyFactors.forEach { factor ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Circle, contentDescription = null, modifier = Modifier.size(6.dp), tint = AccentBlue)
                    Spacer(Modifier.width(8.dp))
                    Text(factor, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                color = when (ps.signal) {
                    "strong_buy", "buy" -> GainGreen.copy(alpha = 0.15f)
                    "strong_sell", "sell" -> LossRed.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    ps.signal.replace("_", " ").uppercase(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold,
                    color = when (ps.signal) {
                        "strong_buy", "buy" -> GainGreen
                        "strong_sell", "sell" -> LossRed
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
fun ScoreBar(label: String, score: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { (score / 10f).toFloat() },
            modifier = Modifier.weight(1f).height(6.dp),
            color = when {
                score >= 7 -> GainGreen
                score >= 5 -> AccentGold
                else -> LossRed
            },
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        Spacer(Modifier.width(8.dp))
        Text(String.format("%.1f", score), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun TechnicalIndicatorsCard(ind: IndicatorsResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Technical Indicators", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IndicatorItem("RSI(14)", ind.rsi14?.let { String.format("%.1f", it) } ?: "--")
                IndicatorItem("MACD", ind.macd?.let { String.format("%.3f", it) } ?: "--")
                IndicatorItem("ATR(14)", ind.atr14?.let { String.format("%.2f", it) } ?: "--")
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IndicatorItem("SMA 20", ind.sma20?.let { "$${String.format("%.0f", it)}" } ?: "--")
                IndicatorItem("SMA 50", ind.sma50?.let { "$${String.format("%.0f", it)}" } ?: "--")
                IndicatorItem("SMA 200", ind.sma200?.let { "$${String.format("%.0f", it)}" } ?: "--")
            }
        }
    }
}

@Composable
fun IndicatorItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SentimentCard(sent: SentimentResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Social Sentiment", fontWeight = FontWeight.Bold)
                if (sent.trending) {
                    Surface(color = AccentGold.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                        Text(
                            "TRENDING",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentGold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Bullish", style = MaterialTheme.typography.labelSmall)
                    Text("${sent.bullishCount}", color = GainGreen, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Bearish", style = MaterialTheme.typography.labelSmall)
                    Text("${sent.bearishCount}", color = LossRed, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Total", style = MaterialTheme.typography.labelSmall)
                    Text("${sent.totalMentions}", fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Score", style = MaterialTheme.typography.labelSmall)
                    val sentColor = if (sent.sentimentScore > 0) GainGreen else if (sent.sentimentScore < 0) LossRed else MaterialTheme.colorScheme.onSurface
                    Text(String.format("%.2f", sent.sentimentScore), color = sentColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PatternCard(pattern: ChartPatternResponse) {
    val isBullish = pattern.direction == "bullish"
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isBullish) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                contentDescription = null,
                tint = if (isBullish) GainGreen else LossRed
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pattern.pattern, fontWeight = FontWeight.SemiBold)
                Text("Confidence: ${(pattern.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
            pattern.targetPrice?.let {
                Text("Target: $${String.format("%.2f", it)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun LevelRow(level: SupportResistanceResponse) {
    val isSupport = level.type == "support"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = if (isSupport) GainGreen.copy(alpha = 0.15f) else LossRed.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    if (isSupport) "S" else "R",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = if (isSupport) GainGreen else LossRed,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("$${String.format("%.2f", level.level)}", fontWeight = FontWeight.SemiBold)
        }
        Text("Strength: ${level.strength}/5", style = MaterialTheme.typography.bodySmall)
    }
}
