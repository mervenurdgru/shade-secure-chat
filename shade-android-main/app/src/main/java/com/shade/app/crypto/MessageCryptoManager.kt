package com.shade.app.crypto

import android.util.Base64
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.util.encoders.Hex
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageCryptoManager @Inject constructor(
    private val nativeCrypto: NativeCryptoManager
) {

    fun generateX25519KeyPairHex(): Pair<String, String> {
        val random = SecureRandom()
        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(random))

        val keyPair = generator.generateKeyPair()
        val publicKey = keyPair.public as X25519PublicKeyParameters
        val privateKey = keyPair.private as X25519PrivateKeyParameters

        val publicKeyHex = Hex.toHexString(publicKey.encoded)
        val privateKeyHex = Hex.toHexString(privateKey.encoded)

        return Pair(publicKeyHex, privateKeyHex)
    }

    fun generateSharedSecret(privateKeyHex: String, otherPublicKeyHex: String): String {
        val privateKeyBytes = Hex.decode(privateKeyHex.trim())
        val otherPublicKeyBytes = decodePublicKeyBytes(otherPublicKeyHex.trim())

        val privateKeyParam = X25519PrivateKeyParameters(privateKeyBytes, 0)
        val otherPublicKeyParam = X25519PublicKeyParameters(otherPublicKeyBytes, 0)

        val agreement = X25519Agreement()
        agreement.init(privateKeyParam)

        val sharedSecretBytes = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(otherPublicKeyParam, sharedSecretBytes, 0)

        return Hex.toHexString(sharedSecretBytes)
    }

    /**
     * Matches how the backend may return keys: Shade clients register hex,
     * but some JSON payloads use standard base64.
     */
    private fun decodePublicKeyBytes(encoded: String): ByteArray {
        val stripped = encoded.removePrefix("0x").removePrefix("0X")
        if (stripped.length % 2 == 0 &&
            stripped.matches(Regex("^[0-9a-fA-F]+$"))
        ) {
            return Hex.decode(stripped)
        }
        return Base64.decode(stripped, Base64.DEFAULT)
    }

    fun deriveConversationKey(sharedSecretHex: String, keyVersion: Int): String {
        val sharedSecretBytes = Hex.decode(sharedSecretHex)

        val infoString = "Shade-Message-Key-v$keyVersion"
        val infoBytes = infoString.toByteArray(Charsets.UTF_8)

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(sharedSecretBytes, ByteArray(0), infoBytes))

        val derivedKey = ByteArray(32)
        hkdf.generateBytes(derivedKey, 0, 32)

        return Hex.toHexString(derivedKey)
    }

    fun encryptMessage(plaintext: String, derivedKeyHex: String): Pair<String, String> {
        val keyBytes = Hex.decode(derivedKeyHex)
        val plainBytes = plaintext.toByteArray(Charsets.UTF_8)

        val (ciphertext, nonce) = nativeCrypto.chaChaEncrypt(plainBytes, keyBytes)

        return Pair(Hex.toHexString(ciphertext), Hex.toHexString(nonce))
    }

    fun decryptMessage(ciphertextHex: String, nonceHex: String, derivedKeyHex: String): String {
        val keyBytes = Hex.decode(derivedKeyHex)
        val ciphertextBytes = Hex.decode(ciphertextHex)
        val nonceBytes = Hex.decode(nonceHex)

        val plaintext = nativeCrypto.chaChaDecrypt(ciphertextBytes, nonceBytes, keyBytes)

        return String(plaintext, Charsets.UTF_8)
    }


    fun encryptBytes(plainBytes: ByteArray, derivedKeyHex: String): Pair<ByteArray, ByteArray> {
        val keyBytes = Hex.decode(derivedKeyHex)
        return nativeCrypto.chaChaEncrypt(plainBytes, keyBytes)
    }

    fun decryptBytes(cipherTextBytes: ByteArray, nonceBytes: ByteArray, derivedKeyHex: String): ByteArray {
        val keyBytes = Hex.decode(derivedKeyHex)
        return nativeCrypto.chaChaDecrypt(cipherTextBytes, nonceBytes, keyBytes)
    }

}