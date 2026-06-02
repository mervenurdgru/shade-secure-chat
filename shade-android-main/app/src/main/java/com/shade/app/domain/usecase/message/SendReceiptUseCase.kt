package com.shade.app.domain.usecase.message

import android.util.Log
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.data.remote.api.MessageService
import com.shade.app.data.remote.dto.BatchReceiptRequest
import com.shade.app.data.remote.dto.ReceiptRequest
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.proto.ReceiptStatus
import com.shade.app.proto.deliveryReceipt
import com.shade.app.proto.webSocketMessage
import com.shade.app.security.KeyVaultManager
import javax.inject.Inject

/**
 * Emits a DELIVERED / READ receipt back to the original message sender.
 *
 * Routing rules:
 *  - 1-to-1: receipt is sent pairwise on `shade.user` keyed on the original
 *    sender's user_id. The wire's `group_id` field stays empty.
 *  - Group:  the same pairwise route is used (receipts are never broadcast),
 *    but [groupId] is populated so the sender can build their delivered-to
 *    matrix.
 *
 * READ receipts fall back to the REST endpoint if the WebSocket send fails —
 * the server then handles them via the same per-message pipeline.
 */
class SendReceiptUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val keyVaultManager: KeyVaultManager,
    private val messageService: MessageService,
) {
    /**
     * @param receiverShadeId The original *sender* of the message we're acking.
     *  Pairwise receipts always route back to them. Accepts either a shade_id
     *  (1-to-1 chats store this on `MessageEntity.senderId`) or a raw user
     *  UUID (can happen for group messages whose sender's shade_id wasn't
     *  resolvable when we received the payload).
     * @param groupId Set when the receipt refers to a group message — left
     *  blank/null for 1-to-1.
     */
    suspend operator fun invoke(
        messageId: String,
        receiverShadeId: String,
        status: MessageStatus,
        groupId: String? = null,
    ) {
        val contact = resolveContact(receiverShadeId) ?: return

        val protoStatus = when (status) {
            MessageStatus.READ -> ReceiptStatus.READ
            else -> ReceiptStatus.DELIVERED
        }

        val socketMsg = webSocketMessage {
            receipt = deliveryReceipt {
                this.messageId = messageId
                senderId = keyVaultManager.getUserId() ?: return
                senderShadeId = keyVaultManager.getShadeId() ?: return
                receiverId = contact.userId
                this.status = protoStatus
                this.timestamp = System.currentTimeMillis()
                groupId?.let { this.groupId = it }
            }
        }

        val sent = messageRepository.sendWebsocketMessage(socketMsg)
        if (sent) return

        // WebSocket failed — fallback to REST (READ only).
        if (status == MessageStatus.READ) {
            sendViaRest(messageId, "READ")
        }
    }

    suspend fun sendBatchReadReceipts(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        val receipts = messageIds.map { ReceiptRequest(messageId = it, status = "READ") }
        sendBatchViaRest(receipts)
    }

    private suspend fun sendViaRest(messageId: String, status: String) {
        sendBatchViaRest(listOf(ReceiptRequest(messageId = messageId, status = status)))
    }

    private suspend fun sendBatchViaRest(receipts: List<ReceiptRequest>) {
        try {
            val token = keyVaultManager.getAccessToken() ?: return
            val response = messageService.sendReceipts(
                "Bearer $token",
                BatchReceiptRequest(receipts)
            )
            if (!response.isSuccessful) {
                Log.e(TAG, "REST receipt failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "REST receipt error: ${e.message}")
        }
    }

    /**
     * Look up the recipient by either shade_id (preferred) or user_id UUID.
     * `getOrFetchContact(shadeId)` would call `/user/lookup/:shadeId` which
     * 404s with a noisy backend log when handed a UUID — so we sniff the
     * shape and route to `/keys/:id` for UUIDs.
     */
    private suspend fun resolveContact(idOrShadeId: String): com.shade.app.data.local.entities.ContactEntity? {
        return if (idOrShadeId.looksLikeUuid()) {
            contactRepository.getOrFetchContactByUserId(idOrShadeId)
        } else {
            contactRepository.getOrFetchContact(idOrShadeId)
        }
    }

    private fun String.looksLikeUuid(): Boolean {
        // Lightweight check, e.g. "e9073419-ae52-4165-944a-5936a6d137af".
        if (length != 36) return false
        for (i in 0 until length) {
            val c = this[i]
            val isDash = (i == 8 || i == 13 || i == 18 || i == 23)
            if (isDash) {
                if (c != '-') return false
            } else if (!c.isDigit() && c.lowercaseChar() !in 'a'..'f') {
                return false
            }
        }
        return true
    }

    private companion object {
        private const val TAG = "SendReceipt"
    }
}
