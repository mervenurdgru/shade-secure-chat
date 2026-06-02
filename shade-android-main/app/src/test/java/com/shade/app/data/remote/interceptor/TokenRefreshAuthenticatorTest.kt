package com.shade.app.data.remote.interceptor

import com.google.common.truth.Truth.assertThat
import com.shade.app.data.remote.api.AuthService
import com.shade.app.data.remote.dto.RefreshRequest
import com.shade.app.data.remote.dto.RefreshResponse
import com.shade.app.security.KeyVaultManager
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response as RetrofitResponse

class TokenRefreshAuthenticatorTest {

    private lateinit var authService: AuthService
    private lateinit var keyVaultManager: KeyVaultManager
    private lateinit var lazyAuthService: Lazy<AuthService>
    private lateinit var authenticator: TokenRefreshAuthenticator

    @Before
    fun setUp() {
        authService   = mock()
        keyVaultManager = mock()
        lazyAuthService = Lazy { authService }
        authenticator = TokenRefreshAuthenticator(lazyAuthService, keyVaultManager)
    }

    @Test
    fun `returns null when X-Retry-After-Refresh header is present (loop guard)`() {
        val response = build401Response(hasRetryHeader = true)
        val result = authenticator.authenticate(null, response)
        assertThat(result).isNull()
    }

    @Test
    fun `returns null when no refresh token stored`() = runTest {
        whenever(keyVaultManager.getRefreshToken()).thenReturn(null)

        val response = build401Response()
        val result = authenticator.authenticate(null, response)

        assertThat(result).isNull()
        verify(authService, never()).refresh(any())
    }

    @Test
    fun `returns retried request with new token on successful refresh`() = runTest {
        val oldRefreshToken = "old_refresh"
        val newAccessToken  = "new_access"
        val newRefreshToken = "new_refresh"

        whenever(keyVaultManager.getRefreshToken()).thenReturn(oldRefreshToken)
        whenever(authService.refresh(RefreshRequest(oldRefreshToken))).thenReturn(
            RetrofitResponse.success(RefreshResponse(newAccessToken, newRefreshToken))
        )

        val response = build401Response()
        val result = authenticator.authenticate(null, response)

        assertThat(result).isNotNull()
        assertThat(result!!.header("Authorization")).isEqualTo("Bearer $newAccessToken")
        assertThat(result.header("X-Retry-After-Refresh")).isEqualTo("1")

        verify(keyVaultManager).saveAccessToken(newAccessToken)
        verify(keyVaultManager).saveRefreshToken(newRefreshToken)
    }

    @Test
    fun `clears vault when refresh returns non-2xx`() = runTest {
        whenever(keyVaultManager.getRefreshToken()).thenReturn("expired_token")
        whenever(authService.refresh(any())).thenReturn(
            RetrofitResponse.error(401, "".toResponseBody())
        )

        val response = build401Response()
        val result = authenticator.authenticate(null, response)

        assertThat(result).isNull()
        verify(keyVaultManager).clearVault()
    }

    @Test
    fun `returns null when refresh throws network exception`() = runTest {
        whenever(keyVaultManager.getRefreshToken()).thenReturn("valid_token")
        whenever(authService.refresh(any())).thenThrow(RuntimeException("no network"))

        val response = build401Response()
        val result = authenticator.authenticate(null, response)

        assertThat(result).isNull()
        verify(keyVaultManager, never()).clearVault()
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private fun build401Response(hasRetryHeader: Boolean = false): Response {
        val request = Request.Builder()
            .url("https://example.com/api/v1/some-endpoint")
            .apply { if (hasRetryHeader) header("X-Retry-After-Refresh", "1") }
            .build()

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody())
            .build()
    }
}
