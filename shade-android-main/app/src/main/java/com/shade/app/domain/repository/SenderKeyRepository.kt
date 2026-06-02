package com.shade.app.domain.repository

import com.shade.app.data.local.entities.OwnSenderKeyEntity
import com.shade.app.data.local.entities.PeerSenderKeyEntity

/**
 * Persistence wrapper around the Sender Keys ratchet state.
 *
 * Use cases call into this repo so the crypto layer can stay pure (no Room).
 */
interface SenderKeyRepository {

    suspend fun getOwn(groupId: String): OwnSenderKeyEntity?
    suspend fun saveOwn(key: OwnSenderKeyEntity)
    suspend fun advanceOwn(groupId: String, chainKeyHex: String, chainIndex: Long)
    suspend fun deleteOwn(groupId: String)

    suspend fun getPeer(
        groupId: String,
        peerUserId: String,
        peerDeviceId: String,
        keyId: Long,
    ): PeerSenderKeyEntity?

    suspend fun savePeer(key: PeerSenderKeyEntity)

    suspend fun advancePeer(
        groupId: String,
        peerUserId: String,
        peerDeviceId: String,
        keyId: Long,
        chainKeyHex: String,
        chainIndex: Long,
    )

    suspend fun clearPeersForGroup(groupId: String)
    suspend fun clearPeersForUser(groupId: String, peerUserId: String)

    /** Has the current OwnSenderKey already been shipped to this peer device? */
    suspend fun isSkdmDispatched(
        groupId: String,
        peerUserId: String,
        peerDeviceId: String,
        ownKeyId: Long,
    ): Boolean

    suspend fun markSkdmDispatched(
        groupId: String,
        peerUserId: String,
        peerDeviceId: String,
        ownKeyId: Long,
    )

    /** Called on rotation — old dispatch records are no longer useful. */
    suspend fun purgeStaleDispatched(groupId: String, keepKeyId: Long)
}
