package com.shade.app.domain.repository

import com.shade.app.data.local.entities.ChatEntity
import com.shade.app.data.local.model.ChatWithContact
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllChats(): Flow<List<ChatEntity>>

    fun getAllChatsWithContact(): Flow<List<ChatWithContact>>
    fun observeChatWithContact(chatId: String): Flow<ChatWithContact?>
    suspend fun insertOrUpdateChat(chat: ChatEntity)
    suspend fun resetUnreadCount(chatId: String)
    /**
     * If [chatId] exists in `groups`, fix a mistaken `chats.isGroup=false` row
     * created before we keyed off the group cache during chat upserts.
     */
    suspend fun alignChatRowFromGroupCache(chatId: String)

    /**
     * Ensures the group exists in the local cache and the chat row is marked
     * `isGroup` with [groupName]. Returns the display name, or null if unknown.
     */
    suspend fun ensureGroupChatRow(groupId: String): String?

    suspend fun updateLastMessage(chatId: String, lastMessage: String, timestamp: Long)
    suspend fun updateChatWithNewMessage(chatId: String, lastMessage: String, timestamp: Long, isFromMe: Boolean = false)
    suspend fun deleteChat(chatId: String)
}
