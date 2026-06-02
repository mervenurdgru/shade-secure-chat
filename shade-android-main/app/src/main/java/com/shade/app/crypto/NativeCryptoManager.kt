package com.shade.app.crypto

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeCryptoManager @Inject constructor() {

    companion object {
        init {
            System.loadLibrary("shade_crypto")
        }
    }

    fun pbkdf2(passphrase: String, salt: ByteArray, iterations: Int, keyLenBits: Int): ByteArray {
        return nativePbkdf2(passphrase, salt, iterations, keyLenBits)
    }

    fun chaChaEncrypt(plaintext: ByteArray, key: ByteArray): Pair<ByteArray, ByteArray> {
        val combined = nativeChaChaEncrypt(plaintext, key)
        val nonce = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        return Pair(ciphertext, nonce)
    }

    fun chaChaDecrypt(ciphertext: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray {
        return nativeChaChaDecrypt(ciphertext, nonce, key)
    }

    private external fun nativePbkdf2(
        passphrase: String,
        salt: ByteArray,
        iterations: Int,
        keyLenBits: Int
    ): ByteArray

    private external fun  nativeChaChaEncrypt(
        plaintext: ByteArray,
        key: ByteArray
    ): ByteArray

    private external fun nativeChaChaDecrypt(
        ciphertext: ByteArray,
        nonce: ByteArray,
        key: ByteArray
    ): ByteArray
}