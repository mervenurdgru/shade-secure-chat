package com.shade.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.shade.app.data.local.dao.MessageDao
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.data.remote.websocket.ShadeWebSocketManager
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.proto.WebSocketMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ShadeRepo"

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val webSocketManager: ShadeWebSocketManager
) : MessageRepository {

    override suspend fun insertMessage(message: MessageEntity) = messageDao.insertMessage(message)

    override fun getMessagesForChat(chatId: String, isGroupThread: Boolean): Flow<List<MessageEntity>> =
        if (isGroupThread) messageDao.getGroupMessagesForChat(chatId)
        else messageDao.getDmMessagesForChat(chatId)

    override fun getMessagesForChatPaged(chatId: String, isGroupThread: Boolean): Flow<PagingData<MessageEntity>> =
        Pager(
            config = PagingConfig(
                pageSize = 40,
                prefetchDistance = 10,
                enablePlaceholders = false,
                initialLoadSize = 60  // İlk açılışta biraz daha fazla yükle
            ),
            pagingSourceFactory = {
                if (isGroupThread) messageDao.getGroupMessagesForChatPaged(chatId)
                else messageDao.getDmMessagesForChatPaged(chatId)
            }
        ).flow

    override suspend fun getUnreadMessages(
        chatId: String,
        isGroupThread: Boolean,
        myShadeId: String?,
    ): List<MessageEntity> =
        if (isGroupThread) {
            val shade = myShadeId ?: return emptyList()
            messageDao.getGroupUnreadMessages(chatId, shade)
        } else {
            messageDao.getDmUnreadMessages(chatId)
        }

    override suspend fun updateMessageStatus(messageId: String, status: MessageStatus) =
        messageDao.updateMessageStatus(messageId, status)

    override suspend fun sendWebsocketMessage(message: WebSocketMessage): Boolean =
        webSocketManager.sendMessage(message)

    override fun observeIncomingMessages(): Flow<WebSocketMessage> =
        webSocketManager.observeMessages()

    override suspend fun updateImagePath(messageId: String, path: String) {
        messageDao.updateImagePath(messageId, path)
    }

    override suspend fun getMessageStatus(messageId: String): MessageStatus? =
        messageDao.getMessageStatus(messageId)

    override suspend fun updateMessageStatusIfForward(messageId: String, newStatus: MessageStatus) {
        val currentStatus = messageDao.getMessageStatus(messageId) ?: return
        val effectiveCurrent = if (currentStatus == MessageStatus.FAILED) MessageStatus.PENDING else currentStatus
        if (newStatus.ordinal > effectiveCurrent.ordinal) {
            messageDao.updateMessageStatus(messageId, newStatus)
        }
    }

    override suspend fun deleteMessage(message: MessageEntity) = messageDao.deleteMessage(message)
    override suspend fun deleteAllGroupMessages(groupId: String) = messageDao.deleteAllGroupMessages(groupId)

    override fun searchMessages(chatId: String, query: String, isGroupThread: Boolean): Flow<List<MessageEntity>> =
        if (isGroupThread) messageDao.searchGroupMessages(chatId, query)
        else messageDao.searchDmMessages(chatId, query)

    override suspend fun markAsDeleted(messageId: String) = messageDao.markAsDeleted(messageId)

    override suspend fun updateMessageContent(messageId: String, content: String) =
        messageDao.updateContent(messageId, content)

    override suspend fun countMediaMessages(chatId: String, isGroupThread: Boolean): Int =
        if (isGroupThread) messageDao.countGroupMediaMessages(chatId)
        else messageDao.countDmMediaMessages(chatId)

    override suspend fun updateAudioPath(messageId: String, path: String, durationMs: Long) =
        messageDao.updateAudioPath(messageId, path, durationMs)

    override suspend fun updateFilePath(messageId: String, path: String) =
        messageDao.updateFilePath(messageId, path)

    override suspend fun getMediaMessages(chatId: String, isGroupThread: Boolean): List<MessageEntity> =
        if (isGroupThread) messageDao.getGroupMediaMessages(chatId)
        else messageDao.getDmMediaMessages(chatId)
}
