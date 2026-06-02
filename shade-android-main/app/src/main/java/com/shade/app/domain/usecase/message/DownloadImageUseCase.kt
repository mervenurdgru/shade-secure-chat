package com.shade.app.domain.usecase.message

import android.util.Log
import com.google.gson.Gson
import com.shade.app.crypto.MessageCryptoManager
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.domain.model.ImageMessageContent
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.ImageRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.security.KeyVaultManager
import com.shade.app.util.ImageFileManager
import org.bouncycastle.util.encoders.Hex
import javax.inject.Inject

class DownloadImageUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val cryptoManager: MessageCryptoManager,
    private val keyVaultManager: KeyVaultManager,
    private val imageFileManager: ImageFileManager
) {
    private val gson = Gson()

    suspend operator fun invoke(
        message: MessageEntity,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        return try {
            val imageContent = gson.fromJson(message.content, ImageMessageContent::class.java)

            val encryptedBytes = imageRepository.downloadEncryptedImageWithProgress(
                imageId = imageContent.imageId,
                expectedSize = imageContent.sizeBytes,
                onProgress = onProgress
            ).getOrElse { return Result.failure(it) }

            val derivedKeyHex = imageContent.imageKeyHex?.trim()?.takeIf { it.isNotEmpty() }
                ?: run {
                    val myPrivateKeyHex = keyVaultManager.getX25519PrivateKey()
                        ?: return Result.failure(Exception("Private key not found"))
                    val myShadeId = keyVaultManager.getShadeId()
                        ?: return Result.failure(Exception("ShadeId not found"))
                    val otherShadeId =
                        if (message.senderId == myShadeId) message.receiverId else message.senderId
                    val contact = contactRepository.getOrFetchContact(otherShadeId)
                        ?: return Result.failure(Exception("Contact not found"))
                    val sharedSecret = cryptoManager.generateSharedSecret(
                        myPrivateKeyHex,
                        contact.encryptionPublicKey,
                    )
                    cryptoManager.deriveConversationKey(sharedSecret, 1)
                }

            val imageNonce = Hex.decode(imageContent.imageNonceHex)
            val decryptedBytes = cryptoManager.decryptBytes(encryptedBytes, imageNonce, derivedKeyHex)

            val filePath = imageFileManager.saveDecryptedImage(message.messageId, decryptedBytes)

            messageRepository.updateImagePath(message.messageId, filePath)

            Result.success(filePath)
        } catch (e: Exception) {
            Log.e("DownloadImage", "Image download failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}