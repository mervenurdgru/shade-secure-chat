package com.shade.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shade.app.data.local.entities.ChatEntity
import com.shade.app.data.local.model.ChatWithContact
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateChat(chat: ChatEntity)

    @Query("SELECT * FROM chats ORDER BY lastMessageTimestamp DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Transaction
    @Query("SELECT * FROM chats ORDER BY lastMessageTimestamp DESC")
    fun getAllChatsWithContact(): Flow<List<ChatWithContact>>

    @Transaction
    @Query("SELECT * FROM chats WHERE chatId = :chatId LIMIT 1")
    fun observeChatWithContact(chatId: String): Flow<ChatWithContact?>

    @Query("UPDATE chats SET lastMessage = :lastMessage, lastMessageTimestamp = :timestamp WHERE chatId = :chatId")
    suspend fun updateLastMessage(chatId: String, lastMessage: String, timestamp: Long): Int

    @Query("UPDATE chats SET unreadCount = unreadCount + 1, lastMessage = :lastMessage, lastMessageTimestamp = :timestamp WHERE chatId = :chatId")
    suspend fun incrementUnreadCount(chatId: String, lastMessage: String, timestamp: Long): Int

    @Query("UPDATE chats SET unreadCount = 0, lastMessage = :lastMessage, lastMessageTimestamp = :timestamp WHERE chatId = :chatId")
    suspend fun updateLastMessageNoIncrement(chatId: String, lastMessage: String, timestamp: Long): Int

    @Query("UPDATE chats SET unreadCount = 0 WHERE chatId = :chatId")
    suspend fun resetUnreadCount(chatId: String)

    @Query(
        """UPDATE chats SET isGroup = :isGroup,
            groupName = :groupName
            WHERE chatId = :chatId"""
    )
    suspend fun patchGroupHints(chatId: String, isGroup: Boolean, groupName: String?): Int

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChat(chatId: String)
}
