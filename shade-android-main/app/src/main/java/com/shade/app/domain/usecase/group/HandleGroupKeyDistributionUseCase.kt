package com.shade.app.domain.usecase.group

import android.util.Log
import com.shade.app.crypto.MessageCryptoManager
import com.shade.app.data.local.entities.ContactEntity
import com.shade.app.data.local.entities.PeerSenderKeyEntity
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.SenderKeyRepository
import com.shade.app.domain.usecase.message.ReceiveGroupMessageUseCase
import com.shade.app.proto.GroupKeyDistribution
import com.shade.app.proto.SenderKeyDistribution
import com.shade.app.security.KeyVaultManager
import org.bouncycastle.util.encoders.Hex
import javax.inject.Inject

/**
 * Decodes an incoming [GroupKeyDistribution] frame and installs the resulting
 * [PeerSenderKeyEntity]. Any payloads that were buffered while waiting for
 * this SKDM are immediately replayed.
 *
 * Outline:
 *   1. Resolve the sender's X25519 public key (from local contacts; the
 *      caller is expected to also be a known user via lookup).
 *   2. ECDH + HKDF → AEAD key (same primitive as 1-to-1 messaging).
 *   3. ChaCha20-Poly1305 decrypt → [SenderKeyDistribution] proto.
 *   4. Validate that the embedded `sender_user_id` / `sender_device_id`
 *      match the envelope (server already enforces this server-side, but we
 *      defensively re-check).
 *   5. Persist as a peer sender key.
 *   6. Drain pending payloads buffered against `(group, sender, key_id)`.
 */
class HandleGroupKeyDistributionUseCase @Inject constructor(
    private val cryptoManager: MessageCryptoManager,
    private val contactRepository: ContactRepository,
    private val senderKeyRepository: SenderKeyRepository,
    private val pendingPayloads: PendingGroupPayloads,
    private val receiveGroupMessage: ReceiveGroupMessageUseCase,
    private val ensureOwnKey: EnsureOwnSenderKeyUseCase,
    private val distributeSenderKey: DistributeSenderKeyUseCase,
    private val keyVaultManager: KeyVaultManager,
) {
    suspend operator fun invoke(gkd: GroupKeyDistribution) {
        try {
            val myUserId = keyVaultManager.getUserId() ?: return
            val myDeviceId = keyVaultManager.getDeviceId() ?: return
            val myX25519Priv = keyVaultManager.getX25519PrivateKey() ?: return

            // The server routes by recipient_user_id; we'll see SKDMs targeted
            // at our user. Filter by device_id when set so peers on other
            // devices don't install someone else's chain.
            if (gkd.recipientUserId != myUserId) {
                Log.d(TAG, "SKDM not for us (recipient=${gkd.recipientUserId}), ignoring")
                return
            }
            if (gkd.recipientDeviceId.isNotEmpty() && gkd.recipientDeviceId != myDeviceId) {
                Log.d(TAG, "SKDM targeted at device=${gkd.recipientDeviceId}, ours=$myDeviceId — ignoring")
                return
            }

            val senderContact = contactRepository.getOrFetchContactByUserId(gkd.senderUserId)
                ?: run {
                    Log.w(TAG, "Cannot decrypt SKDM: unknown sender ${gkd.senderUserId}")
                    return
                }

            val plaintextSkdmBytes = decryptSkdmOrRetry(
                gkd = gkd,
                cachedSender = senderContact,
                myPrivHex = myX25519Priv,
            )
            val skdm = SenderKeyDistribution.parseFrom(plaintextSkdmBytes)

            // Sanity-check the wrapped fields match the envelope.
            if (skdm.senderUserId != gkd.senderUserId || skdm.senderDeviceId != gkd.senderDeviceId) {
                Log.w(
                    TAG,
                    "SKDM identity mismatch: envelope=(${gkd.senderUserId}/${gkd.senderDeviceId}) " +
                            "inner=(${skdm.senderUserId}/${skdm.senderDeviceId}) — dropping"
                )
                return
            }
            if (skdm.groupId != gkd.groupId) {
                Log.w(
                    TAG,
                    "SKDM group mismatch: envelope=${gkd.groupId} inner=${skdm.groupId} — dropping"
                )
                return
            }

            // Detect first-time peer install BEFORE savePeer overwrites it. New
            // peers (e.g. a freshly-paired web device) require reciprocation:
            // our dispatch ledger is user-level, so our own SKDM was never
            // re-sent to that device, and the peer can't decrypt our payloads
            // without it. See API_CONTRACT.md → "Late Join / SKDM Recovery
            // (Implicit MVP)".
            val isNewPeer = senderKeyRepository.getPeer(
                groupId = skdm.groupId,
                peerUserId = skdm.senderUserId,
                peerDeviceId = skdm.senderDeviceId,
                keyId = skdm.keyId.toLong(),
            ) == null

            val peerKey = PeerSenderKeyEntity(
                groupId = skdm.groupId,
                peerUserId = skdm.senderUserId,
                peerDeviceId = skdm.senderDeviceId,
                keyId = skdm.keyId.toLong(),
                chainKeyHex = Hex.toHexString(skdm.chainKey.toByteArray()),
                chainIndex = skdm.chainIndex,
                signingPublicKeyHex = Hex.toHexString(skdm.signingPublicKey.toByteArray()),
                updatedAt = System.currentTimeMillis(),
            )
            senderKeyRepository.savePeer(peerKey)
            Log.i(
                TAG,
                "Installed peer sender key: group=${skdm.groupId} peer=${skdm.senderUserId} " +
                        "device=${skdm.senderDeviceId} key=${skdm.keyId} new=$isNewPeer"
            )

            // Drain any payloads queued for this exact tuple.
            val buffered = pendingPayloads.drain(
                groupId = skdm.groupId,
                senderUserId = skdm.senderUserId,
                senderDeviceId = skdm.senderDeviceId,
                senderKeyId = skdm.keyId.toLong(),
            )
            if (buffered.isNotEmpty()) {
                Log.i(TAG, "Replaying ${buffered.size} buffered payload(s) for ${skdm.senderUserId}")
                for (p in buffered.sortedBy { it.chainIndex }) {
                    receiveGroupMessage.replayWithPeerKey(p, peerKey)
                }
            }

            // Reciprocate: send our own SKDM back to the sender so *their*
            // device can decrypt our future payloads. Skip for our own user
            // (linked-device echoes are handled by distributeToLinkedDevices on
            // every send). `isNewPeer` guards the loop — once both sides have
            // each other's key, neither reciprocates again.
            if (isNewPeer && skdm.senderUserId != myUserId) {
                runCatching {
                    val ownKey = ensureOwnKey(skdm.groupId)
                    val sent = distributeSenderKey(
                        ownKey,
                        force = true,
                        onlyUserId = skdm.senderUserId,
                    )
                    Log.i(
                        TAG,
                        "Reciprocated SKDM to ${skdm.senderUserId} after new-peer install " +
                                "(group=${skdm.groupId} sent=$sent)"
                    )
                }.onFailure {
                    Log.w(TAG, "Reciprocate SKDM failed: ${it.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process SKDM: ${e.message}", e)
        }
    }

    /** Cache may hold a stale `encryption_public_key` vs `GET /keys/:id`; retry once. */
    private suspend fun decryptSkdmOrRetry(
        gkd: GroupKeyDistribution,
        cachedSender: ContactEntity,
        myPrivHex: String,
    ): ByteArray {
        fun decryptWith(senderPubHex: String): ByteArray {
            val sharedSecret = cryptoManager.generateSharedSecret(
                myPrivHex,
                senderPubHex,
            )
            val derivedKey = cryptoManager.deriveConversationKey(sharedSecret, 1)
            return cryptoManager.decryptBytes(
                cipherTextBytes = gkd.encryptedSkdm.toByteArray(),
                nonceBytes = gkd.nonce.toByteArray(),
                derivedKeyHex = derivedKey,
            )
        }

        return try {
            decryptWith(cachedSender.encryptionPublicKey)
        } catch (first: Exception) {
            Log.d(TAG, "SKDM decrypt retry (${gkd.senderUserId}) after ${first.message}")
            val refreshed = contactRepository.getOrFetchContactByUserId(
                gkd.senderUserId,
                bypassCache = true,
            ) ?: throw first
            try {
                decryptWith(refreshed.encryptionPublicKey)
            } catch (second: Exception) {
                second.addSuppressed(first)
                throw second
            }
        }
    }

    private companion object {
        private const val TAG = "HandleSKDM"
    }
}
