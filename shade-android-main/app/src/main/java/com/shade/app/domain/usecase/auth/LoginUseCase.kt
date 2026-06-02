package com.shade.app.domain.usecase.auth

import com.shade.app.crypto.AuthCryptoManager
import com.shade.app.domain.model.AuthResult
import com.shade.app.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val authCrypto: AuthCryptoManager,
) {
    suspend operator fun invoke(
        shadeId: String,
        mnemonic: List<String>,
        deviceModel: String,
        fcmToken: String
    ): Result<AuthResult> {
        return try {
            val initResult = repository.loginInit(shadeId)
            val loginData = initResult.getOrThrow()

            val aesKey = authCrypto.deriveAesKeyFromMnemonic(mnemonic, loginData.salt)

            val decryptedIdPrivateKeyHex = authCrypto.decryptPrivateKey(
                loginData.encryptedIdentityPrivateKey,
                aesKey
            )

            val decryptedEncPrivateKeyHex = authCrypto.decryptPrivateKey(
                loginData.encryptedEncryptionPrivateKey,
                aesKey
            )

            val signature = authCrypto.signChallenge(
                decryptedIdPrivateKeyHex,
                loginData.challenge
            )

            repository.loginVerify(
                shadeId = shadeId,
                challenge = loginData.challenge,
                signature = signature,
                deviceModel = deviceModel,
                fcmToken = fcmToken
            ).map { authResult ->
                val finalResult = authResult.copy(
                    idPrivateKey = decryptedIdPrivateKeyHex,
                    encPrivateKey = decryptedEncPrivateKeyHex
                )

                repository.saveSession(finalResult)
                finalResult
            }
        } catch (e: Exception) {
            android.util.Log.e("LoginUseCase", "Login failed: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }
}
