package com.investpro.app.data.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that stamps every outbound request with the user's
 * stable per-install identifier. Backend uses this to look up the encrypted
 * Webull credentials it stored for this user.
 */
class AuthInterceptor(private val credentialManager: CredentialManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-User-Id", credentialManager.userId)
            .build()
        return chain.proceed(request)
    }
}
