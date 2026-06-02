package com.shade.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.shade.app.data.remote.api.AuthService
import com.shade.app.data.remote.dto.LoginVerifyRequest
import com.shade.app.data.remote.dto.LoginVerifyResponse
import com.shade.app.data.remote.dto.RegisterRequest
import com.shade.app.data.remote.dto.RegisterResponse
import com.shade.app.domain.model.AuthResult
import com.shade.app.security.KeyVaultManager
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response as RetrofitResponse

class AuthRepositoryImplTest {

    private lateinit var authService: AuthService
    private lateinit var keyVaultManager: KeyVaultManager
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        authService = mock()
        keyVaultManager = mock()
        repository = AuthRepositoryImpl(authService, keyVaultManager)
    }

    // ── loginVerify ───────────────────────────────────────────────────────────

    @Test
    fun `loginVerify success maps all response fields into AuthResult`() = runTest {
        val response = LoginVerifyResponse(
            shadeId    = "shade_abc",
            userId     = "user_xyz",
            accessToken  = "at_123",
            refreshToken = "rt_456",
            deviceId   = "dev_789",
            message    = "ok"
        )
        whenever(authService.loginVerify(any())).thenReturn(RetrofitResponse.success(response))

        val result = repository.loginVerify(
            shadeId = "shade_abc", challenge = "ch", signature = "sig",
            deviceModel = "Pixel", fcmToken = "fcm"
        )

        assertThat(result.isSuccess).isTrue()
        val authResult = result.getOrThrow()
        assertThat(authResult.shadeId).isEqualTo("shade_abc")
        assertThat(authResult.userId).isEqualTo("user_xyz")
        assertThat(authResult.accessToken).isEqualTo("at_123")
        assertThat(authResult.refreshToken).isEqualTo("rt_456")
        assertThat(authResult.deviceId).isEqualTo("dev_789")
    }

    @Test
    fun `loginVerify non-2xx response returns failure`() = runTest {
        whenever(authService.loginVerify(any()))
            .thenReturn(RetrofitResponse.error(401, "".toResponseBody()))

        val result = repository.loginVerify(
            shadeId = "shade", challenge = "ch", signature = "sig",
            deviceModel = "Pixel", fcmToken = "fcm"
        )

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `loginVerify network exception wraps in failure`() = runTest {
        whenever(authService.loginVerify(any())).thenThrow(RuntimeException("no network"))

        val result = repository.loginVerify(
            shadeId = "shade", challenge = "ch", signature = "sig",
            deviceModel = "Pixel", fcmToken = "fcm"
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("no network")
    }

    @Test
    fun `loginVerify sends correct request body to service`() = runTest {
        val response = LoginVerifyResponse("s", "u", "at", "rt", "d", "ok")
        whenever(authService.loginVerify(any())).thenReturn(RetrofitResponse.success(response))

        repository.loginVerify(
            shadeId = "shade_abc", challenge = "the_challenge",
            signature = "the_sig", deviceModel = "Galaxy", fcmToken = "token_fcm"
        )

        verify(authService).loginVerify(
            LoginVerifyRequest(
                shadeId = "shade_abc", challenge = "the_challenge",
                signature = "the_sig", deviceModel = "Galaxy", fcmToken = "token_fcm"
            )
        )
    }

    // ── saveSession ───────────────────────────────────────────────────────────

    @Test
    fun `saveSession persists all non-null fields to vault`() = runTest {
        val authResult = AuthResult(
            shadeId      = "shade1",
            userId       = "user1",
            accessToken  = "access1",
            refreshToken = "refresh1",
            idPrivateKey = "idKey1",
            encPrivateKey = "encKey1",
            deviceId     = "device1"
        )

        repository.saveSession(authResult)

        verify(keyVaultManager).saveShadeId("shade1")
        verify(keyVaultManager).saveUserId("user1")
        verify(keyVaultManager).saveAccessToken("access1")
        verify(keyVaultManager).saveRefreshToken("refresh1")
        verify(keyVaultManager).saveEd25519PrivateKey("idKey1")
        verify(keyVaultManager).saveX25519PrivateKey("encKey1")
        verify(keyVaultManager).saveDeviceId("device1")
    }

    @Test
    fun `saveSession skips null optional fields`() = runTest {
        val authResult = AuthResult(
            shadeId      = "shade1",
            userId       = null,
            accessToken  = null,
            refreshToken = null
        )

        repository.saveSession(authResult)

        verify(keyVaultManager).saveShadeId("shade1")
        verify(keyVaultManager, never()).saveUserId(any())
        verify(keyVaultManager, never()).saveAccessToken(any())
        verify(keyVaultManager, never()).saveRefreshToken(any())
    }

    @Test
    fun `saveSession always saves shadeId regardless of other nulls`() = runTest {
        val authResult = AuthResult(shadeId = "only_shade", userId = null)

        repository.saveSession(authResult)

        verify(keyVaultManager).saveShadeId("only_shade")
        verifyNoMoreInteractions(keyVaultManager)
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    fun `register success returns AuthResult with shadeId and deviceId`() = runTest {
        val response = RegisterResponse(
            shadeId = "shade_new", userId = "u_new",
            deviceID = "dev_new", message = "registered"
        )
        whenever(authService.register(any())).thenReturn(RetrofitResponse.success(response))

        val result = repository.register(
            identityPublicKey             = "ipk",
            encryptedIdentityPrivateKey   = "eipk",
            encryptionPublicKey           = "epk",
            encryptedEncryptionPrivateKey = "eepk",
            salt = "salt", deviceModel = "Pixel", fcmToken = "fcm"
        )

        assertThat(result.isSuccess).isTrue()
        val authResult = result.getOrThrow()
        assertThat(authResult.shadeId).isEqualTo("shade_new")
        assertThat(authResult.deviceId).isEqualTo("dev_new")
        // accessToken is not returned from registration
        assertThat(authResult.accessToken).isNull()
    }

    @Test
    fun `register failure returns Result failure`() = runTest {
        whenever(authService.register(any()))
            .thenReturn(RetrofitResponse.error(500, "".toResponseBody()))

        val result = repository.register("", "", "", "", "", "", "")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `register sends correct payload to service`() = runTest {
        val response = RegisterResponse("s", "u", "d", "ok")
        whenever(authService.register(any())).thenReturn(RetrofitResponse.success(response))

        repository.register(
            identityPublicKey             = "idPub",
            encryptedIdentityPrivateKey   = "idPrivEnc",
            encryptionPublicKey           = "encPub",
            encryptedEncryptionPrivateKey = "encPrivEnc",
            salt = "mySalt", deviceModel = "OnePlus", fcmToken = "myFcm"
        )

        verify(authService).register(
            RegisterRequest(
                identityPublicKey             = "idPub",
                encryptedIdentityPrivateKey   = "idPrivEnc",
                encryptionPublicKey           = "encPub",
                encryptedEncryptionPrivateKey = "encPrivEnc",
                salt = "mySalt", deviceModel = "OnePlus", fcmToken = "myFcm"
            )
        )
    }
}
