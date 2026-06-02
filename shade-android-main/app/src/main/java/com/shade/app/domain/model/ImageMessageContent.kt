package com.shade.app.domain.model

data class ImageMessageContent(
    val imageId: String,
    val thumbnailBase64: String,
    val imageNonceHex: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    /**
     * Grup mesajları: yüklenen blob şifre çözüm anahtarı (UTF-8 JSON içinde,
     * dışarıda sender-key AEAD ile korunur). 1:1 mesajlarda yoktur — indirme
     * o zaman pairwise ECDH ile yapılır.
     */
    val imageKeyHex: String? = null,
)