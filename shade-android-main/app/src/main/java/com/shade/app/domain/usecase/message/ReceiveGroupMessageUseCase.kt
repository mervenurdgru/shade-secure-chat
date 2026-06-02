package com.shade.app.domain.usecase.message

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.shade.app.crypto.SenderKeyCryptoManager
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.data.local.entities.PeerSenderKeyEntity
import com.shade.app.domain.model.ImageMessageContent
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.domain.repository.SenderKeyRepository
import com.shade.app.domain.usecase.group.DistributeSenderKeyUseCase
import com.shade.app.domain.usecase.group.EnsureOwnSenderKeyUseCase
import com.shade.app.domain.usecase.group.PendingGroupPayloads
import com.shade.app.proto.EncryptedPayload
import com.shade.app.proto.MessageType
import com.shade.app.proto.ReceiptStatus
import com.shade.app.proto.WebSocketMessage
import com.shade.app.proto.deliveryReceipt
import com.shade.app.proto.webSocketMessage
import com.shade.app.security.KeyVaultManager
import com.shade.app.util.ActiveChatTracker
import com.shade.app.util.ImageFileManager
import com.shade.app.util.NotificationHelper
import org.bouncycastle.util.encoders.Hex
import javax.inject.Inject

/**
 * Decrypts and persists a **group** [EncryptedPayload] using the Sender Keys
 * ratchet.
 *
 * If the payload's `(sender_user, sender_device, sender_key_id)` is unknown
 * locally, the payload is buffered in [PendingGroupPayloads] and an SKDM
 * recovery is triggered by shipping our own current SKDM to that sender (see
 * "Late Join / SKDM Recovery" in `API_CONTRACT.md`). The matching SKDM,
 * once installed, drains the buffer and replays the payload.
 */
class ReceiveGroupMessageUseCase @Inject constructor(
    private val crypto: SenderKeyCryptoManager,
    private val senderKeyRepository: SenderKeyRepository,
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository,
    private val ensureOwnKey: EnsureOwnSenderKeyUseCase,
    private val distributeSenderKey: DistributeSenderKeyUseCase,
    private val pendingPayloads: PendingGroupPayloads,
    private val keyVaultManager: KeyVaultManager,
    private val notificationHelper: NotificationHelper,
    private val activeChatTracker: ActiveChatTracker,
    private val imageFileManager: ImageFileManager,
) {
    private val gson = Gson()

    suspend operator fun invoke(payload: EncryptedPayload, sendReceipt: Boolean = true) {
        try {
            val myUserId = keyVaultManager.getUserId() ?: return
            val myDeviceId = keyVaultManager.getDeviceId() ?: return

            // Self-echo: server fans our own message back to other devices.
            // Skip decryption — the sending device already persisted it.
            if (payload.senderId == myUserId && payload.senderDeviceId == myDeviceId) {
                Log.d(TAG, "Skipping own group echo: ${payload.messageId}")
                return
            }

            val peer = senderKeyRepository.getPeer(
                groupId = payload.groupId,
                peerUserId = payload.senderId,
                peerDeviceId = payload.senderDeviceId,
                keyId = payload.senderKeyId.toLong(),
            )

            if (peer == null) {
                pendingPayloads.store(payload)
                Log.d(
                    TAG,
                    "Buffering unknown sender-key payload ${payload.messageId} " +
                            "(group=${payload.groupId} key=${payload.senderKeyId})"
                )
                // Trigger SKDM recovery by sending our own SKDM to the peer.
                // They'll observe and reciprocate with theirs.
                runCatching {
                    val ownKey = ensureOwnKey(payload.groupId)
                    distributeSenderKey(ownKey, force = true, onlyUserId = payload.senderId)
                }.onFailure { Log.w(TAG, "SKDM recovery dispatch failed: ${it.message}") }
                return
            }

            decryptAndPersist(payload, peer, sendReceipt)
        } catch (e: Exception) {
            Log.e(TAG, "Exception decrypting group payload: ${e.message}", e)
        }
    }

    /**
     * Public entry-point used by the GKD handler to replay buffered payloads
     * once a SenderKey is finally installed.
     */
    suspend fun replayWithPeerKey(payload: EncryptedPayload, peer: PeerSenderKeyEntity) {
        decryptAndPersist(payload, peer, sendReceipt = true)
    }

    private suspend fun decryptAndPersist(
        payload: EncryptedPayload,
        peer: PeerSenderKeyEntity,
        sendReceipt: Boolean,
    ) {
        // Catch the ratchet up if the message is ahead of where we are.
        var chainKey = Hex.decode(peer.chainKeyHex)
        var chainIndex = peer.chainIndex
        val targetIndex = payload.chainIndex
        if (targetIndex < chainIndex) {
            Log.w(
                TAG,
                "Out-of-order group payload ${payload.messageId} " +
                        "(target=$targetIndex < current=$chainIndex); dropping"
            )
            return
        }
        while (chainIndex < targetIndex) {
            chainKey = crypto.deriveNextChainKey(chainKey)
            chainIndex++
        }

        val msgKey = crypto.deriveMessageKey(chainKey)

        // Signature verify
        val signatureOk = crypto.verifyGroupPayload(
            signingPublicKey = Hex.decode(peer.signingPublicKeyHex),
            signature = payload.signature.toByteArray(),
            ciphertextWithTag = payload.ciphertext.toByteArray(),
            nonce = payload.nonce.toByteArray(),
            groupId = payload.groupId,
            keyId = payload.senderKeyId.toLong(),
            chainIndex = payload.chainIndex,
        )
        if (!signatureOk) {
            Log.w(TAG, "Signature verify failed for ${payload.messageId} — probable spoof, dropping")
            return
        }

        // AEAD decrypt
        val aad = crypto.buildAad(
            groupId = payload.groupId,
            senderDeviceId = payload.senderDeviceId,
            keyId = payload.senderKeyId.toLong(),
            chainIndex = payload.chainIndex,
        )
        val plaintextBytes = try {
            crypto.aeadDecrypt(
                ciphertextWithTag = payload.ciphertext.toByteArray(),
                nonce = payload.nonce.toByteArray(),
                key = msgKey,
                aad = aad,
            )
        } catch (e: Exception) {
            Log.e(TAG, "AEAD decrypt failed for ${payload.messageId}: ${e.message}")
            return
        }

        // Advance peer ratchet locally (chain_key += 1 hop, chain_index += 1).
        val advancedChainKey = crypto.deriveNextChainKey(chainKey)
        senderKeyRepository.advancePeer(
            groupId = payload.groupId,
            peerUserId = peer.peerUserId,
            peerDeviceId = peer.peerDeviceId,
            keyId = peer.keyId,
            chainKeyHex = Hex.toHexString(advancedChainKey),
            chainIndex = chainIndex + 1,
        )

        val plaintext = String(plaintextBytes, Charsets.UTF_8)
        persistAndNotify(payload, plaintext)

        if (sendReceipt) {
            sendDeliveredReceipt(payload)
        }
    }

    private suspend fun persistAndNotify(payload: EncryptedPayload, plaintext: String) {
        // The chat UI keys "is-me" off shade_id, so resolve / cache the
        // sender's shade_id here. Inbox payloads don't carry sender_shade_id,
        // only sender_id (UUID). Fall back to the raw UUID if every lookup
        // fails — better an ugly label than a lost message.
        val senderContact = contactRepository.getOrFetchContactByUserId(payload.senderId)
        val senderShadeId = payload.senderShadeId
            .ifEmpty { senderContact?.shadeId.orEmpty() }
            .ifEmpty { payload.senderId }

        val msgType = when (payload.type) {
            MessageType.IMAGE -> com.shade.app.data.local.entities.MessageType.IMAGE
            MessageType.AUDIO -> com.shade.app.data.local.entities.MessageType.AUDIO
            else              -> com.shade.app.data.local.entities.MessageType.TEXT
        }

        var thumbnailPath: String? = null
        if (msgType == com.shade.app.data.local.entities.MessageType.IMAGE) {
            try {
                val imageContent = gson.fromJson(plaintext, ImageMessageContent::class.java)
                val thumbnailBytes = Base64.decode(imageContent.thumbnailBase64, Base64.NO_WRAP)
                thumbnailPath = imageFileManager.saveThumbnail(payload.messageId, thumbnailBytes)
            } catch (e: Exception) {
                Log.e(TAG, "Thumbnail save failed: ${e.message}")
            }
        }

        val entity = MessageEntity(
            messageId = payload.messageId,
            senderId = senderShadeId,
            receiverId = payload.groupId,
            isGroupThread = true,
            content = plaintext,
            timestamp = payload.timestamp,
            messageType = msgType,
            status = MessageStatus.DELIVERED,
            thumbnailPath = thumbnailPath,
        )
        messageRepository.insertMessage(entity)

        val chatId = payload.groupId
        val senderLabel = senderContact
            ?.let { it.savedName ?: it.profileName ?: it.shadeId }
            ?: senderShadeId
        val previewBody = when (msgType) {
            com.shade.app.data.local.entities.MessageType.IMAGE -> "📷 Fotoğraf"
            com.shade.app.data.local.entities.MessageType.AUDIO -> "🎤 Ses mesajı"
            com.shade.app.data.local.entities.MessageType.FILE -> "📄 Dosya"
            else -> plaintext
        }
        val lastMessagePreview = "$senderLabel: $previewBody"

        val groupName = chatRepository.ensureGroupChatRow(chatId) ?: chatId

        if (activeChatTracker.activeShadeId == chatId) {
            chatRepository.updateLastMessage(chatId, lastMessagePreview, payload.timestamp)
        } else {
            chatRepository.updateChatWithNewMessage(chatId, lastMessagePreview, payload.timestamp)
            notificationHelper.showMessageNotification(
                chatId = chatId,
                chatTitle = groupName,
                message = lastMessagePreview,
            )
        }
    }

    private suspend fun sendDeliveredReceipt(payload: EncryptedPayload) {
        val myUserId = keyVaultManager.getUserId() ?: return
        val myShadeId = keyVaultManager.getShadeId() ?: return
        val wsMsg: WebSocketMessage = webSocketMessage {
            receipt = deliveryReceipt {
                this.messageId = payload.messageId
                this.senderId = myUserId
                this.senderShadeId = myShadeId
                this.receiverId = payload.senderId
                this.status = ReceiptStatus.DELIVERED
                this.timestamp = System.currentTimeMillis()
                this.groupId = payload.groupId
            }
        }
        messageRepository.sendWebsocketMessage(wsMsg)
    }

    private companion object {
        private const val TAG = "ReceiveGroupMsg"
    }
}
