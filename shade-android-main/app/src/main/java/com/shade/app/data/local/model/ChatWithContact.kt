package com.shade.app.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.shade.app.data.local.entities.ChatEntity
import com.shade.app.data.local.entities.ContactEntity

data class ChatWithContact(
    @Embedded val chat: ChatEntity,
    @Relation(
        parentColumn = "chatId",
        entityColumn = "shadeId"
    ) val contact: ContactEntity?
) {
    /**
     * Sohbet listesinde gösterilen isim.
     * Kaydedilmemişse shadeId gösterilir (profileName atlanır).
     */
    val displayName: String
        get() = if (chat.isGroup) {
            chat.groupName ?: chat.chatId
        } else {
            contact?.savedName ?: contact?.shadeId ?: chat.chatId
        }

    /**
     * Sohbet ekranı header'ında gösterilen isim.
     * Kaydedilmemişse profileName (Tel3 gibi) gösterilir.
     */
    val headerName: String
        get() = if (chat.isGroup) {
            chat.groupName ?: chat.chatId
        } else {
            contact?.savedName ?: contact?.profileName ?: contact?.shadeId ?: chat.chatId
        }
}
