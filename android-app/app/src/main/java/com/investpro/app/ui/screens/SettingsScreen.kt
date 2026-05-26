package com.investpro.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.investpro.app.data.api.InvestProApi
import com.investpro.app.data.api.SetupRequest
import com.investpro.app.data.auth.CredentialManager
import com.investpro.app.data.auth.CredentialState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialManager: CredentialManager,
    private val api: InvestProApi,
) : ViewModel() {

    val credentialState: StateFlow<CredentialState> = credentialManager.state

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun save(form: SettingsForm) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)

            val current = credentialManager.state.value
            val updated = current.copy(
                webullDeviceId = form.webullDeviceId.trim(),
                webullAccessToken = form.webullAccessToken.trim(),
                webullRefreshToken = form.webullRefreshToken.trim(),
                webullAccountId = form.webullAccountId.trim(),
                anthropicApiKey = form.anthropicApiKey.trim(),
                openaiApiKey = form.openaiApiKey.trim(),
            )

            // Save locally first so the X-User-Id header is stable.
            credentialManager.update(updated)

            // Then push to the backend.
            val result = runCatching {
                api.setup(
                    SetupRequest(
                        user_id = updated.userId,
                        webull_device_id = updated.webullDeviceId.takeIf { it.isNotBlank() },
                        webull_access_token = updated.webullAccessToken.takeIf { it.isNotBlank() },
                        webull_refresh_token = updated.webullRefreshToken.takeIf { it.isNotBlank() },
                        webull_account_id = updated.webullAccountId.takeIf { it.isNotBlank() },
                        anthropic_api_key = updated.anthropicApiKey.takeIf { it.isNotBlank() },
                        openai_api_key = updated.openaiApiKey.takeIf { it.isNotBlank() },
                    )
                )
            }

            _uiState.value = result.fold(
                onSuccess = { resp ->
                    SettingsUiState(
                        isSaving = false,
                        message = resp.message,
                    )
                },
                onFailure = { err ->
                    SettingsUiState(
                        isSaving = false,
                        error = "Couldn't reach the server: ${err.message ?: err::class.simpleName}",
                    )
                }
            )
        }
    }

    fun signOut() {
        credentialManager.clear()
        _uiState.value = SettingsUiState(message = "Signed out. Your local credentials were wiped.")
    }
}

data class SettingsUiState(
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

data class SettingsForm(
    val webullDeviceId: String = "",
    val webullAccessToken: String = "",
    val webullRefreshToken: String = "",
    val webullAccountId: String = "",
    val anthropicApiKey: String = "",
    val openaiApiKey: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDone: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val stored by viewModel.credentialState.collectAsState()
    val ui by viewModel.uiState.collectAsState()

    var form by remember(stored) {
        mutableStateOf(
            SettingsForm(
                webullDeviceId = stored.webullDeviceId,
                webullAccessToken = stored.webullAccessToken,
                webullRefreshToken = stored.webullRefreshToken,
                webullAccountId = stored.webullAccountId,
                anthropicApiKey = stored.anthropicApiKey,
                openaiApiKey = stored.openaiApiKey,
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Plain-English intro card.
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Connect Webull",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Paste the four values from your Webull account below. " +
                                "They're saved encrypted on your phone and on the server. " +
                                "Skip any field you don't have — you can come back later.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SectionHeader("Webull")
            SecretField("Account ID", form.webullAccountId) {
                form = form.copy(webullAccountId = it)
            }
            SecretField("Access Token", form.webullAccessToken) {
                form = form.copy(webullAccessToken = it)
            }
            SecretField("Refresh Token (optional)", form.webullRefreshToken) {
                form = form.copy(webullRefreshToken = it)
            }
            SecretField("Device ID (optional)", form.webullDeviceId) {
                form = form.copy(webullDeviceId = it)
            }

            SectionHeader("AI (optional)")
            SecretField("Anthropic API Key", form.anthropicApiKey) {
                form = form.copy(anthropicApiKey = it)
            }
            SecretField("OpenAI API Key", form.openaiApiKey) {
                form = form.copy(openaiApiKey = it)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.save(form) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !ui.isSaving,
            ) {
                if (ui.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save & Connect")
                }
            }

            OutlinedButton(
                onClick = { viewModel.signOut() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out & Reset")
            }

            ui.message?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(msg)
                    }
                }
            }
            ui.error?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Error, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(err)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                )
            }
        },
    )
}
