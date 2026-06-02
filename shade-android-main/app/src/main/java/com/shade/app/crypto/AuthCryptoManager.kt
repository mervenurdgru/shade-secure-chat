package com.shade.app.crypto

import android.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Hex
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthCryptoManager @Inject constructor(
    private val nativeCrypto: NativeCryptoManager
) {

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val ITERATION_COUNT = 100_000
        private const val KEY_LENGTH = 256
        private const val IV_LENGTH = 12
    }

    fun generateEd25519KeyPairHex(): Pair<String, String> {
        val random = SecureRandom()
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(random))

        val keyPair = generator.generateKeyPair()
        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters

        val publicKeyHex = Hex.toHexString(publicKey.encoded)
        val privateKeyHex = Hex.toHexString(privateKey.encoded)

        return Pair(publicKeyHex, privateKeyHex)
    }

    fun signChallenge(privateKeyHex: String, challengeHex: String): String {
        val privateKeyBytes = Hex.decode(privateKeyHex)
        val challengeBytes = Hex.decode(challengeHex)

        val privateKeyParam = Ed25519PrivateKeyParameters(privateKeyBytes, 0)

        val signer = Ed25519Signer()
        signer.init(true, privateKeyParam)
        signer.update(challengeBytes, 0, challengeBytes.size)

        val signatureBytes = signer.generateSignature()

        return Hex.toHexString(signatureBytes)
    }

    fun deriveAesKeyFromMnemonic(mnemonic: List<String>, salt: String): SecretKeySpec {
        val passphrase = mnemonic.joinToString(" ")
        val keyBytes = nativeCrypto.pbkdf2(passphrase, salt.toByteArray(Charsets.UTF_8), ITERATION_COUNT, KEY_LENGTH)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encryptPrivateKey(privateKeyHex: String, aesKey: SecretKeySpec): String {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val encryptedBytes = cipher.doFinal(privateKeyHex.toByteArray(Charsets.UTF_8))

        val combined = iv + encryptedBytes
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptPrivateKey(encryptedDataB64: String, aesKey: SecretKeySpec): String {
        val combined = Base64.decode(encryptedDataB64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val encryptedBytes = combined.copyOfRange(IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}