package com.shade.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Our outgoing Sender Key for a group. Stored once per group; rotated on
 * REMOVED / LEFT events for any subject ≠ self.
 *
 * Hex-encoded byte fields (chainKey, signingPublicKey, signingPrivateKey) keep
 * the DB schema small and avoid byte[] type-converter clutter.
 */
@Entity(tableName = "own_sender_keys")
data class OwnSenderKeyEntity(
    @PrimaryKey
    val groupId: String,
    val keyId: Long,              // proto uint32, widened for Kotlin Long ergonomics
    /** 32-byte HKDF root for the ratchet, hex-encoded. */
    val chainKeyHex: String,
    val chainIndex: Long,         // proto uint64
    val signingPublicKeyHex: String,  // Ed25519 public (32 bytes)
    val signingPrivateKeyHex: String, // Ed25519 private seed (32 bytes)
    val createdAt: Long,
)

/**
 * Peer's Sender Key for a group, per (peer_user_id, peer_device_id).
 * Multiple [keyId] versions can briefly coexist while members re-key.
 */
@Entity(
    tableName = "peer_sender_keys",
    primaryKeys = ["groupId", "peerUserId", "peerDeviceId", "keyId"],
    indices = [
        Index("groupId"),
        Index(value = ["groupId", "peerUserId", "peerDeviceId"]),
    ],
)
data class PeerSenderKeyEntity(
    val groupId: String,
    val peerUserId: String,
    val peerDeviceId: String,
    val keyId: Long,
    val chainKeyHex: String,
    val chainIndex: Long,
    val signingPublicKeyHex: String,
    val updatedAt: Long,
)

/**
 * Bookkeeping: have we already shipped our current SKDM to this peer device?
 * Composite PK = (groupId, peerUserId, peerDeviceId, ownKeyId). When a peer
 * appears (JOINED or first message exchange) we send our SKDM and insert a
 * row here so we don't spam them on every reconnect.
 */
@Entity(
    tableName = "skdm_dispatched",
    primaryKeys = ["groupId", "peerUserId", "peerDeviceId", "ownKeyId"],
    indices = [Index("groupId")],
)
data class SkdmDispatchedEntity(
    val groupId: String,
    val peerUserId: String,
    val peerDeviceId: String,
    val ownKeyId: Long,
    val dispatchedAt: Long,
)
