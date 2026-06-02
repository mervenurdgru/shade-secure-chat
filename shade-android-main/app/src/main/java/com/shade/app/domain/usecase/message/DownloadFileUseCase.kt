package com.shade.app.domain.usecase.message

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.shade.app.crypto.MessageCryptoManager
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.domain.model.AudioMessageContent
import com.shade.app.domain.model.FileMessageContent
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.ImageRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.security.KeyVaultManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.util.encoders.Hex
import java.io.File
import javax.inject.Inject

class DownloadFileUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val cryptoManager: MessageCryptoManager,
    private val keyVaultManager: KeyVaultManager,
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    suspend fun downloadAudio(message: MessageEntity): Result<String> {
        return try {
            val audioContent = gson.fromJson(message.content, AudioMessageContent::class.java)

            val encryptedBytes = imageRepository.downloadEncryptedFile(audioContent.audioId)
                .getOrElse { return Result.failure(it) }

            val myPrivateKeyHex = keyVaultManager.getX25519PrivateKey()
                ?: return Result.failure(Exception("Private key not found"))
            val myShadeId = keyVaultManager.getShadeId()
                ?: return Result.failure(Exception("ShadeId not found"))

            val otherShadeId = if (message.senderId == myShadeId) message.receiverId else message.senderId
            val contact = contactRepository.getOrFetchContact(otherShadeId)
                ?: return Result.failure(Exception("Contact not found"))

            val sharedSecret = cryptoManager.generateSharedSecret(myPrivateKeyHex, contact.encryptionPublicKey)
            val derivedKey = cryptoManager.deriveConversationKey(sharedSecret, 1)
            val audioNonce = Hex.decode(audioContent.audioNonceHex)
            val decryptedBytes = cryptoManager.decryptBytes(encryptedBytes, audioNonce, derivedKey)

            val audioDir = File(context.filesDir, "audio").also { it.mkdirs() }
            val audioFile = File(audioDir, "${message.messageId}.aac")
            audioFile.writeBytes(decryptedBytes)

            messageRepository.updateAudioPath(message.messageId, audioFile.absolutePath, audioContent.durationMs)
            Result.success(audioFile.absolutePath)
        } catch (e: Exception) {
            Log.e("DownloadFile", "Audio download failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun downloadFile(message: MessageEntity): Result<String> {
        return try {
            val fileContent = gson.fromJson(message.content, FileMessageContent::class.java)

            val encryptedBytes = imageRepository.downloadEncryptedFile(fileContent.fileId)
                .getOrElse { return Result.failure(it) }

            val myPrivateKeyHex = keyVaultManager.getX25519PrivateKey()
                ?: return Result.failure(Exception("Private key not found"))
            val myShadeId = keyVaultManager.getShadeId()
                ?: return Result.failure(Exception("ShadeId not found"))

            val otherShadeId = if (message.senderId == myShadeId) message.receiverId else message.senderId
            val contact = contactRepository.getOrFetchContact(otherShadeId)
                ?: return Result.failure(Exception("Contact not found"))

            val sharedSecret = cryptoManager.generateSharedSecret(myPrivateKeyHex, contact.encryptionPublicKey)
            val derivedKey = cryptoManager.deriveConversationKey(sharedSecret, 1)
            val fileNonce = Hex.decode(fileContent.fileNonceHex)
            val decryptedBytes = cryptoManager.decryptBytes(encryptedBytes, fileNonce, derivedKey)

            // İndirilenler klasörüne kaydet
            val downloadsDir = File(context.getExternalFilesDir(null), "Shade").also { it.mkdirs() }
            val outFile = File(downloadsDir, fileContent.fileName)
            outFile.writeBytes(decryptedBytes)

            messageRepository.updateFilePath(message.messageId, outFile.absolutePath)
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            Log.e("DownloadFile", "File download failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
