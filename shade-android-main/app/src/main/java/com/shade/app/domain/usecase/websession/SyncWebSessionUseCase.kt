package com.shade.app.domain.usecase.websession

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.shade.app.crypto.WebPairingCryptoManager
import com.shade.app.data.local.entities.ChatEntity
import com.shade.app.data.local.dao.ChatDao
import com.shade.app.data.local.dao.GroupDao
import com.shade.app.data.local.dao.MessageDao
import com.shade.app.data.remote.dto.GroupMemberResponse
import com.shade.app.data.remote.dto.GroupResponse
import com.shade.app.data.remote.websocket.WebSyncSocketManager
import com.shade.app.security.KeyVaultManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Web ile konuşma geçmişini WS üzerinden senkronize eder.
 * Mesajlar JSON **text** frame olarak gönderilir (`groups_snapshot` isteğe bağlı),
 * ardından `batch`(ler), son olarak `sync_complete`.
 *
 * Web kontratı: grup mesajlarında `chat_id` = `group:<uuid>`; DM’de karşı Shade ID.
 */
class SyncWebSessionUseCase @Inject constructor(
    private val socketManager: WebSyncSocketManager,
    private val chatDao: ChatDao,
    private val groupDao: GroupDao,
    private val messageDao: MessageDao,
    private val keyVault: KeyVaultManager,
    private val pairingCrypto: WebPairingCryptoManager
) {
    private val gson = Gson()

    suspend operator fun invoke(): Result<Int> =
        try {
            Result.success(withContext(Dispatchers.IO) { syncBlocking() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

    private suspend fun syncBlocking(): Int {
        if (!pairingCrypto.isSessionActive()) {
            error("Pairing session not active (transfer key missing)")
        }
        val myShadeId = keyVault.getShadeId() ?: error("shade_id missing")

        Log.d(TAG, "Sync started myShadeId=$myShadeId")

        val chats = chatDao.getAllChats().first()
        Log.d(TAG, "Found ${chats.size} chats")

        val cachedGroupsWire = loadCachedGroupsWire()
        if (cachedGroupsWire.isNotEmpty()) {
            val snapshotJson = gson.toJson(
                SyncGroupsSnapshot(type = TYPE_GROUPS_SNAPSHOT, groups = cachedGroupsWire)
            )
            val snapOk = socketManager.sendText(snapshotJson)
            Log.d(TAG, "groups_snapshot sent count=${cachedGroupsWire.size} ok=$snapOk chars=${snapshotJson.length}")
            if (!snapOk) error("WS send failed for groups_snapshot")
        }

        var total = 0

        for (chat in chats) {
            val wireChatId = wireChatId(chat)
            val messages = if (chat.isGroup) {
                messageDao.getGroupMessagesForChat(chat.chatId).first()
            } else {
                messageDao.getDmMessagesForChat(chat.chatId).first()
            }
            if (messages.isEmpty()) continue

            val items = messages.map { msg ->
                val blob = pairingCrypto.encryptWithTransferKey(
                    msg.content.toByteArray(Charsets.UTF_8)
                )
                SyncMessage(
                    messageId = msg.messageId,
                    chatId = wireChatId,
                    senderShadeId = msg.senderId,
                    ciphertext = blob.ciphertextHex,
                    nonce = blob.nonceHex,
                    timestamp = msg.timestamp,
                    msgType = msg.messageType.name,
                    status = msg.status.name
                )
            }

            val batch = SyncBatch(type = TYPE_BATCH, messages = items)
            val payload = gson.toJson(batch)
            val ok = socketManager.sendText(payload)
            Log.d(
                TAG,
                "Batch sent: chat=$wireChatId  count=${items.size}  chars=${payload.length}  ok=$ok"
            )
            if (!ok) error("WS send failed for chat=$wireChatId")
            total += items.size
        }

        val completeJson = gson.toJson(SyncComplete(type = TYPE_SYNC_COMPLETE))
        val completeOk = socketManager.sendText(completeJson)
        Log.d(TAG, "sync_complete sent  ok=$completeOk  totalMessages=$total")
        if (!completeOk) error("WS send failed for sync_complete")

        return total
    }

    /**
     * REST `GET /api/v1/groups` ile aynı JSON şekli; yerel `groups` / `group_members` önbelleğinden.
     */
    private suspend fun loadCachedGroupsWire(): List<GroupResponse> {
        val rows = groupDao.observeAllGroups().first()
        return rows.map { g ->
            val members = groupDao.getMembers(g.groupId).map { m ->
                GroupMemberResponse(
                    userId = m.userId,
                    shadeId = m.shadeId,
                    role = if (m.role == "owner") "owner" else "member",
                )
            }
            GroupResponse(
                groupId = g.groupId,
                name = g.name,
                ownerId = g.ownerId,
                avatarUrl = g.avatarUrl,
                members = members,
                createdAt = g.createdAt,
            )
        }
    }

    private fun wireChatId(chat: ChatEntity): String =
        if (chat.isGroup) "${GROUP_CHAT_ID_PREFIX}${chat.chatId}" else chat.chatId

    private data class SyncGroupsSnapshot(
        val type: String,
        val groups: List<GroupResponse>,
    )

    private data class SyncBatch(
        val type: String,
        val messages: List<SyncMessage>
    )

    private data class SyncMessage(
        @SerializedName("message_id") val messageId: String,
        @SerializedName("chat_id") val chatId: String,
        @SerializedName("sender_shade_id") val senderShadeId: String,
        @SerializedName("ciphertext") val ciphertext: String,
        @SerializedName("nonce") val nonce: String,
        @SerializedName("timestamp") val timestamp: Long,
        @SerializedName("msg_type") val msgType: String,
        @SerializedName("status") val status: String
    )

    private data class SyncComplete(val type: String)

    private companion object {
        const val TAG = "SyncWebSession"
        const val TYPE_GROUPS_SNAPSHOT = "groups_snapshot"
        const val TYPE_BATCH = "batch"
        const val TYPE_SYNC_COMPLETE = "sync_complete"
        const val GROUP_CHAT_ID_PREFIX = "group:"
    }
}
