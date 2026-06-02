package com.shade.app.domain.usecase.message

import android.util.Log
import com.google.gson.Gson
import com.google.protobuf.ByteString
import com.shade.app.crypto.MessageCryptoManager
import com.shade.app.crypto.SenderKeyCryptoManager
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.domain.model.AudioMessageContent
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.ImageRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.domain.repository.SenderKeyRepository
import com.shade.app.domain.usecase.group.DistributeSenderKeyUseCase
import com.shade.app.domain.usecase.group.EnsureOwnSenderKeyUseCase
import com.shade.app.proto.EncryptedPayload
import com.shade.app.proto.MessageType
import com.shade.app.proto.WebSocketMessage
import com.shade.app.security.KeyVaultManager
import org.bouncycastle.util.encoders.Hex
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject

/**
 * Grup ses mesajı: gövde JSON'u sender-key ratchet ile şifrelenir; CDN'deki blob
 * ise rastgele bir simetrik anahtarla korunur (`audioKeyHex`, JSON içinde).
 */
class SendGroupAudioMessageUseCase @Inject constructor(
    private val ensureOwnKey: EnsureOwnSenderKeyUseCase,
    private val distributeSenderKey: DistributeSenderKeyUseCase,
    private val senderKeyCrypto: SenderKeyCryptoManager,
    private val senderKeyRepository: SenderKeyRepository,
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val imageRepository: ImageRepository,
    private val pairwiseCrypto: MessageCryptoManager,
    private val keyVaultManager: KeyVaultManager,
) {
    private val gson = Gson()
    private val random = SecureRandom()

    suspend operator fun invoke(
        groupId: String,
        audioFile: File,
        durationMs: Long,
    ): Result<Unit> = runCatching {
        val audioBytes = audioFile.readBytes()

        // CDN'e yüklenecek blob için rastgele anahtar
        val audioKey = ByteArray(32).also(random::nextBytes)
        val audioKeyHex = Hex.toHexString(audioKey)
        val (encryptedAudioBytes, audioNonce) = pairwiseCrypto.encryptBytes(audioBytes, audioKeyHex)

        val uploadResult = imageRepository.uploadEncryptedFile(encryptedAudioBytes, "audio.aac")
        val uploadResponse = uploadResult.getOrElse { throw it }

        val myUserId = keyVaultManager.getUserId() ?: error("Missing user_id")
        val myShadeId = keyVaultManager.getShadeId() ?: error("Missing shade_id")
        val myDeviceId = keyVaultManager.getDeviceId()
            ?: error("Missing device_id — re-login required for group messaging")

        val ownKey = ensureOwnKey(groupId)
        distributeSenderKey(ownKey, force = false)

        val chainKey = Hex.decode(ownKey.chainKeyHex)
        val msgKey = senderKeyCrypto.deriveMessageKey(chainKey)
        val nextChainKey = senderKeyCrypto.deriveNextChainKey(chainKey)

        val aad = senderKeyCrypto.buildAad(
            groupId = groupId,
            senderDeviceId = myDeviceId,
            keyId = ownKey.keyId,
            chainIndex = ownKey.chainIndex,
        )

        val msgId = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()

        val audioContent = AudioMessageContent(
            audioId = uploadResponse.imageId,
            audioNonceHex = Hex.toHexString(audioNonce),
            durationMs = durationMs,
            sizeBytes = audioBytes.size.toLong(),
            audioKeyHex = audioKeyHex,
        )
        val contentJson = gson.toJson(audioContent)
        val plaintext = contentJson.toByteArray(Charsets.UTF_8)

        val ciphertext = senderKeyCrypto.aeadEncrypt(plaintext, msgKey, aad)
        val signature = senderKeyCrypto.signGroupPayload(
            signingPrivateKey = Hex.decode(ownKey.signingPrivateKeyHex),
            ciphertextWithTag = ciphertext.body,
            nonce = ciphertext.nonce,
            groupId = groupId,
            keyId = ownKey.keyId,
            chainIndex = ownKey.chainIndex,
        )

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
            .setType(MessageType.AUDIO)
            .build()

        val sent = messageRepository.sendWebsocketMessage(
            WebSocketMessage.newBuilder().setPayload(payload).build(),
        )

        senderKeyRepository.advanceOwn(
            groupId = groupId,
            chainKeyHex = Hex.toHexString(nextChainKey),
            chainIndex = ownKey.chainIndex + 1,
        )

        // Ses dosyasını yerel kaydet
        val audioDir = File(audioFile.parentFile, "audio").also { it.mkdirs() }
        val localAudioFile = File(audioDir, "$msgId.aac")
        audioFile.copyTo(localAudioFile, overwrite = true)

        val entity = MessageEntity(
            messageId = msgId,
            senderId = myShadeId,
            receiverId = groupId,
            isGroupThread = true,
            content = contentJson,
            timestamp = ts,
            messageType = com.shade.app.data.local.entities.MessageType.AUDIO,
            status = if (sent) MessageStatus.SENT else MessageStatus.FAILED,
            audioPath = localAudioFile.absolutePath,
            audioDurationMs = durationMs,
        )
        messageRepository.insertMessage(entity)

        chatRepository.updateLastMessage(chatId = groupId, lastMessage = "🎤 Ses mesajı", timestamp = ts)

        if (!sent) Log.w(TAG, "WebSocket send failed for group audio $msgId")
    }.onFailure { Log.e(TAG, "Group audio send failed: ${it.message}", it) }

    private companion object {
        private const val TAG = "SendGroupAudio"
    }
}
