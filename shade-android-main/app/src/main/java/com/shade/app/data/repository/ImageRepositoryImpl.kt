package com.shade.app.data.repository

import com.shade.app.data.remote.api.MediaService
import com.shade.app.data.remote.dto.ImageUploadResponse
import com.shade.app.domain.repository.ImageRepository
import com.shade.app.security.KeyVaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val mediaService: MediaService,
    private val keyVaultManager: KeyVaultManager
): ImageRepository {
    override suspend fun uploadEncryptedImage(encryptedBytes: ByteArray): Result<ImageUploadResponse> {
        return try {
            val token = "Bearer ${keyVaultManager.getAccessToken()}"
            val requestBody = encryptedBytes.toRequestBody("application/octet-stream".toMediaType())
            val part = MultipartBody.Part.createFormData("image", "encrypted.bin", requestBody)

            val response = mediaService.uploadImage(token, part)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Upload failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadEncryptedFile(encryptedBytes: ByteArray, fileName: String): Result<ImageUploadResponse> {
        return try {
            val token = "Bearer ${keyVaultManager.getAccessToken()}"
            val requestBody = encryptedBytes.toRequestBody("application/octet-stream".toMediaType())
            val part = MultipartBody.Part.createFormData("file", fileName, requestBody)

            val response = mediaService.uploadFile(token, part)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("File upload failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadEncryptedFile(fileId: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                val token = "Bearer ${keyVaultManager.getAccessToken()}"
                val response = mediaService.downloadFile(token, fileId)
                if (response.isSuccessful && response.body() != null) {
                    val bytes = response.body()!!.bytes()
                    Result.success(bytes)
                } else {
                    Result.failure(Exception("File download failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun downloadEncryptedImage(imageId: String): Result<ByteArray> {
        return downloadEncryptedImageWithProgress(imageId) {}
    }

    override suspend fun downloadEncryptedImageWithProgress(
        imageId: String,
        expectedSize: Long,
        onProgress: (Float) -> Unit
    ): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                val token = "Bearer ${keyVaultManager.getAccessToken()}"
                val response = mediaService.downloadImage(token, imageId)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val totalLength = body.contentLength().let { if (it > 0) it else expectedSize }
                    val source = body.source()
                    val buffer = okio.Buffer()
                    var totalRead = 0L

                    while (true) {
                        val read = source.read(buffer, 8192)
                        if (read == -1L) break
                        totalRead += read
                        if (totalLength > 0) {
                            onProgress((totalRead.toFloat() / totalLength.toFloat()).coerceAtMost(1f))
                        }
                    }

                    onProgress(1f)
                    Result.success(buffer.readByteArray())
                } else {
                    Result.failure(Exception("Download failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}