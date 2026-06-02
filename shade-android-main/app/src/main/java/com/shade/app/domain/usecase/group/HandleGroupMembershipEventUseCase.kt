package com.shade.app.domain.usecase.group

import android.util.Log
import com.shade.app.data.local.entities.ChatEntity
import com.shade.app.data.local.entities.GroupMemberEntity
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.data.local.entities.MessageType
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.GroupRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.domain.repository.SenderKeyRepository
import com.shade.app.proto.GroupMembershipEvent
import com.shade.app.security.KeyVaultManager
import java.util.UUID
import javax.inject.Inject

/**
 * Reacts to server-authoritative [GroupMembershipEvent]s.
 *
 *  - **JOINED, subject = self** → refresh the local group cache; new SKDMs
 *    will arrive separately. Nothing crypto-wise to do here.
 *  - **JOINED, subject ≠ self** → add the new member locally and ship our
 *    current SKDM to their device(s).
 *  - **LEFT / REMOVED, subject = self** → wipe all local sender-key state for
 *    the group. Any further payloads will be undecryptable.
 *  - **LEFT / REMOVED, subject ≠ self** → rotate our own sender key (so the
 *    departing device's chain snapshot can't read future messages) and
 *    re-distribute the new SKDM to the remaining members.
 */
class HandleGroupMembershipEventUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val senderKeyRepository: SenderKeyRepository,
    private val ensureOwnKey: EnsureOwnSenderKeyUseCase,
    private val rotateOwnKey: RotateOwnSenderKeyUseCase,
    private val distributeSenderKey: DistributeSenderKeyUseCase,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val keyVaultManager: KeyVaultManager,
) {
    suspend operator fun invoke(event: GroupMembershipEvent) {
        try {
            val myUserId = keyVaultManager.getUserId() ?: return
            val isSelf = event.subjectId == myUserId
            val groupId = event.groupId

            Log.i(
                TAG,
                "GME group=$groupId kind=${event.kind} subject=${event.subjectId} " +
                        "actor=${event.actorId} (self=$isSelf)"
            )

            when (event.kind) {
                GroupMembershipEvent.Kind.JOINED -> handleJoined(event, isSelf)
                GroupMembershipEvent.Kind.LEFT,
                GroupMembershipEvent.Kind.REMOVED -> handleLeftOrRemoved(event, isSelf)
                else -> Unit
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle GME: ${e.message}", e)
        }
    }

    private suspend fun handleJoined(event: GroupMembershipEvent, isSelf: Boolean) {
        // Always refresh the group from the server so the local cache is
        // authoritative (member list + role + display name). This also
        // populates shade_id for the JOINED user.
        val groupResult = groupRepository.getGroup(event.groupId)
        groupResult.onFailure {
            Log.w(TAG, "GET /groups/${event.groupId} failed: ${it.message}")
        }

        if (isSelf) {
            // Restore (or create) the chat row so the group reappears in the
            // chat list even if the user had previously deleted it.
            val groupName = groupResult.getOrNull()?.name
                ?: groupRepository.getCachedGroup(event.groupId)?.name
                ?: event.groupId
            val ts = System.currentTimeMillis()
            chatRepository.insertOrUpdateChat(
                ChatEntity(
                    chatId = event.groupId,
                    lastMessage = "Gruba eklendiniz",
                    lastMessageTimestamp = ts,
                    unreadCount = 1,
                    isGroup = true,
                    groupName = groupName,
                )
            )
            messageRepository.insertMessage(
                MessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    senderId = "system",
                    receiverId = event.groupId,
                    isGroupThread = true,
                    content = "Gruba eklendiniz",
                    timestamp = ts,
                    messageType = MessageType.SYSTEM,
                    status = MessageStatus.READ,
                )
            )
            return
        }

        // Add the new peer locally with a placeholder shade_id if we don't
        // know it yet (refreshed above by getGroup).
        val cachedMember = groupRepository.getCachedMembers(event.groupId)
            .firstOrNull { it.userId == event.subjectId }
        val member = cachedMember ?: GroupMemberEntity(
            groupId = event.groupId,
            userId = event.subjectId,
            shadeId = "",
            role = "member",
        )
        groupRepository.upsertLocalMember(member)

        // Send our SKDM to the new member. No rotation — joiner should pick
        // up from the current chain forward.
        val ownKey = ensureOwnKey(event.groupId)
        distributeSenderKey(ownKey, force = true, onlyUserId = event.subjectId)
    }

    private suspend fun handleLeftOrRemoved(event: GroupMembershipEvent, isSelf: Boolean) {
        if (isSelf) {
            // We were removed / chose to leave. Tear down all crypto state and
            // wipe local message history so rejoining starts with a clean slate.
            senderKeyRepository.deleteOwn(event.groupId)
            senderKeyRepository.clearPeersForGroup(event.groupId)
            senderKeyRepository.purgeStaleDispatched(event.groupId, keepKeyId = Long.MAX_VALUE)
            messageRepository.deleteAllGroupMessages(event.groupId)
            groupRepository.clearLocal(event.groupId)
            // Touch the chats table so the HomeViewModel Flow re-emits and
            // recomputes nonMemberGroupIds → the chat becomes deletable.
            chatRepository.updateLastMessage(event.groupId, "Bu gruptan ayrıldınız", System.currentTimeMillis())
            return
        }

        // Look up shade_id before removing from local cache.
        val subjectShadeId = groupRepository.getCachedMembers(event.groupId)
            .firstOrNull { it.userId == event.subjectId }?.shadeId?.takeIf { it.isNotBlank() }
            ?: event.subjectId

        val systemText = when (event.kind) {
            GroupMembershipEvent.Kind.REMOVED -> "$subjectShadeId gruptan çıkarıldı"
            else -> "$subjectShadeId gruptan ayrıldı"
        }
        messageRepository.insertMessage(
            MessageEntity(
                messageId = UUID.randomUUID().toString(),
                senderId = "system",
                receiverId = event.groupId,
                isGroupThread = true,
                content = systemText,
                timestamp = System.currentTimeMillis(),
                messageType = MessageType.SYSTEM,
                status = MessageStatus.READ,
            )
        )

        // Someone else left/was removed. Drop their key material locally.
        senderKeyRepository.clearPeersForUser(event.groupId, event.subjectId)
        groupRepository.removeLocalMember(event.groupId, event.subjectId)

        // Rotate our own key + distribute to remaining members.
        val rotated = rotateOwnKey(event.groupId)
        distributeSenderKey(rotated, force = true)
    }

    private companion object {
        private const val TAG = "HandleGME"
    }
}
