package com.shade.app.data.repository

import com.shade.app.data.local.dao.SenderKeyDao
import com.shade.app.data.local.entities.OwnSenderKeyEntity
import com.shade.app.data.local.entities.PeerSenderKeyEntity
import com.shade.app.data.local.entities.SkdmDispatchedEntity
import com.shade.app.domain.repository.SenderKeyRepository
import javax.inject.Inject

class SenderKeyRepositoryImpl @Inject constructor(
    private val dao: SenderKeyDao,
) : SenderKeyRepository {

    override suspend fun getOwn(groupId: String): OwnSenderKeyEntity? = dao.getOwn(groupId)

    override suspend fun saveOwn(key: OwnSenderKeyEntity) = dao.upsertOwn(key)

    override suspend fun advanceOwn(groupId: String, chainKeyHex: String, chainIndex: Long) =
        dao.advanceOwn(groupId, chainKeyHex, chainIndex)

    override suspend fun deleteOwn(groupId: String) = dao.deleteOwn(groupId)

    override suspend fun getPeer(
        groupId: String,
        peerUserId: String,
        peerDeviceId: String,
        keyId: Long,
    ): PeerSenderKeyEntity? = dao.getPeer(groupId, peerUserId, peerDeviceId, keyId)

    override suspend fun savePeer(key: PeerSenderKeyEntity) = dao.upsertPeer(key)

    override suspend fun advancePeer(
        groupId: String,
        peerUserId: String,
        peerDeviceId: String,
        keyId: Long,
        chainKeyHex: String,
        chainIndex: Long,
    ) = dao.advancePeer(
        groupId = groupId,
        peerUserId = peerUserId,
        peerDeviceId = peerDeviceId,
        keyId = keyId,
        chainKeyHex = chainKeyHex,
        chainIndex = chainIndex,
        updatedAt = System.currentTimeMillis(),
    )

    override suspend fun clearPeersForGroup(groupId: String) = dao.clearPeersForGroup(groupId)

    override suspend fun clearPeersForUser(groupId: String, peerUserId: String) =
        dao.clearPeersForUser(groupId, peerUserId)

    override suspend fun isSkdmDispatched(
        groupId: String,
        peerUserId: String,
        peerDeviceId: String,
        ownKeyId: Long,
    ): Boolean = dao.isSkdmDispatched(groupId, peerUserId, peerDeviceId, ownKeyId) > 0

    override suspend fun markSkdmDispatched(
        groupId: String,
        peerUserId: String,
        peerDeviceId: String,
        ownKeyId: Long,
    ) = dao.markSkdmDispatched(
        SkdmDispatchedEntity(
            groupId = groupId,
            peerUserId = peerUserId,
            peerDeviceId = peerDeviceId,
            ownKeyId = ownKeyId,
            dispatchedAt = System.currentTimeMillis(),
        )
    )

    override suspend fun purgeStaleDispatched(groupId: String, keepKeyId: Long) =
        dao.purgeStaleDispatched(groupId, keepKeyId)
}
