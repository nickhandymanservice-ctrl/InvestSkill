package com.investpro.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.investpro.app.data.models.*
import com.investpro.app.data.repository.MarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val marketIndices: List<QuoteResponse> = emptyList(),
    val watchlist: List<Pair<QuoteResponse, PowerScoreResponse?>> = emptyList(),
    val topSignals: List<TradingSignalResponse> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val watchlistSymbols = listOf("AAPL", "NVDA", "MSFT", "TSLA", "AMZN", "GOOGL", "META", "AMD")
    private val indexSymbols = listOf("SPY", "QQQ", "DIA", "IWM")

    init {
        loadDashboard()
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Load market indices
            val indices = indexSymbols.map { symbol ->
                async { repository.getQuote(symbol).getOrNull() }
            }.awaitAll().filterNotNull()

            // Load watchlist quotes and power scores
            val watchlist = watchlistSymbols.map { symbol ->
                async {
                    val quote = repository.getQuote(symbol).getOrNull()
                    val score = repository.getPowerScore(symbol).getOrNull()
                    if (quote != null) Pair(quote, score) else null
                }
            }.awaitAll().filterNotNull()

            // Load top signals
            val signals = repository.scanMarket(watchlistSymbols.joinToString(","))
                .getOrDefault(emptyList())

            _uiState.value = DashboardUiState(
                isLoading = false,
                marketIndices = indices,
                watchlist = watchlist,
                topSignals = signals
            )
        }
    }
}
