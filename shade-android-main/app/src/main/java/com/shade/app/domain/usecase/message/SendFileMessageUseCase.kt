package com.shade.app.domain.usecase.message

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.gson.Gson
import com.google.protobuf.ByteString
import com.shade.app.crypto.MessageCryptoManager
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.domain.model.FileMessageContent
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.ImageRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.proto.MessageType
import com.shade.app.proto.encryptedPayload
import com.shade.app.proto.webSocketMessage
import com.shade.app.security.KeyVaultManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.util.encoders.Hex
import java.util.UUID
import javax.inject.Inject

class SendFileMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val chatRepository: ChatRepository,
    private val imageRepository: ImageRepository,
    private val cryptoManager: MessageCryptoManager,
    private val keyVaultManager: KeyVaultManager,
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    suspend operator fun invoke(receiverShadeId: String, fileUri: Uri): Result<Unit> {
        return try {
            val (fileName, mimeType) = getFileInfo(fileUri)
            val fileBytes = context.contentResolver.openInputStream(fileUri)?.readBytes()
                ?: return Result.failure(Exception("Dosya okunamadı"))

            val contact = contactRepository.getOrFetchContact(receiverShadeId)
                ?: return Result.failure(Exception("Contact not found"))
            val myPrivateKeyHex = keyVaultManager.getX25519PrivateKey()
                ?: return Result.failure(Exception("Private key not found"))
            val myShadeId = keyVaultManager.getShadeId()
                ?: return Result.failure(Exception("ShadeId not found"))
            val myUserId = keyVaultManager.getUserId()
                ?: return Result.failure(Exception("UserId not found"))

            val sharedSecret = cryptoManager.generateSharedSecret(myPrivateKeyHex, contact.encryptionPublicKey)
            val derivedKey = cryptoManager.deriveConversationKey(sharedSecret, 1)

            val (encryptedBytes, fileNonce) = cryptoManager.encryptBytes(fileBytes, derivedKey)

            val uploadResult = imageRepository.uploadEncryptedFile(encryptedBytes, fileName)
            val uploadResponse = uploadResult.getOrElse { return Result.failure(it) }

            val msgId = UUID.randomUUID().toString()
            val ts = System.currentTimeMillis()

            val fileContent = FileMessageContent(
                fileId = uploadResponse.imageId,
                fileNonceHex = Hex.toHexString(fileNonce),
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = fileBytes.size.toLong()
            )
            val contentJson = gson.toJson(fileContent)
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
                    type = MessageType.FILE
                }
            }

            val isSent = messageRepository.sendWebsocketMessage(socketMsg)

            val entity = MessageEntity(
                messageId = msgId,
                senderId = myShadeId,
                receiverId = contact.shadeId,
                content = contentJson,
                timestamp = ts,
                messageType = com.shade.app.data.local.entities.MessageType.FILE,
                status = if (isSent) MessageStatus.SENT else MessageStatus.FAILED,
                fileName = fileName,
                fileSizeBytes = fileBytes.size.toLong()
            )

            messageRepository.insertMessage(entity)
            chatRepository.updateLastMessage(
                chatId = receiverShadeId,
                lastMessage = "📎 $fileName",
                timestamp = ts
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SendFile", "File send failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun getFileInfo(uri: Uri): Pair<String, String> {
        var fileName = "dosya"
        var mimeType = "application/octet-stream"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex) ?: "dosya"
            }
        }
        context.contentResolver.getType(uri)?.let { mimeType = it }
        return Pair(fileName, mimeType)
    }
}
