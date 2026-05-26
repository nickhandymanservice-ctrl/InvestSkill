package com.investpro.app.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local secure storage for the user's identity + Webull credentials.
 *
 * - [userId] is a stable per-install UUID generated on first launch.
 *   It is sent on every API request as the `X-User-Id` header so the
 *   backend can look up that user's encrypted credentials.
 *
 * - Webull credentials are mirrored locally in EncryptedSharedPreferences
 *   so the Settings screen can show what's already configured. They are
 *   ALSO pushed to the backend via POST /api/v1/setup — the backend is
 *   the source of truth for live trading.
 */
@Singleton
class CredentialManager @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _state = MutableStateFlow(loadFromDisk())
    val state: StateFlow<CredentialState> = _state.asStateFlow()

    val userId: String
        get() = _state.value.userId

    val isConfigured: Boolean
        get() = _state.value.let { it.webullAccessToken.isNotBlank() && it.webullAccountId.isNotBlank() }

    fun update(creds: CredentialState) {
        prefs.edit().apply {
            putString(KEY_USER_ID, creds.userId)
            putString(KEY_WEBULL_DEVICE_ID, creds.webullDeviceId)
            putString(KEY_WEBULL_ACCESS_TOKEN, creds.webullAccessToken)
            putString(KEY_WEBULL_REFRESH_TOKEN, creds.webullRefreshToken)
            putString(KEY_WEBULL_ACCOUNT_ID, creds.webullAccountId)
            putString(KEY_ANTHROPIC, creds.anthropicApiKey)
            putString(KEY_OPENAI, creds.openaiApiKey)
            apply()
        }
        _state.value = creds
    }

    fun clear() {
        val fresh = CredentialState(userId = newUserId())
        prefs.edit().clear().putString(KEY_USER_ID, fresh.userId).apply()
        _state.value = fresh
    }

    private fun loadFromDisk(): CredentialState {
        val storedId = prefs.getString(KEY_USER_ID, null)
        val resolvedId = if (storedId.isNullOrBlank()) {
            val fresh = newUserId()
            prefs.edit().putString(KEY_USER_ID, fresh).apply()
            fresh
        } else storedId

        return CredentialState(
            userId = resolvedId,
            webullDeviceId = prefs.getString(KEY_WEBULL_DEVICE_ID, "").orEmpty(),
            webullAccessToken = prefs.getString(KEY_WEBULL_ACCESS_TOKEN, "").orEmpty(),
            webullRefreshToken = prefs.getString(KEY_WEBULL_REFRESH_TOKEN, "").orEmpty(),
            webullAccountId = prefs.getString(KEY_WEBULL_ACCOUNT_ID, "").orEmpty(),
            anthropicApiKey = prefs.getString(KEY_ANTHROPIC, "").orEmpty(),
            openaiApiKey = prefs.getString(KEY_OPENAI, "").orEmpty(),
        )
    }

    private fun newUserId(): String = "u_" + UUID.randomUUID().toString().replace("-", "")

    private companion object {
        const val PREFS_NAME = "investpro_credentials"
        const val KEY_USER_ID = "user_id"
        const val KEY_WEBULL_DEVICE_ID = "webull_device_id"
        const val KEY_WEBULL_ACCESS_TOKEN = "webull_access_token"
        const val KEY_WEBULL_REFRESH_TOKEN = "webull_refresh_token"
        const val KEY_WEBULL_ACCOUNT_ID = "webull_account_id"
        const val KEY_ANTHROPIC = "anthropic_api_key"
        const val KEY_OPENAI = "openai_api_key"
    }
}

/** Snapshot of what's stored locally. All string fields are non-null; empty string == "not set". */
data class CredentialState(
    val userId: String,
    val webullDeviceId: String = "",
    val webullAccessToken: String = "",
    val webullRefreshToken: String = "",
    val webullAccountId: String = "",
    val anthropicApiKey: String = "",
    val openaiApiKey: String = "",
)
