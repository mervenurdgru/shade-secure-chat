package com.shade.app.ui.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shade.app.data.local.entities.ContactEntity
import com.shade.app.data.local.entities.GroupEntity
import com.shade.app.data.local.entities.GroupMemberEntity
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.data.local.entities.MessageType
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.GroupRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.security.KeyVaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupDetailUiState(
    val group: GroupEntity? = null,
    val members: List<GroupMemberEntity> = emptyList(),
    val myUserId: String = "",
    val isOwner: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val inviteCode: String? = null,
    val leftGroup: Boolean = false,
    val mediaMessages: List<MessageEntity> = emptyList(),
    val savedContacts: List<ContactEntity> = emptyList(),
) {
    val memberCount: Int get() = members.size
}

/**
 * Backs `GroupDetailScreen`.
 *
 * Member list + group metadata are observed live from the local cache so any
 * `GroupMembershipEvent` immediately reflects in the UI. A best-effort REST
 * refresh runs on init to fill stale shade_ids and pick up server-side
 * changes (avatar, name).
 */
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val keyVaultManager: KeyVaultManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val groupId: String = savedStateHandle["groupId"] ?: ""

    private val _uiState = MutableStateFlow(GroupDetailUiState())
    val uiState: StateFlow<GroupDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            val me = keyVaultManager.getUserId().orEmpty()
            _uiState.update { it.copy(myUserId = me) }
        }
        observeLocal()
        observeSavedContacts()
        refresh()
        loadMediaMessages()
    }

    private fun observeLocal() {
        combine(
            groupRepository.observeCachedGroup(groupId),
            groupRepository.observeCachedMembers(groupId),
        ) { group, members -> group to members }
            .onEach { (group, members) ->
                _uiState.update { st ->
                    st.copy(
                        group = group,
                        members = members.sortedWith(
                            compareByDescending<GroupMemberEntity> { it.role == "owner" }
                                .thenBy { it.shadeId.ifBlank { it.userId } }
                        ),
                        isOwner = group?.ownerId == st.myUserId,
                        isLoading = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSavedContacts() {
        contactRepository.getAllContacts()
            .map { list -> list.filter { it.savedName != null } }
            .onEach { contacts -> _uiState.update { it.copy(savedContacts = contacts) } }
            .launchIn(viewModelScope)
    }

    private fun refresh() {
        viewModelScope.launch {
            groupRepository.getGroup(groupId).onFailure { e ->
                // 403 = kullanıcı artık bu grubun üyesi değil; hata gösterme
                if (e.message?.contains("403") == true) return@onFailure
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun createInvite() {
        viewModelScope.launch {
            groupRepository.createInvite(groupId = groupId, maxUses = 5).fold(
                onSuccess = { resp -> _uiState.update { it.copy(inviteCode = resp.code) } },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } },
            )
        }
    }

    fun dismissInvite() {
        _uiState.update { it.copy(inviteCode = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun removeMember(userId: String) {
        if (!_uiState.value.isOwner) return
        if (userId == _uiState.value.myUserId) return
        viewModelScope.launch {
            groupRepository.removeMember(groupId, userId).fold(
                onSuccess = { /* GME event tüm cihazlarda sistem mesajını ekleyecek */ },
                onFailure = { e -> _uiState.update { it.copy(error = e.message ?: "Üye çıkarılamadı") } },
            )
        }
    }

    fun leaveGroup() {
        val me = _uiState.value.myUserId
        if (me.isEmpty()) return
        viewModelScope.launch {
            val myShadeId = keyVaultManager.getShadeId().orEmpty()
            groupRepository.removeMember(groupId, me).fold(
                onSuccess = {
                    insertSystemMessage("$myShadeId gruptan ayrıldı")
                    _uiState.update { it.copy(leftGroup = true) }
                },
                onFailure = { e -> _uiState.update { it.copy(error = e.message ?: "Gruptan çıkılamadı") } },
            )
        }
    }

    fun deleteGroup() {
        if (!_uiState.value.isOwner) return
        viewModelScope.launch {
            groupRepository.deleteGroup(groupId).fold(
                onSuccess = { _uiState.update { it.copy(leftGroup = true) } },
                onFailure = { e -> _uiState.update { it.copy(error = e.message ?: "Grup silinemedi") } },
            )
        }
    }

    fun addMemberByShadeId(shadeId: String) {
        val trimmed = shadeId.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val contact = contactRepository.getOrFetchContact(trimmed)
            if (contact == null) {
                _uiState.update { it.copy(error = "Kullanıcı bulunamadı: $trimmed") }
                return@launch
            }
            groupRepository.addMember(groupId, contact.userId).fold(
                onSuccess = { refresh() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message ?: "Üye eklenemedi") } },
            )
        }
    }

    private suspend fun insertSystemMessage(text: String) {
        val entity = MessageEntity(
            messageId = java.util.UUID.randomUUID().toString(),
            senderId = "system",
            receiverId = groupId,
            isGroupThread = true,
            content = text,
            timestamp = System.currentTimeMillis(),
            messageType = MessageType.SYSTEM,
            status = MessageStatus.READ,
        )
        messageRepository.insertMessage(entity)
    }

    private fun loadMediaMessages() {
        viewModelScope.launch {
            val media = messageRepository.getMediaMessages(groupId, isGroupThread = true)
            _uiState.update { it.copy(mediaMessages = media) }
        }
    }
}
