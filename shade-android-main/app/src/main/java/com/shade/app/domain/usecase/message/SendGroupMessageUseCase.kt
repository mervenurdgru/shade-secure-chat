package com.shade.app.domain.usecase.message

import android.util.Log
import com.google.protobuf.ByteString
import com.shade.app.crypto.SenderKeyCryptoManager
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.domain.repository.SenderKeyRepository
import com.shade.app.domain.usecase.group.DistributeSenderKeyUseCase
import com.shade.app.domain.usecase.group.EnsureOwnSenderKeyUseCase
import com.shade.app.proto.EncryptedPayload
import com.shade.app.proto.MessageType
import com.shade.app.proto.WebSocketMessage
import com.shade.app.security.KeyVaultManager
import org.bouncycastle.util.encoders.Hex
import java.util.UUID
import javax.inject.Inject

/**
 * Sends a text message to a group using the **Sender Keys** scheme.
 *
 * Unlike the old per-member fan-out, the message is encrypted **once** under
 * the caller's own sender-key chain. The server fans the same ciphertext out
 * to every bound member-device queue via `shade.group`.
 *
 * Steps (see `API_CONTRACT.md` → "Send Algorithm"):
 *  1. Make sure our OwnSenderKey exists.
 *  2. Ship the current SKDM to any peer that doesn't already have it.
 *  3. Derive msg_key + new chain_key from the current chain root.
 *  4. AEAD-encrypt the plaintext with AAD bound to (group, device, key_id, idx).
 *  5. Sign the frame with our Ed25519 signing key.
 *  6. Wrap as [EncryptedPayload] and send a single WS message.
 *  7. Advance the chain ratchet (++chain_index, chain_key ← HKDF chain).
 */
class SendGroupMessageUseCase @Inject constructor(
    private val ensureOwnKey: EnsureOwnSenderKeyUseCase,
    private val distributeSenderKey: DistributeSenderKeyUseCase,
    private val senderKeyCrypto: SenderKeyCryptoManager,
    private val senderKeyRepository: SenderKeyRepository,
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val keyVaultManager: KeyVaultManager,
) {
    suspend operator fun invoke(
        groupId: String,
        groupName: String,
        text: String,
    ): Result<Unit> = runCatching {
        val myUserId = keyVaultManager.getUserId() ?: error("Missing user_id")
        val myShadeId = keyVaultManager.getShadeId() ?: error("Missing shade_id")
        val myDeviceId = keyVaultManager.getDeviceId()
            ?: error("Missing device_id — re-login required for group messaging")

        // 1. Own sender key
        val ownKey = ensureOwnKey(groupId)

        // 2a. Linked devices (web): current chain snapshot for multi-device echo.
        distributeSenderKey.distributeToLinkedDevices(ownKey, force = true)

        // 2b. Other group members (skipped if already dispatched for this key_id).
        distributeSenderKey(ownKey, force = false)

        // 3. Derive ratchet keys
        val chainKey = Hex.decode(ownKey.chainKeyHex)
        val msgKey = senderKeyCrypto.deriveMessageKey(chainKey)
        val nextChainKey = senderKeyCrypto.deriveNextChainKey(chainKey)

        // 4. AEAD with AAD = group_id || sender_device_id || key_id || chain_index
        val aad = senderKeyCrypto.buildAad(
            groupId = groupId,
            senderDeviceId = myDeviceId,
            keyId = ownKey.keyId,
            chainIndex = ownKey.chainIndex,
        )
        val plaintext = text.toByteArray(Charsets.UTF_8)
        val ciphertext = senderKeyCrypto.aeadEncrypt(plaintext, msgKey, aad)

        // 5. Ed25519 sign
        val signature = senderKeyCrypto.signGroupPayload(
            signingPrivateKey = Hex.decode(ownKey.signingPrivateKeyHex),
            ciphertextWithTag = ciphertext.body,
            nonce = ciphertext.nonce,
            groupId = groupId,
            keyId = ownKey.keyId,
            chainIndex = ownKey.chainIndex,
        )

        val msgId = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()

        // 6. Build + send the EncryptedPayload (single fan-out via server).
        val payload = EncryptedPayload.newBuilder()
            .setMessageId(msgId)
            .setSenderId(myUserId)
            .setSenderShadeId(myShadeId)
            .setGroupId(groupId)
            .setSenderDeviceId(myDeviceId)
            .setSenderKeyId(ownKey.keyId.toInt())
            .setChainIndex(ownKey.chainIndex)
            .setCiphertext(ByteString.copyFrom(ciphertext.body))
            .setNonce(ByteString.copyFrom(ciphertext.nonce))
            .setSignature(ByteString.copyFrom(signature))
            .setTimestamp(ts)
            .setType(MessageType.TEXT)
            .build()

        val wsMsg = WebSocketMessage.newBuilder().setPayload(payload).build()
        val sent = messageRepository.sendWebsocketMessage(wsMsg)

        // 7. Advance ratchet (success or failure — we don't reuse a key index).
        senderKeyRepository.advanceOwn(
            groupId = groupId,
            chainKeyHex = Hex.toHexString(nextChainKey),
            chainIndex = ownKey.chainIndex + 1,
        )

        val entity = MessageEntity(
            messageId = msgId,
            senderId = myShadeId,
            receiverId = groupId,
            isGroupThread = true,
            content = text,
            timestamp = ts,
            messageType = com.shade.app.data.local.entities.MessageType.TEXT,
            status = if (sent) MessageStatus.SENT else MessageStatus.FAILED,
        )
        messageRepository.insertMessage(entity)
        chatRepository.updateLastMessage(
            chatId = groupId,
            lastMessage = text,
            timestamp = ts,
        )

        if (!sent) Log.w(TAG, "WebSocket send failed for group message $msgId")
    }.onFailure { Log.e(TAG, "Group send failed: ${it.message}", it) }

    private companion object {
        private const val TAG = "SendGroupMsg"
    }
}
