package com.shade.app.data.repository

import com.shade.app.data.local.dao.ChatDao
import com.shade.app.data.local.entities.ChatEntity
import com.shade.app.data.local.model.ChatWithContact
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val groupRepository: GroupRepository,
) : ChatRepository {
    override fun getAllChats() = chatDao.getAllChats()

    override fun getAllChatsWithContact(): Flow<List<ChatWithContact>> {
        return chatDao.getAllChatsWithContact()
    }

    override fun observeChatWithContact(chatId: String): Flow<ChatWithContact?> {
        return chatDao.observeChatWithContact(chatId)
    }

    override suspend fun insertOrUpdateChat(chat: ChatEntity) {
        chatDao.insertOrUpdateChat(chat)
    }

    override suspend fun resetUnreadCount(chatId: String) {
        chatDao.resetUnreadCount(chatId)
    }

    override suspend fun alignChatRowFromGroupCache(chatId: String) {
        val grp = groupRepository.getCachedGroup(chatId) ?: return
        chatDao.patchGroupHints(chatId, isGroup = true, groupName = grp.name)
    }

    override suspend fun ensureGroupChatRow(groupId: String): String? {
        var grp = groupRepository.getCachedGroup(groupId)
        if (grp == null) {
            groupRepository.getGroup(groupId).getOrNull()
            grp = groupRepository.getCachedGroup(groupId)
        }
        if (grp != null) {
            chatDao.patchGroupHints(groupId, isGroup = true, groupName = grp.name)
        }
        return grp?.name
    }

    override suspend fun updateLastMessage(
        chatId: String,
        lastMessage: String,
        timestamp: Long
    ) {
        patchGroupHintsIfKnown(chatId)
        val updatedRows = chatDao.updateLastMessage(chatId, lastMessage, timestamp)
        if (updatedRows == 0) {
            chatDao.insertOrUpdateChat(emptyChat(chatId, lastMessage, timestamp, unreadCount = 0))
        }
    }

    override suspend fun updateChatWithNewMessage(
        chatId: String,
        lastMessage: String,
        timestamp: Long,
        isFromMe: Boolean,
    ) {
        patchGroupHintsIfKnown(chatId)
        if (isFromMe) {
            val updatedRows = chatDao.updateLastMessageNoIncrement(chatId, lastMessage, timestamp)
            if (updatedRows == 0) {
                chatDao.insertOrUpdateChat(emptyChat(chatId, lastMessage, timestamp, unreadCount = 0))
            }
        } else {
            val updatedRows = chatDao.incrementUnreadCount(chatId, lastMessage, timestamp)
            if (updatedRows == 0) {
                chatDao.insertOrUpdateChat(emptyChat(chatId, lastMessage, timestamp, unreadCount = 1))
            }
        }
    }

    override suspend fun deleteChat(chatId: String) {
        chatDao.deleteChat(chatId)
    }

    private suspend fun patchGroupHintsIfKnown(chatId: String) {
        val grp = groupRepository.getCachedGroup(chatId) ?: return
        chatDao.patchGroupHints(chatId, isGroup = true, groupName = grp.name)
    }

    /**
     * If [chatId] matches a cached [GroupEntity], persist the row as a group so
     * `chats.isGroup` is not left at the default (`false`). Otherwise members
     * who first receive messages before a proper chat row existed treat it as DM.
     */
    private suspend fun emptyChat(
        chatId: String,
        lastMessage: String?,
        timestamp: Long,
        unreadCount: Int,
    ): ChatEntity {
        val grp = groupRepository.getCachedGroup(chatId)
        return if (grp != null) {
            ChatEntity(
                chatId = chatId,
                lastMessage = lastMessage,
                lastMessageTimestamp = timestamp,
                unreadCount = unreadCount,
                isGroup = true,
                groupName = grp.name,
            )
        } else {
            ChatEntity(chatId, lastMessage, timestamp, unreadCount)
        }
    }
}
