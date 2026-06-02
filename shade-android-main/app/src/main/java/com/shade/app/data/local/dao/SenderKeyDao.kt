package com.shade.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shade.app.data.local.entities.OwnSenderKeyEntity
import com.shade.app.data.local.entities.PeerSenderKeyEntity
import com.shade.app.data.local.entities.SkdmDispatchedEntity

@Dao
interface SenderKeyDao {

    // ── Own sender key ────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOwn(key: OwnSenderKeyEntity)

    @Query("SELECT * FROM own_sender_keys WHERE groupId = :groupId LIMIT 1")
    suspend fun getOwn(groupId: String): OwnSenderKeyEntity?

    @Query("UPDATE own_sender_keys SET chainKeyHex = :chainKeyHex, chainIndex = :chainIndex WHERE groupId = :groupId")
    suspend fun advanceOwn(groupId: String, chainKeyHex: String, chainIndex: Long)

    @Query("DELETE FROM own_sender_keys WHERE groupId = :groupId")
    suspend fun deleteOwn(groupId: String)

    // ── Peer sender keys ──────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeer(key: PeerSenderKeyEntity)

    @Query(
        """
        SELECT * FROM peer_sender_keys
        WHERE groupId = :groupId
          AND peerUserId = :peerUserId
          AND peerDeviceId = :peerDeviceId
          AND keyId = :keyId
        LIMIT 1
        """
    )
    suspend fun getPeer(
        groupId: String,
        peerUserId: String,
        peerDeviceId: String,
        keyId: Long,
    ): PeerSenderKeyEntity?

    @Query(
        """
        UPDATE peer_sender_keys
        SET chainKeyHex = :chainKeyHex, chainIndex = :chainIndex, updatedAt = :updatedAt
        WHERE groupId = :groupId
          AND peerUserId = :peerUserId
          AND peerDeviceId = :peerDeviceId
          AND keyId = :keyId
        """
    )
    suspend fun advancePeer(
        groupId: String,
        peerUserId: String,
        peerDeviceId: String,
        keyId: Long,
        chainKeyHex: String,
        chainIndex: Long,
        updatedAt: Long,
    )

    @Query("DELETE FROM peer_sender_keys WHERE groupId = :groupId")
    suspend fun clearPeersForGroup(groupId: String)

    @Query("DELETE FROM peer_sender_keys WHERE groupId = :groupId AND peerUserId = :peerUserId")
    suspend fun clearPeersForUser(groupId: String, peerUserId: String)

    // ── SKDM dispatch bookkeeping ────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markSkdmDispatched(record: SkdmDispatchedEntity)

    @Query(
        """
        SELECT COUNT(*) FROM skdm_dispatched
        WHERE groupId = :groupId
          AND peerUserId = :peerUserId
          AND peerDeviceId = :peerDeviceId
          AND ownKeyId = :ownKeyId
        """
    )
    suspend fun isSkdmDispatched(
        groupId: String,
        peerUserId: String,
        peerDeviceId: String,
        ownKeyId: Long,
    ): Int

    @Query("DELETE FROM skdm_dispatched WHERE groupId = :groupId")
    suspend fun clearDispatchedForGroup(groupId: String)

    @Query("DELETE FROM skdm_dispatched WHERE groupId = :groupId AND ownKeyId < :keepKeyId")
    suspend fun purgeStaleDispatched(groupId: String, keepKeyId: Long)
}
