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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.investpro.app.data.models.PortfolioResponse
import com.investpro.app.data.models.PositionResponse
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
class PortfolioViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _portfolio = MutableStateFlow<PortfolioResponse?>(null)
    val portfolio: StateFlow<PortfolioResponse?> = _portfolio.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadPortfolio()
    }

    fun loadPortfolio() {
        viewModelScope.launch {
            _isLoading.value = true
            _portfolio.value = repository.getPortfolio().getOrNull()
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(viewModel: PortfolioViewModel = hiltViewModel()) {
    val portfolio by viewModel.portfolio.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Portfolio", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { viewModel.loadPortfolio() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            portfolio?.let { p ->
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Portfolio Summary Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("Total Value", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "$${String.format("%,.2f", p.totalValue)}",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    PnlColumn("Today", p.dayPnl, p.dayPnlPercent)
                                    PnlColumn("Total P&L", p.totalPnl, p.totalPnlPercent)
                                    Column {
                                        Text("Buying Power", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            "$${String.format("%,.0f", p.buyingPower)}",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Cash Balance
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cash", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "$${String.format("%,.2f", p.cashBalance)}",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    item {
                        Text(
                            "Positions (${p.positions.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(p.positions) { position ->
                        PositionCard(position)
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun PnlColumn(label: String, pnl: Double, pnlPercent: Double) {
    val isPositive = pnl >= 0
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            "${if (isPositive) "+" else ""}$${String.format("%,.2f", pnl)}",
            color = if (isPositive) GainGreen else LossRed,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "${if (isPositive) "+" else ""}${String.format("%.2f", pnlPercent)}%",
            color = if (isPositive) GainGreen else LossRed,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun PositionCard(position: PositionResponse) {
    val isPositive = position.unrealizedPnl >= 0

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(position.symbol, fontWeight = FontWeight.Bold)
                Text(
                    "${position.quantity.toInt()} shares @ $${String.format("%.2f", position.avgCost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$${String.format("%,.2f", position.marketValue)}",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${if (isPositive) "+" else ""}$${String.format("%.2f", position.unrealizedPnl)} (${String.format("%.1f", position.unrealizedPnlPercent)}%)",
                    color = if (isPositive) GainGreen else LossRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
