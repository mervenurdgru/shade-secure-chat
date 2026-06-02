package com.shade.app.domain.usecase.message

import android.util.Log
import com.google.protobuf.ByteString
import com.shade.app.crypto.MessageCryptoManager
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.proto.MessageType
import com.shade.app.proto.encryptedPayload
import com.shade.app.proto.webSocketMessage
import com.shade.app.security.KeyVaultManager
import org.bouncycastle.util.encoders.Hex
import java.util.UUID
import javax.inject.Inject

class DeleteMessageForEveryoneUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val cryptoManager: MessageCryptoManager,
    private val keyVaultManager: KeyVaultManager
) {
    suspend operator fun invoke(message: MessageEntity): Result<Unit> {
        return try {
            val otherShadeId = message.receiverId
            val contact = contactRepository.getOrFetchContact(otherShadeId)
                ?: return Result.failure(Exception("Contact not found"))

            val myPrivateKeyHex = keyVaultManager.getX25519PrivateKey()
                ?: return Result.failure(Exception("Private key not found"))
            val myShadeId = keyVaultManager.getShadeId()
                ?: return Result.failure(Exception("ShadeId not found"))
            val myUserId = keyVaultManager.getUserId()
                ?: return Result.failure(Exception("UserId not found"))

            val sharedSecret = cryptoManager.generateSharedSecret(myPrivateKeyHex, contact.encryptionPublicKey)
            val derivedKey = cryptoManager.deriveConversationKey(sharedSecret, 1)

            // İçerik: silinecek mesajın ID'si
            val (cipherHex, nonceHex) = cryptoManager.encryptMessage(message.messageId, derivedKey)

            val socketMsg = webSocketMessage {
                payload = encryptedPayload {
                    messageId = UUID.randomUUID().toString()
                    senderShadeId = myShadeId
                    senderId = myUserId
                    receiverId = contact.userId
                    ciphertext = ByteString.copyFrom(Hex.decode(cipherHex))
                    this.nonce = ByteString.copyFrom(Hex.decode(nonceHex))
                    timestamp = System.currentTimeMillis()
                    type = MessageType.DELETE
                }
            }

            messageRepository.sendWebsocketMessage(socketMsg)

            // Kendi tarafında da sil
            messageRepository.markAsDeleted(message.messageId)

            Log.d("DeleteForEveryone", "Silme bildirimi gönderildi: ${message.messageId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("DeleteForEveryone", "Hata: ${e.message}", e)
            Result.failure(e)
        }
    }
}
