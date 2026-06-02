package com.shade.app.domain.usecase.message

import android.util.Log
import com.google.gson.Gson
import com.google.protobuf.ByteString
import com.shade.app.crypto.MessageCryptoManager
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.domain.model.ReplyContent
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.proto.MessageType
import com.shade.app.proto.encryptedPayload
import com.shade.app.proto.webSocketMessage
import com.shade.app.security.KeyVaultManager
import org.bouncycastle.util.encoders.Hex
import java.util.UUID
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val chatRepository: ChatRepository,
    private val cryptoManager: MessageCryptoManager,
    private val keyVaultManager: KeyVaultManager
) {
    private val gson = Gson()

    suspend operator fun invoke(
        receiverShadeId: String,
        content: String,
        replyToId: String? = null,
        replyToContent: String? = null
    ) {
        val contact = contactRepository.getOrFetchContact(receiverShadeId) ?: return
        val myPrivateKeyHex = keyVaultManager.getX25519PrivateKey() ?: return
        val myShadeId = keyVaultManager.getShadeId() ?: return

        val sharedSecret = cryptoManager.generateSharedSecret(myPrivateKeyHex, contact.encryptionPublicKey)
        val derivedKey = cryptoManager.deriveConversationKey(sharedSecret, 1)

        // If replying, wrap in JSON so the receiver can parse the reply reference
        val wireContent = if (replyToId != null) {
            gson.toJson(ReplyContent(t = content, rId = replyToId, rC = replyToContent?.take(80)))
        } else {
            content
        }

        val (cipherHex, nonceHex) = cryptoManager.encryptMessage(wireContent, derivedKey)

        val msgId = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()

        val socketMsg = webSocketMessage {
            payload = encryptedPayload {
                messageId = msgId
                senderShadeId = myShadeId
                senderId = keyVaultManager.getUserId() ?: return@encryptedPayload
                receiverId = contact.userId
                ciphertext = ByteString.copyFrom(Hex.decode(cipherHex))
                this.nonce = ByteString.copyFrom(Hex.decode(nonceHex))
                timestamp = ts
                type = MessageType.TEXT
            }
        }

        val isSent = messageRepository.sendWebsocketMessage(socketMsg)

        val entity = MessageEntity(
            messageId = msgId,
            senderId = myShadeId,
            receiverId = contact.shadeId,
            content = content,            // store actual text, not wire JSON
            timestamp = ts,
            messageType = com.shade.app.data.local.entities.MessageType.TEXT,
            status = if (isSent) MessageStatus.SENT else MessageStatus.FAILED,
            replyToId = replyToId,
            replyToContent = replyToContent?.take(80)
        )

        messageRepository.insertMessage(entity)
        chatRepository.updateLastMessage(
            chatId = receiverShadeId,
            lastMessage = content,
            timestamp = ts
        )
    }
}
