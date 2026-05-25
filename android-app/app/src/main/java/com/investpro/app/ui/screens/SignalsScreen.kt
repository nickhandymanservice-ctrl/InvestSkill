package com.investpro.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.investpro.app.data.models.TradingSignalResponse
import com.investpro.app.data.repository.MarketRepository
import com.investpro.app.ui.theme.GainGreen
import com.investpro.app.ui.theme.LossRed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignalsViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _signals = MutableStateFlow<List<TradingSignalResponse>>(emptyList())
    val signals: StateFlow<List<TradingSignalResponse>> = _signals.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadSignals()
    }

    fun loadSignals() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.scanMarket("AAPL,MSFT,NVDA,TSLA,AMZN,GOOGL,META,AMD,SPY,QQQ,NFLX,CRM,AVGO,LLY,JPM")
            _signals.value = result.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalsScreen(viewModel: SignalsViewModel = hiltViewModel()) {
    val signals by viewModel.signals.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        TopAppBar(
            title = {
                Column {
                    Text("Trading Signals", fontWeight = FontWeight.Bold)
                    Text(
                        "AI-powered entry/exit alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = { viewModel.loadSignals() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(signals) { signal ->
                    SignalCard(signal)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun SignalCard(signal: TradingSignalResponse) {
    val isBuy = signal.signalType.contains("buy", ignoreCase = true)
    val signalColor = if (isBuy) GainGreen else LossRed
    val signalIcon = if (isBuy) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = signalColor.copy(alpha = 0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(signalIcon, contentDescription = null, tint = signalColor, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(signal.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            signal.signalType.replace("_", " ").uppercase(),
                            color = signalColor,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Confidence badge
                Surface(
                    color = signalColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "${(signal.confidence * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = signalColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Price levels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PriceLevel("Entry", signal.entryPrice, MaterialTheme.colorScheme.onSurface)
                PriceLevel("Stop Loss", signal.stopLoss, LossRed)
                PriceLevel("Target", signal.takeProfit, GainGreen)
            }

            Spacer(Modifier.height(8.dp))

            // Reasoning
            Text(
                signal.reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Win rate
            signal.backtestedWinRate?.let { wr ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { wr.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = signalColor,
                    trackColor = signalColor.copy(alpha = 0.1f)
                )
                Text(
                    "Backtested Win Rate: ${(wr * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PriceLevel(label: String, price: Double?, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (price != null) "$${String.format("%.2f", price)}" else "--",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
