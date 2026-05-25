package com.investpro.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.investpro.app.data.models.AIAssistantResponse
import com.investpro.app.data.repository.MarketRepository
import com.investpro.app.ui.theme.AccentBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val sources: List<String> = emptyList(),
    val relatedTickers: List<String> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                content = "Hi! I'm your AI trading assistant. Ask me anything about stocks, markets, or your portfolio.\n\nTry: \"Why is NVDA moving?\" or \"What's the outlook for AAPL?\"",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun sendMessage(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            // Add user message
            _messages.value = _messages.value + ChatMessage(content = query, isUser = true)
            _isProcessing.value = true

            // Add loading indicator
            _messages.value = _messages.value + ChatMessage(content = "", isUser = false, isLoading = true)

            val result = repository.askAssistant(query)

            // Remove loading indicator and add response
            _messages.value = _messages.value.dropLast(1)

            result.onSuccess { response ->
                _messages.value = _messages.value + ChatMessage(
                    content = response.answer,
                    isUser = false,
                    sources = response.sources,
                    relatedTickers = response.relatedTickers
                )
            }.onFailure {
                _messages.value = _messages.value + ChatMessage(
                    content = "Sorry, I couldn't process that request. Please try again.",
                    isUser = false
                )
            }

            _isProcessing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(viewModel: AssistantViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, tint = AccentBlue)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("AI Assistant", fontWeight = FontWeight.Bold)
                        Text(
                            "Powered by market data & sentiment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )

        // Chat messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(messages) { message ->
                ChatBubble(message)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // Quick actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { viewModel.sendMessage("What are today's top signals?") },
                label = { Text("Top Signals") }
            )
            SuggestionChip(
                onClick = { viewModel.sendMessage("How is the market today?") },
                label = { Text("Market Now") }
            )
        }

        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask about any stock...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                enabled = !isProcessing
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                enabled = inputText.isNotBlank() && !isProcessing
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    if (message.isLoading) {
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Analyzing...", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (message.isUser)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(12.dp),
                color = if (message.isUser)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Sources
        if (message.sources.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Sources: ${message.sources.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
