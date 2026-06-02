package com.shade.app.domain.usecase.group

import android.util.Log
import com.google.protobuf.ByteString
import com.shade.app.crypto.MessageCryptoManager
import com.shade.app.data.local.entities.ContactEntity
import com.shade.app.data.local.entities.OwnSenderKeyEntity
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.GroupRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.domain.repository.SenderKeyRepository
import com.shade.app.proto.GroupKeyDistribution
import com.shade.app.proto.SenderKeyDistribution
import com.shade.app.proto.WebSocketMessage
import com.shade.app.security.KeyVaultManager
import org.bouncycastle.util.encoders.Hex
import javax.inject.Inject

/**
 * Builds and sends a Sender Key Distribution Message to a single peer device.
 *
 * Crypto envelope:
 *   1. Encode [SenderKeyDistribution] (the plaintext SKDM) from the caller's
 *      own sender-key state.
 *   2. Derive a pairwise X25519 key using `MessageCryptoManager` — the same
 *      primitive used for 1-to-1 messaging — and encrypt the encoded proto
 *      with ChaCha20-Poly1305 (no AAD; the wire blob is opaque).
 *   3. Wrap in a [GroupKeyDistribution] proto and ship via the WebSocket
 *      pairwise route (`shade.user`, routing_key = recipient_user_id).
 *
 * The caller is responsible for marking the dispatch in [SenderKeyRepository]
 * so we don't re-ship the same SKDM on every reconnect.
 */
class SendSenderKeyDistributionUseCase @Inject constructor(
    private val cryptoManager: MessageCryptoManager,
    private val contactRepository: ContactRepository,
    private val groupRepository: GroupRepository,
    private val messageRepository: MessageRepository,
    private val senderKeyRepository: SenderKeyRepository,
    private val keyVaultManager: KeyVaultManager,
) {
    /**
     * @param recipientDeviceId Optional. The server routes by
     *  [recipientUserId] only, but the proto carries [recipientDeviceId] so
     *  multi-device recipients can pick the right SKDM. Pass an empty string
     *  when the caller does not yet know which device of the user is targeted.
     */
    suspend operator fun invoke(
        ownKey: OwnSenderKeyEntity,
        recipientUserId: String,
        recipientDeviceId: String = "",
    ): Result<Unit> = runCatching {
        val myUserId = keyVaultManager.getUserId()
            ?: error("Missing user_id — not signed in?")
        val myDeviceId = keyVaultManager.getDeviceId()
            ?: error("Missing device_id — re-login required for group messaging")
        val myX25519Priv = keyVaultManager.getX25519PrivateKey()
            ?: error("Missing X25519 private key")

        val recipient = resolveRecipientContact(ownKey.groupId, recipientUserId)

        val skdm = SenderKeyDistribution.newBuilder()
            .setGroupId(ownKey.groupId)
            .setSenderUserId(myUserId)
            .setSenderDeviceId(myDeviceId)
            .setKeyId(ownKey.keyId.toInt())
            .setChainKey(ByteString.copyFrom(Hex.decode(ownKey.chainKeyHex)))
            .setSigningPublicKey(ByteString.copyFrom(Hex.decode(ownKey.signingPublicKeyHex)))
            .setChainIndex(ownKey.chainIndex)
            .build()
            .toByteArray()

        // Pairwise envelope: encrypt the proto blob with X25519 → HKDF → ChaCha20.
        val sharedSecret = cryptoManager.generateSharedSecret(
            myX25519Priv,
            recipient.encryptionPublicKey,
        )
        val derivedKey = cryptoManager.deriveConversationKey(sharedSecret, 1)
        val (cipherBytes, nonceBytes) = cryptoManager.encryptBytes(skdm, derivedKey)

        val wsMsg = WebSocketMessage.newBuilder()
            .setGkd(
                GroupKeyDistribution.newBuilder()
                    .setGroupId(ownKey.groupId)
                    .setSenderUserId(myUserId)
                    .setSenderDeviceId(myDeviceId)
                    .setRecipientUserId(recipientUserId)
                    .setRecipientDeviceId(recipientDeviceId)
                    .setEncryptedSkdm(ByteString.copyFrom(cipherBytes))
                    .setNonce(ByteString.copyFrom(nonceBytes))
                    .build()
            )
            .build()

        if (!messageRepository.sendWebsocketMessage(wsMsg)) {
            error("WebSocket send failed")
        }

        senderKeyRepository.markSkdmDispatched(
            groupId = ownKey.groupId,
            peerUserId = recipientUserId,
            peerDeviceId = recipientDeviceId.ifEmpty { recipientUserId },
            ownKeyId = ownKey.keyId,
        )
        Log.d(TAG, "SKDM dispatched: group=${ownKey.groupId} peer=$recipientUserId key=${ownKey.keyId}")
        Unit
    }.onFailure { Log.e(TAG, "SKDM dispatch failed: ${it.message}", it) }

    /**
     * SKDM için X25519 karşı anahtarı bulur: önce `GET keys/:user_id`, olmazsa
     * gruptaki `shade_id` ile `GET user/lookup/:shadeId`, en son yerel önbellek.
     */
    private suspend fun resolveRecipientContact(groupId: String, recipientUserId: String): ContactEntity {
        contactRepository.getOrFetchContactByUserId(recipientUserId, bypassCache = true)?.let { return it }

        val shadeId = resolveRecipientShadeId(groupId, recipientUserId)
        if (!shadeId.isNullOrBlank()) {
            contactRepository.getOrFetchContact(shadeId)?.takeIf { it.encryptionPublicKey.isNotBlank() }?.let {
                return it
            }
        }

        contactRepository.getContactByUserId(recipientUserId)?.takeIf { it.encryptionPublicKey.isNotBlank() }?.let {
            return it
        }

        error("Cannot resolve recipient public key for $recipientUserId")
    }

    private suspend fun resolveRecipientShadeId(groupId: String, recipientUserId: String): String? {
        groupRepository.getCachedMembers(groupId)
            .firstOrNull { it.userId == recipientUserId }
            ?.shadeId
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return groupRepository.getGroup(groupId).getOrNull()
            ?.members
            ?.firstOrNull { it.userId == recipientUserId }
            ?.shadeId
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        private const val TAG = "SendSKDM"
    }
}
