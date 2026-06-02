package com.shade.app.data.remote.interceptor

import com.shade.app.data.remote.api.AuthService
import com.shade.app.data.remote.dto.RefreshRequest
import com.shade.app.security.KeyVaultManager
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Authenticator — 401 yanıtı alındığında refresh token ile
 * yeni bir access token çeker ve isteği yeni token ile tekrar dener.
 *
 * [authService] Dagger Lazy ile enjekte edilir; bu sayede
 * TokenRefreshAuthenticator → AuthService → Retrofit → OkHttpClient → TokenRefreshAuthenticator
 * döngüsel bağımlılığı kırılır.
 */
@Singleton
class TokenRefreshAuthenticator @Inject constructor(
    private val authService: Lazy<AuthService>,
    private val keyVaultManager: KeyVaultManager
) : Authenticator {

    /** Eş zamanlı refresh girişimlerini önler. */
    private val isRefreshing = AtomicBoolean(false)

    override fun authenticate(route: Route?, response: Response): Request? {
        // Zaten refresh denemesi yaşandıysa sonsuz döngüye girme
        if (response.request.header("X-Retry-After-Refresh") != null) return null

        // Aynı anda yalnızca bir refresh denemesi
        if (!isRefreshing.compareAndSet(false, true)) return null

        return try {
            val newAccessToken = runBlocking { refreshTokens() } ?: return null

            response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .header("X-Retry-After-Refresh", "1")
                .build()
        } finally {
            isRefreshing.set(false)
        }
    }

    /**
     * Refresh token ile yeni access token alır, vault'a kaydeder.
     * Başarısız olursa null döner (kullanıcı yeniden giriş yapmalı).
     */
    private suspend fun refreshTokens(): String? {
        val refreshToken = keyVaultManager.getRefreshToken() ?: return null

        return try {
            val refreshResponse = authService.get().refresh(RefreshRequest(refreshToken))
            if (refreshResponse.isSuccessful) {
                val body = refreshResponse.body() ?: return null
                keyVaultManager.saveAccessToken(body.accessToken)
                keyVaultManager.saveRefreshToken(body.refreshToken)
                body.accessToken
            } else {
                // Refresh token süresi dolmuş veya geçersiz — vault'u temizle
                keyVaultManager.clearVault()
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
