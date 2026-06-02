package com.shade.app.domain.usecase.auth

import com.shade.app.crypto.AuthCryptoManager
import com.shade.app.crypto.MessageCryptoManager
import com.shade.app.domain.model.AuthResult
import com.shade.app.domain.repository.AuthRepository
import com.shade.app.security.KeyVaultManager
import org.bouncycastle.util.encoders.Hex
import java.security.SecureRandom
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val authCrypto: AuthCryptoManager,
    private val messageCrypto: MessageCryptoManager,
    private val keyVaultManager: KeyVaultManager
) {
    suspend operator fun invoke(mnemonic: List<String>, deviceModel: String, fcmToken: String): Result<AuthResult> {
        return try {
            val (idPub, idPriv) = authCrypto.generateEd25519KeyPairHex()
            val (encPub, encPriv) = messageCrypto.generateX25519KeyPairHex()

            val saltBytes = ByteArray(16)
            SecureRandom().nextBytes(saltBytes)
            val saltHex = Hex.toHexString(saltBytes)

            val aesKey = authCrypto.deriveAesKeyFromMnemonic(mnemonic, saltHex)

            val encryptedIdPriv = authCrypto.encryptPrivateKey(idPriv, aesKey)
            val encryptedEncPriv = authCrypto.encryptPrivateKey(encPriv, aesKey)

            val result = repository.register(
                identityPublicKey = idPub,
                encryptedIdentityPrivateKey = encryptedIdPriv,
                encryptionPublicKey = encPub,
                encryptedEncryptionPrivateKey = encryptedEncPriv,
                salt = saltHex,
                deviceModel = deviceModel,
                fcmToken = fcmToken
            )

            result.map { authResult ->
                val finalResult = authResult.copy(
                    idPrivateKey = idPriv,
                    encPrivateKey = encPriv
                )
                repository.saveSession(finalResult)
                finalResult
            }

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}