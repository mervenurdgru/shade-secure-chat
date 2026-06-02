package com.shade.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query(
        "SELECT * FROM messages WHERE isGroupThread = 0 AND " +
            "(senderId = :chatId OR receiverId = :chatId) ORDER BY timestamp ASC, rowid ASC"
    )
    fun getDmMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query(
        "SELECT * FROM messages WHERE isGroupThread != 0 AND receiverId = :chatId " +
            "ORDER BY timestamp ASC, rowid ASC"
    )
    fun getGroupMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    /**
     * Sayfalı mesaj listesi — en yeni mesajlar önce gelir.
     * `rowid` ile aynı unix zamanına düşen iletilerde deterministik sıra.
     */
    @Query(
        "SELECT * FROM messages WHERE isGroupThread = 0 AND " +
            "(senderId = :chatId OR receiverId = :chatId) ORDER BY timestamp DESC, rowid DESC"
    )
    fun getDmMessagesForChatPaged(chatId: String): PagingSource<Int, MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE isGroupThread != 0 AND receiverId = :chatId " +
            "ORDER BY timestamp DESC, rowid DESC"
    )
    fun getGroupMessagesForChatPaged(chatId: String): PagingSource<Int, MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE isGroupThread = 0 AND senderId = :chatId AND status != 'READ'"
    )
    suspend fun getDmUnreadMessages(chatId: String): List<MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE isGroupThread != 0 AND receiverId = :groupId AND " +
            "senderId != :myShadeId AND status != 'READ'"
    )
    suspend fun getGroupUnreadMessages(groupId: String, myShadeId: String): List<MessageEntity>

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)

    @Query("UPDATE messages SET imagePath = :path WHERE messageId = :messageId")
    suspend fun updateImagePath(messageId: String, path: String)

    @Query("UPDATE messages SET audioPath = :path, audioDurationMs = :durationMs WHERE messageId = :messageId")
    suspend fun updateAudioPath(messageId: String, path: String, durationMs: Long)

    @Query("UPDATE messages SET filePath = :path WHERE messageId = :messageId")
    suspend fun updateFilePath(messageId: String, path: String)

    @Query("SELECT status FROM messages WHERE messageId = :messageId")
    suspend fun getMessageStatus(messageId: String): MessageStatus?

    @Query(
        "SELECT * FROM messages WHERE isGroupThread = 0 AND " +
            "(senderId = :chatId OR receiverId = :chatId) AND content LIKE '%' || :query || '%' " +
            "ORDER BY timestamp DESC, rowid DESC"
    )
    fun searchDmMessages(chatId: String, query: String): Flow<List<MessageEntity>>

    @Query(
        "SELECT * FROM messages WHERE isGroupThread != 0 AND receiverId = :chatId AND " +
            "content LIKE '%' || :query || '%' ORDER BY timestamp DESC, rowid DESC"
    )
    fun searchGroupMessages(chatId: String, query: String): Flow<List<MessageEntity>>

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE isGroupThread != 0 AND receiverId = :groupId")
    suspend fun deleteAllGroupMessages(groupId: String)

    @Query("UPDATE messages SET isDeleted = 1 WHERE messageId = :messageId")
    suspend fun markAsDeleted(messageId: String)

    @Query("UPDATE messages SET content = :content, isEdited = 1 WHERE messageId = :messageId")
    suspend fun updateContent(messageId: String, content: String)

    @Query(
        "SELECT * FROM messages WHERE isGroupThread != 0 AND receiverId = :chatId AND " +
            "messageType = 'IMAGE' AND isDeleted = 0 ORDER BY timestamp DESC LIMIT 30"
    )
    suspend fun getGroupMediaMessages(chatId: String): List<MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE isGroupThread = 0 AND " +
            "(senderId = :chatId OR receiverId = :chatId) AND " +
            "messageType = 'IMAGE' AND isDeleted = 0 ORDER BY timestamp DESC LIMIT 30"
    )
    suspend fun getDmMediaMessages(chatId: String): List<MessageEntity>

    @Query(
        "SELECT COUNT(*) FROM messages WHERE isGroupThread = 0 AND " +
            "(senderId = :chatId OR receiverId = :chatId) AND messageType = 'IMAGE' AND isDeleted = 0"
    )
    suspend fun countDmMediaMessages(chatId: String): Int

    @Query(
        "SELECT COUNT(*) FROM messages WHERE isGroupThread != 0 AND receiverId = :chatId AND " +
            "messageType = 'IMAGE' AND isDeleted = 0"
    )
    suspend fun countGroupMediaMessages(chatId: String): Int
}
