package com.shade.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val messageId: String,

    val senderId: String,
    val receiverId: String,

    /**
     * Grup iletilerinde `senderId` yine gönderenin shade_id'sidir; DM listesi
     * sorgusu `(senderId = X OR receiverId = X)` olduğundan grup mesajları DM'de
     * yanlışlıkla görünmesin diye ayrı filtre kullanılır.
     */
    @ColumnInfo(name = "isGroupThread", defaultValue = "0")
    val isGroupThread: Boolean = false,

    val messageType: MessageType,
    val content: String,

    val timestamp: Long,
    val status: MessageStatus,

    val thumbnailPath: String? = null,
    val imagePath: String? = null,

    // Audio message fields
    val audioPath: String? = null,
    val audioDurationMs: Long? = null,

    // File message fields
    val filePath: String? = null,
    val fileName: String? = null,
    val fileSizeBytes: Long? = null,

    val isDeleted: Boolean = false,
    val isEdited: Boolean = false,

    // Reply-to support
    val replyToId: String? = null,
    val replyToContent: String? = null
)

enum class MessageType {
    TEXT, IMAGE, AUDIO, FILE, SYSTEM
}

enum class MessageStatus {
    PENDING, SENT, DELIVERED, READ, FAILED
}
