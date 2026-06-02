package com.shade.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val chatId: String,
    val lastMessage: String?,
    val lastMessageTimestamp: Long,
    val unreadCount: Int = 0,
    /** true when this row represents a group chat */
    val isGroup: Boolean = false,
    /** Display name for group chats; null for 1:1 chats */
    val groupName: String? = null,
)