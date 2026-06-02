package com.shade.app.domain.repository

import com.shade.app.data.remote.dto.LoginInitResponse
import com.shade.app.domain.model.AuthResult

interface AuthRepository {
    suspend fun register(
        identityPublicKey: String,
        encryptedIdentityPrivateKey: String,
        encryptionPublicKey: String,
        encryptedEncryptionPrivateKey: String,
        salt: String,
        deviceModel: String,
        fcmToken: String
    ): Result<AuthResult>

    suspend fun loginInit(shadeId: String): Result<LoginInitResponse>

    suspend fun loginVerify(
        shadeId: String,
        challenge: String,
        signature: String,
        deviceModel: String,
        fcmToken: String
    ): Result<AuthResult>

    suspend fun saveSession(authResult: AuthResult)
}