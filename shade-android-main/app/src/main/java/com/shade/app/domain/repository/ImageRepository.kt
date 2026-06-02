package com.shade.app.domain.repository

import com.shade.app.data.remote.dto.ImageUploadResponse

interface ImageRepository {
    suspend fun uploadEncryptedImage(encryptedBytes: ByteArray): Result<ImageUploadResponse>
    suspend fun uploadEncryptedFile(encryptedBytes: ByteArray, fileName: String): Result<ImageUploadResponse>
    suspend fun downloadEncryptedImage(imageId: String): Result<ByteArray>
    suspend fun downloadEncryptedImageWithProgress(
        imageId: String,
        expectedSize: Long = -1L,
        onProgress: (Float) -> Unit
    ): Result<ByteArray>
    suspend fun downloadEncryptedFile(fileId: String): Result<ByteArray>
}