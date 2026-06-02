package com.shade.app.domain.usecase.message

import com.shade.app.data.local.entities.GroupReadReceiptEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.data.local.dao.GroupReadReceiptDao
import com.shade.app.domain.repository.GroupRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.proto.DeliveryReceipt
import com.shade.app.proto.ReceiptStatus
import javax.inject.Inject

class HandleIncomingReceiptUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val groupReadReceiptDao: GroupReadReceiptDao,
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(receipt: DeliveryReceipt) {
        val newStatus = when (receipt.status) {
            ReceiptStatus.DELIVERED -> MessageStatus.DELIVERED
            ReceiptStatus.READ -> MessageStatus.READ
            else -> MessageStatus.SENT
        }

        if (receipt.groupId.isNotBlank() && newStatus == MessageStatus.READ) {
            handleGroupRead(receipt)
        } else {
            messageRepository.updateMessageStatusIfForward(receipt.messageId, newStatus)
        }
    }

    private suspend fun handleGroupRead(receipt: DeliveryReceipt) {
        // Record this member's read
        groupReadReceiptDao.insert(
            GroupReadReceiptEntity(
                messageId = receipt.messageId,
                readerShadeId = receipt.senderShadeId,
            )
        )

        val readCount = groupReadReceiptDao.countReaders(receipt.messageId)

        // memberCount - 1: exclude the message sender (they don't read their own message)
        val members = groupRepository.getCachedMembers(receipt.groupId)
        val requiredReads = (members.size - 1).coerceAtLeast(1)

        if (readCount >= requiredReads) {
            messageRepository.updateMessageStatusIfForward(receipt.messageId, MessageStatus.READ)
        }
        // else: keep current status (SENT or DELIVERED)
    }
}
