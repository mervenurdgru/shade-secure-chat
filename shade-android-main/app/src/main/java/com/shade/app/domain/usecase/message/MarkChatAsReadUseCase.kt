package com.shade.app.domain.usecase.message

import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.GroupRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.security.KeyVaultManager
import javax.inject.Inject

/**
 * Marks every unread message in a chat as READ and emits the corresponding
 * receipts.
 *
 *  - 1-to-1: a single READ receipt goes back to the chat partner (chatId
 *    happens to equal their shade_id, but we still route by message sender
 *    for correctness).
 *  - Group: one READ receipt per *distinct sender* in the unread batch,
 *    each tagged with the group_id so the sender can update their
 *    delivered-to matrix.
 *
 * The REST batch fallback at the end backstops the WebSocket path.
 */
class MarkChatAsReadUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val groupRepository: GroupRepository,
    private val sendReceiptUseCase: SendReceiptUseCase,
    private val keyVaultManager: KeyVaultManager,
) {
    suspend operator fun invoke(chatId: String) {
        val isGroup = groupRepository.getCachedGroup(chatId) != null

        val unreadMessages = messageRepository.getUnreadMessages(
            chatId = chatId,
            isGroupThread = isGroup,
            myShadeId = keyVaultManager.getShadeId(),
        )

        if (unreadMessages.isEmpty()) {
            chatRepository.resetUnreadCount(chatId)
            return
        }

        unreadMessages.forEach { msg ->
            messageRepository.updateMessageStatus(msg.messageId, MessageStatus.READ)
        }

        // Routing depends on group vs 1-to-1 — `isGroup` already resolved above.
        for (msg in unreadMessages) {
            sendReceiptUseCase(
                messageId = msg.messageId,
                receiverShadeId = msg.senderId,
                status = MessageStatus.READ,
                groupId = if (isGroup) chatId else null,
            )
        }

        sendReceiptUseCase.sendBatchReadReceipts(unreadMessages.map { it.messageId })

        chatRepository.resetUnreadCount(chatId)
    }
}
