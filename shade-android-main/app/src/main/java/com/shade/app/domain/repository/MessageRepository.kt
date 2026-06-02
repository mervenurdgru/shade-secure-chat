package com.shade.app.domain.repository

import androidx.paging.PagingData
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.proto.WebSocketMessage
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun insertMessage(message: MessageEntity)
    fun getMessagesForChat(chatId: String, isGroupThread: Boolean): Flow<List<MessageEntity>>

    /** Sayfalı mesaj akışı — büyük sohbetler için bellek tasarruflu yükleme. */
    fun getMessagesForChatPaged(chatId: String, isGroupThread: Boolean): Flow<PagingData<MessageEntity>>
    suspend fun getUnreadMessages(
        chatId: String,
        isGroupThread: Boolean,
        myShadeId: String? = null,
    ): List<MessageEntity>
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)
    suspend fun sendWebsocketMessage(message: WebSocketMessage): Boolean
    fun observeIncomingMessages(): Flow<WebSocketMessage>
    suspend fun updateImagePath(messageId: String, path: String)
    suspend fun getMessageStatus(messageId: String): MessageStatus?
    suspend fun updateMessageStatusIfForward(messageId: String, newStatus: MessageStatus)
    suspend fun deleteMessage(message: MessageEntity)
    suspend fun deleteAllGroupMessages(groupId: String)
    fun searchMessages(chatId: String, query: String, isGroupThread: Boolean): Flow<List<MessageEntity>>
    suspend fun markAsDeleted(messageId: String)
    suspend fun updateMessageContent(messageId: String, content: String)
    suspend fun countMediaMessages(chatId: String, isGroupThread: Boolean): Int
    suspend fun getMediaMessages(chatId: String, isGroupThread: Boolean): List<MessageEntity>
    suspend fun updateAudioPath(messageId: String, path: String, durationMs: Long)
    suspend fun updateFilePath(messageId: String, path: String)
}
