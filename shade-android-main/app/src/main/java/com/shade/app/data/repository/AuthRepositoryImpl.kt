package com.shade.app.data.repository

import android.util.Log
import com.shade.app.data.remote.api.AuthService
import com.shade.app.data.remote.dto.LoginInitRequest
import com.shade.app.data.remote.dto.LoginInitResponse
import com.shade.app.data.remote.dto.LoginVerifyRequest
import com.shade.app.data.remote.dto.LoginVerifyResponse
import com.shade.app.data.remote.dto.RegisterRequest
import com.shade.app.data.remote.dto.RegisterResponse
import com.shade.app.domain.model.AuthResult
import com.shade.app.domain.repository.AuthRepository
import com.shade.app.security.KeyVaultManager
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authService: AuthService,
    private val keyVaultManager: KeyVaultManager
) : AuthRepository {

    override suspend fun register(
        identityPublicKey: String,
        encryptedIdentityPrivateKey: String,
        encryptionPublicKey: String,
        encryptedEncryptionPrivateKey: String,
        salt: String,
        deviceModel: String,
        fcmToken: String
    ): Result<AuthResult> {
        return try {
            val response = authService.register(
                RegisterRequest(
                    identityPublicKey,
                    encryptedIdentityPrivateKey,
                    encryptionPublicKey,
                    encryptedEncryptionPrivateKey,
                    salt,
                    deviceModel,
                    fcmToken
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(AuthResult(body.shadeId, body.userId, null, deviceId = body.deviceID))
            } else {
                Result.failure(Exception("Registration failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loginInit(shadeId: String): Result<LoginInitResponse> {
        return try {
            val response = authService.loginInit(LoginInitRequest(shadeId))
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "loginInit success — challenge received")
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "(no body)"
                Log.e(TAG, "loginInit failed — HTTP ${response.code()} ${response.message()} | body=$errorBody")
                Result.failure(Exception("Login init failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "loginInit exception: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun loginVerify(
        shadeId: String,
        challenge: String,
        signature: String,
        deviceModel: String,
        fcmToken: String
    ): Result<AuthResult> {
        return try {
            val response = authService.loginVerify(
                LoginVerifyRequest(shadeId, challenge, signature, deviceModel, fcmToken)
            )
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "loginVerify success — token received")
                val body = response.body()!!
                Result.success(
                    AuthResult(
                        shadeId = body.shadeId,
                        userId = body.userId,
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken,
                        deviceId = body.deviceId
                    )
                )
            } else {
                val errorBody = response.errorBody()?.string() ?: "(no body)"
                Log.e(TAG, "loginVerify failed — HTTP ${response.code()} ${response.message()} | body=$errorBody")
                Result.failure(Exception("Verification failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "loginVerify exception: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun saveSession(authResult: AuthResult) {
        keyVaultManager.saveShadeId(authResult.shadeId)
        authResult.userId?.let { keyVaultManager.saveUserId(it) }
        authResult.accessToken?.let { keyVaultManager.saveAccessToken(it) }
        authResult.refreshToken?.let { keyVaultManager.saveRefreshToken(it) }
        authResult.idPrivateKey?.let { keyVaultManager.saveEd25519PrivateKey(it) }
        authResult.encPrivateKey?.let { keyVaultManager.saveX25519PrivateKey(it) }
        authResult.deviceId?.let { keyVaultManager.saveDeviceId(it) }
    }
}