package com.shade.app.domain.usecase.message

import android.util.Log
import com.google.gson.Gson
import com.google.protobuf.ByteString
import com.shade.app.crypto.MessageCryptoManager
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.domain.model.AudioMessageContent
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.ImageRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.proto.MessageType
import com.shade.app.proto.encryptedPayload
import com.shade.app.proto.webSocketMessage
import com.shade.app.security.KeyVaultManager
import kotlinx.coroutines.delay
import org.bouncycastle.util.encoders.Hex
import java.io.File
import java.util.UUID
import javax.inject.Inject

class SendAudioMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val chatRepository: ChatRepository,
    private val imageRepository: ImageRepository,
    private val cryptoManager: MessageCryptoManager,
    private val keyVaultManager: KeyVaultManager
) {
    private val gson = Gson()

    suspend operator fun invoke(
        receiverShadeId: String,
        audioFile: File,
        durationMs: Long
    ): Result<Unit> {
        return try {
            val audioBytes = audioFile.readBytes()

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

            val (encryptedAudioBytes, audioNonce) = cryptoManager.encryptBytes(audioBytes, derivedKey)

            val uploadResult = imageRepository.uploadEncryptedFile(encryptedAudioBytes, "audio.aac")
            val uploadResponse = uploadResult.getOrElse { return Result.failure(it) }

            val msgId = UUID.randomUUID().toString()
            val ts = System.currentTimeMillis()

            val audioContent = AudioMessageContent(
                audioId = uploadResponse.imageId,
                audioNonceHex = Hex.toHexString(audioNonce),
                durationMs = durationMs,
                sizeBytes = audioBytes.size.toLong()
            )
            val contentJson = gson.toJson(audioContent)
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
                    type = MessageType.AUDIO
                }
            }

            // WS gönderimi: başarısız olursa 1 kez retry (upload sırasında WS kopmuş olabilir)
            var isSent = messageRepository.sendWebsocketMessage(socketMsg)
            if (!isSent) {
                Log.w("SendAudio", "WS send failed, 1.5s sonra retry deneniyor...")
                delay(1500)
                isSent = messageRepository.sendWebsocketMessage(socketMsg)
                Log.d("SendAudio", "Retry sonucu: $isSent")
            }

            // Sesi yerel dosyaya kaydet
            val audioDir = File(audioFile.parentFile, "audio").also { it.mkdirs() }
            val localAudioFile = File(audioDir, "$msgId.aac")
            audioFile.copyTo(localAudioFile, overwrite = true)

            val entity = MessageEntity(
                messageId = msgId,
                senderId = myShadeId,
                receiverId = contact.shadeId,
                content = contentJson,
                timestamp = ts,
                messageType = com.shade.app.data.local.entities.MessageType.AUDIO,
                status = if (isSent) MessageStatus.SENT else MessageStatus.FAILED,
                audioPath = localAudioFile.absolutePath,
                audioDurationMs = durationMs
            )

            messageRepository.insertMessage(entity)
            chatRepository.updateLastMessage(
                chatId = receiverShadeId,
                lastMessage = "🎤 Ses mesajı",
                timestamp = ts
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SendAudio", "Audio send failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
