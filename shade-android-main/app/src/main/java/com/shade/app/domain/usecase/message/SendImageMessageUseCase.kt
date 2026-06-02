package com.shade.app.domain.usecase.message

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.protobuf.ByteString
import com.shade.app.crypto.MessageCryptoManager
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.domain.model.ImageMessageContent
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.ImageRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.proto.MessageType
import com.shade.app.proto.encryptedPayload
import com.shade.app.proto.webSocketMessage
import com.shade.app.security.KeyVaultManager
import com.shade.app.util.ImageFileManager
import com.shade.app.util.ImageProcessor
import org.bouncycastle.util.encoders.Hex
import java.util.UUID
import javax.inject.Inject

class SendImageMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val chatRepository: ChatRepository,
    private val imageRepository: ImageRepository,
    private val cryptoManager: MessageCryptoManager,
    private val keyVaultManager: KeyVaultManager,
    private val imageProcessor: ImageProcessor,
    private val imageFileManager: ImageFileManager
) {
    private val gson = Gson()

    suspend operator fun invoke(receiverShadeId: String, imageUri: Uri) {
        try {
            val compressedBytes = imageProcessor.compressImage(imageUri)
            val thumbnailBytes = imageProcessor.generateThumbnail(imageUri)
            val (width, height) = imageProcessor.getImageDimensions(imageUri)

            val contact = contactRepository.getOrFetchContact(receiverShadeId)
                ?: throw Exception("Contact not found")
            val myPrivateKeyHex = keyVaultManager.getX25519PrivateKey()
                ?: throw Exception("Private key not found")
            val myShadeId = keyVaultManager.getShadeId()
                ?: throw Exception("ShadeId not found")
            val myUserId = keyVaultManager.getUserId()
                ?: throw Exception("UserId not found")

            val sharedSecret = cryptoManager.generateSharedSecret(myPrivateKeyHex, contact.encryptionPublicKey)
            val derivedKey = cryptoManager.deriveConversationKey(sharedSecret, 1)

            val (encryptedImageBytes, imageNonce) = cryptoManager.encryptBytes(compressedBytes, derivedKey)

            val uploadResult = imageRepository.uploadEncryptedImage(encryptedImageBytes)
            val uploadresponse = uploadResult.getOrThrow()

            val msgId = UUID.randomUUID().toString()
            val ts = System.currentTimeMillis()

            val imageContent = ImageMessageContent(
                imageId = uploadresponse.imageId,
                thumbnailBase64 = Base64.encodeToString(thumbnailBytes, Base64.NO_WRAP),
                imageNonceHex = Hex.toHexString(imageNonce),
                width = width,
                height = height,
                sizeBytes = compressedBytes.size.toLong()
            )
            val contentJson = gson.toJson(imageContent)

            val (cipherHex, nonceHex) = cryptoManager.encryptMessage(contentJson, derivedKey)

            val socketMsg = webSocketMessage {
                payload = encryptedPayload {
                    messageId = msgId
                    senderShadeId = myShadeId
                    senderId = myUserId
                    receiverId = contact.userId
                    ciphertext = ByteString.copyFrom(Hex.decode(cipherHex))
                    nonce = ByteString.copyFrom(Hex.decode(nonceHex))
                    timestamp = ts
                    type = MessageType.IMAGE
                }
            }

            val isSent = messageRepository.sendWebsocketMessage(socketMsg)

            val thumbnailPath = imageFileManager.saveThumbnail(msgId, thumbnailBytes)
            val imagePath = imageFileManager.saveDecryptedImage(msgId, compressedBytes)

            val entity = MessageEntity(
                messageId = msgId,
                senderId = myShadeId,
                receiverId = contact.shadeId,
                content = contentJson,
                timestamp = ts,
                messageType = com.shade.app.data.local.entities.MessageType.IMAGE,
                status = if (isSent) MessageStatus.SENT else MessageStatus.FAILED,
                thumbnailPath = thumbnailPath,
                imagePath = imagePath
            )

            messageRepository.insertMessage(entity)
            chatRepository.updateLastMessage(
                chatId = receiverShadeId,
                lastMessage = "\uD83D\uDCF7 Fotoğraf",
                timestamp = ts
            )

        } catch (e: Exception) {
            Log.e("SendImage", "Image send failed: ${e.message}", e)
            throw e
        }
    }
}
