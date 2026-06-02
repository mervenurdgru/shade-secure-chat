package com.shade.app.crypto

import android.util.Log
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

/**
 * Web pairing pipeline crypto'su.
 *
 * 1. [startSession]  : ephemeral X25519 keypair üretir, web pub ile ECDH yapar,
 *                      HKDF-SHA256 (info "Shade-Web-Pairing-v1") ile 32 byte
 *                      "transfer key" türetir, plaintext bundle'ı bu key ile
 *                      ChaCha20-Poly1305 ile şifreler. Transfer key + android
 *                      pub manager içinde tutulur.
 * 2. [encryptWithTransferKey] : startSession sonrası, AYNI transfer key ile
 *                               ek payload'lar (mesaj sync vs.) şifrelenir.
 *                               Her çağrı yeni bir nonce üretir.
 * 3. [clearSession]  : transfer key'i sıfırlar.
 */
@Singleton
class WebPairingCryptoManager @Inject constructor(
    private val nativeCrypto: NativeCryptoManager
) {

    data class HandshakeResult(
        val ciphertextHex: String,
        val nonceHex: String,
        val androidPublicKeyHex: String
    )

    data class EncryptedBlob(
        val ciphertextHex: String,
        val nonceHex: String
    )

    @Volatile private var transferKey: ByteArray? = null
    @Volatile private var androidPubHex: String? = null

    /**
     * Pairing oturumunu başlatır ve session bundle'ı transfer key ile şifreler.
     * Sonuç authorize endpoint'ine gönderilir; transfer key sonraki encrypt
     * çağrıları için manager'da saklanır.
     */
    @Synchronized
    fun startSession(sessionBundle: ByteArray, webPublicKeyHex: String): HandshakeResult {
        val webPubBytes = Hex.decode(webPublicKeyHex)
        require(webPubBytes.size == X25519_KEY_BYTES) { "invalid web public key length" }

        val rng = SecureRandom()
        val gen = X25519KeyPairGenerator().apply {
            init(X25519KeyGenerationParameters(rng))
        }
        val pair = gen.generateKeyPair()
        val androidPriv = pair.private as X25519PrivateKeyParameters
        val androidPub = pair.public as X25519PublicKeyParameters
        val webPub = X25519PublicKeyParameters(webPubBytes, 0)

        val agreement = X25519Agreement().apply { init(androidPriv) }
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(webPub, shared, 0)

        val derivedKey = ByteArray(KEY_SIZE_BYTES).also {
            HKDFBytesGenerator(SHA256Digest()).apply {
                init(HKDFParameters(shared, ByteArray(0), HKDF_INFO))
            }.generateBytes(it, 0, KEY_SIZE_BYTES)
        }

        Log.d(TAG, "X25519 web_pub      : $webPublicKeyHex")
        Log.d(TAG, "X25519 android_priv : ${Hex.toHexString(androidPriv.encoded)}")
        Log.d(TAG, "X25519 android_pub  : ${Hex.toHexString(androidPub.encoded)}")
        Log.d(TAG, "ECDH shared_secret  : ${Hex.toHexString(shared)}")
        Log.d(TAG, "HKDF info           : \"${String(HKDF_INFO, Charsets.UTF_8)}\"  salt=empty")
        Log.d(TAG, "HKDF transfer_key   : ${Hex.toHexString(derivedKey)}  (${derivedKey.size}B)")

        val (cipher, nonce) = nativeCrypto.chaChaEncrypt(sessionBundle, derivedKey)
        Log.d(TAG, "Bundle nonce        : ${Hex.toHexString(nonce)}  (${nonce.size}B)")
        Log.d(TAG, "Bundle ciphertext   : ${Hex.toHexString(cipher)}  (${cipher.size}B, içerir 16B tag)")

        transferKey?.fill(0)
        transferKey = derivedKey
        androidPubHex = Hex.toHexString(androidPub.encoded)

        return HandshakeResult(
            ciphertextHex = Hex.toHexString(cipher),
            nonceHex = Hex.toHexString(nonce),
            androidPublicKeyHex = androidPubHex!!
        )
    }

    /**
     * [startSession] sonrası, aynı transfer key ile ek payload'ları şifreler.
     * Her çağrı yeni nonce üretir; aynı nonce-key ikilisi tekrar etmez.
     */
    fun encryptWithTransferKey(plaintext: ByteArray): EncryptedBlob {
        val key = transferKey ?: error("Web pairing session not started")
        val (cipher, nonce) = nativeCrypto.chaChaEncrypt(plaintext, key)
        return EncryptedBlob(
            ciphertextHex = Hex.toHexString(cipher),
            nonceHex = Hex.toHexString(nonce)
        )
    }

    fun isSessionActive(): Boolean = transferKey != null

    @Synchronized
    fun clearSession() {
        transferKey?.fill(0)
        transferKey = null
        androidPubHex = null
        Log.d(TAG, "Session cleared")
    }

    private companion object {
        const val TAG = "WebPairingCrypto"
        const val KEY_SIZE_BYTES = 32
        const val X25519_KEY_BYTES = 32
        val HKDF_INFO = "shade-web-auth-v1".toByteArray(Charsets.UTF_8)
    }
}
