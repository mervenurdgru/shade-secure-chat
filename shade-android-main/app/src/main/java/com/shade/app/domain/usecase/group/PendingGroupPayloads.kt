package com.shade.app.domain.usecase.group

import com.shade.app.proto.EncryptedPayload
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory buffer for group payloads whose Sender Key has not arrived yet.
 *
 * The Receive Algorithm (`API_CONTRACT.md` → "Late Join / SKDM Recovery") says
 * that when an `EncryptedPayload` arrives for an `(group, sender_user_id,
 * sender_device_id, sender_key_id)` we don't have a SenderKey for, we should
 * buffer the payload for a short TTL (~30 s) and trigger SKDM recovery.
 *
 * This is intentionally in-memory only — buffered payloads are best-effort and
 * the server will not re-send them, so persistence wouldn't help. Buckets are
 * cleared once the matching SKDM arrives and the payload is replayed.
 */
@Singleton
class PendingGroupPayloads @Inject constructor() {

    private data class Entry(val payload: EncryptedPayload, val storedAt: Long)

    /** Key = "{groupId}|{senderUserId}|{senderDeviceId}|{senderKeyId}". */
    private val buckets = ConcurrentHashMap<String, MutableList<Entry>>()

    fun store(payload: EncryptedPayload) {
        val key = keyFor(
            payload.groupId,
            payload.senderId,
            payload.senderDeviceId,
            payload.senderKeyId.toLong(),
        )
        val list = buckets.getOrPut(key) { mutableListOf() }
        synchronized(list) {
            list.add(Entry(payload, System.currentTimeMillis()))
        }
    }

    /** Returns and removes all payloads matching the given sender-key tuple. */
    fun drain(
        groupId: String,
        senderUserId: String,
        senderDeviceId: String,
        senderKeyId: Long,
    ): List<EncryptedPayload> {
        val key = keyFor(groupId, senderUserId, senderDeviceId, senderKeyId)
        val list = buckets.remove(key) ?: return emptyList()
        return synchronized(list) { list.map { it.payload } }
    }

    /** Best-effort eviction of buckets older than [maxAgeMs]. Cheap to call. */
    fun evictExpired(maxAgeMs: Long = DEFAULT_TTL_MS) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        buckets.entries.removeAll { (_, list) ->
            synchronized(list) {
                list.removeAll { it.storedAt < cutoff }
                list.isEmpty()
            }
        }
    }

    private fun keyFor(
        groupId: String,
        senderUserId: String,
        senderDeviceId: String,
        senderKeyId: Long,
    ): String = "$groupId|$senderUserId|$senderDeviceId|$senderKeyId"

    private companion object {
        private const val DEFAULT_TTL_MS = 30_000L
    }
}
