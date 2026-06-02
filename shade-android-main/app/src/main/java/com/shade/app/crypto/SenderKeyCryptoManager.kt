package com.shade.app.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crypto primitives for the **Sender Keys** group messaging protocol (see
 * `API_CONTRACT.md` → "Group Messaging Protocol").
 *
 * Pure-Kotlin BouncyCastle implementation so we don't have to round-trip
 * through the Rust JNI layer for every message — the AEAD primitive here
 * supports Additional Authenticated Data (AAD), which the Rust layer doesn't
 * expose today.
 *
 * Wire format for [aeadEncrypt] is `ciphertext || tag` (Poly1305 tag appended,
 * matching the protobuf `ciphertext` field). The 12-byte nonce is generated
 * fresh per call.
 */
@Singleton
class SenderKeyCryptoManager @Inject constructor() {

    private val random = SecureRandom()

    // ── HKDF ratchet ─────────────────────────────────────────────────────────

    /** `msg_key = HKDF(chain_key, info="msg", 32)`. */
    fun deriveMessageKey(chainKey: ByteArray): ByteArray =
        hkdf(chainKey, INFO_MSG, 32)

    /** `new_chain_key = HKDF(chain_key, info="chain", 32)`. */
    fun deriveNextChainKey(chainKey: ByteArray): ByteArray =
        hkdf(chainKey, INFO_CHAIN, 32)

    private fun hkdf(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(ikm, ByteArray(0), info))
        return ByteArray(length).also { hkdf.generateBytes(it, 0, length) }
    }

    // ── AEAD (ChaCha20-Poly1305) with AAD ────────────────────────────────────

    /**
     * Encrypts [plaintext] with a fresh random 12-byte nonce. The returned
     * [Ciphertext.body] is `ciphertext || tag` so it can be sent as the
     * single `ciphertext` field in [com.shade.app.proto.EncryptedPayload].
     */
    fun aeadEncrypt(plaintext: ByteArray, key: ByteArray, aad: ByteArray): Ciphertext {
        require(key.size == 32) { "AEAD key must be 32 bytes" }
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = ChaCha20Poly1305().apply {
            init(true, AEADParameters(KeyParameter(key), 128, nonce, aad))
        }
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val processed = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, processed)
        return Ciphertext(body = out, nonce = nonce)
    }

    fun aeadDecrypt(
        ciphertextWithTag: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        require(key.size == 32) { "AEAD key must be 32 bytes" }
        require(nonce.size == 12) { "ChaCha20-Poly1305 nonce must be 12 bytes" }
        val cipher = ChaCha20Poly1305().apply {
            init(false, AEADParameters(KeyParameter(key), 128, nonce, aad))
        }
        val out = ByteArray(cipher.getOutputSize(ciphertextWithTag.size))
        val processed = cipher.processBytes(
            ciphertextWithTag, 0, ciphertextWithTag.size, out, 0
        )
        val written = cipher.doFinal(out, processed)
        return if (written + processed == out.size) out else out.copyOf(processed + written)
    }

    data class Ciphertext(val body: ByteArray, val nonce: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Ciphertext && body.contentEquals(other.body) && nonce.contentEquals(other.nonce)

        override fun hashCode(): Int = body.contentHashCode() * 31 + nonce.contentHashCode()
    }

    // ── Ed25519 sign / verify ────────────────────────────────────────────────

    fun generateSigningKeyPair(): SigningKeyPair {
        val gen = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(random)) }
        val kp = gen.generateKeyPair()
        val priv = (kp.private as Ed25519PrivateKeyParameters).encoded
        val pub = (kp.public as Ed25519PublicKeyParameters).encoded
        return SigningKeyPair(publicKey = pub, privateKey = priv)
    }

    fun signGroupPayload(
        signingPrivateKey: ByteArray,
        ciphertextWithTag: ByteArray,
        nonce: ByteArray,
        groupId: String,
        keyId: Long,
        chainIndex: Long,
    ): ByteArray {
        val msg = buildSignaturePreimage(ciphertextWithTag, nonce, groupId, keyId, chainIndex)
        val signer = Ed25519Signer().apply {
            init(true, Ed25519PrivateKeyParameters(signingPrivateKey, 0))
            update(msg, 0, msg.size)
        }
        return signer.generateSignature()
    }

    fun verifyGroupPayload(
        signingPublicKey: ByteArray,
        signature: ByteArray,
        ciphertextWithTag: ByteArray,
        nonce: ByteArray,
        groupId: String,
        keyId: Long,
        chainIndex: Long,
    ): Boolean {
        val msg = buildSignaturePreimage(ciphertextWithTag, nonce, groupId, keyId, chainIndex)
        return runCatching {
            val verifier = Ed25519Signer().apply {
                init(false, Ed25519PublicKeyParameters(signingPublicKey, 0))
                update(msg, 0, msg.size)
            }
            verifier.verifySignature(signature)
        }.getOrDefault(false)
    }

    /** 32 random bytes; used as a fresh root chain_key on first send / rotation. */
    fun generateChainKey(): ByteArray = ByteArray(32).also(random::nextBytes)

    data class SigningKeyPair(val publicKey: ByteArray, val privateKey: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is SigningKeyPair &&
                    publicKey.contentEquals(other.publicKey) &&
                    privateKey.contentEquals(other.privateKey)

        override fun hashCode(): Int =
            publicKey.contentHashCode() * 31 + privateKey.contentHashCode()
    }

    // ── Canonical AAD / signature preimage ───────────────────────────────────

    /**
     * AAD for the group AEAD frame:
     * `group_id_bytes || sender_device_id_bytes || keyId_be4 || chainIndex_be8`.
     *
     * Both ID strings are UUIDs (fixed 36 chars), so concatenating UTF-8 bytes
     * without length prefixing is unambiguous. The numeric tails are
     * fixed-width big-endian and bind the (group, device, rotation, index)
     * tuple as required by the contract's Send Algorithm.
     */
    fun buildAad(
        groupId: String,
        senderDeviceId: String,
        keyId: Long,
        chainIndex: Long,
    ): ByteArray = ByteBuffer
        .allocate(groupId.length + senderDeviceId.length + 4 + 8)
        .order(ByteOrder.BIG_ENDIAN)
        .put(groupId.toByteArray(Charsets.UTF_8))
        .put(senderDeviceId.toByteArray(Charsets.UTF_8))
        .putInt(keyId.toInt())
        .putLong(chainIndex)
        .array()

    private fun buildSignaturePreimage(
        ciphertextWithTag: ByteArray,
        nonce: ByteArray,
        groupId: String,
        keyId: Long,
        chainIndex: Long,
    ): ByteArray {
        val groupBytes = groupId.toByteArray(Charsets.UTF_8)
        return ByteBuffer
            .allocate(ciphertextWithTag.size + nonce.size + groupBytes.size + 4 + 8)
            .order(ByteOrder.BIG_ENDIAN)
            .put(ciphertextWithTag)
            .put(nonce)
            .put(groupBytes)
            .putInt(keyId.toInt())
            .putLong(chainIndex)
            .array()
    }

    private companion object {
        private val INFO_MSG = "msg".toByteArray(Charsets.UTF_8)
        private val INFO_CHAIN = "chain".toByteArray(Charsets.UTF_8)
    }
}
