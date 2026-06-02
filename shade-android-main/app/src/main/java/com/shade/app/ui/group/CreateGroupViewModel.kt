package com.shade.app.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shade.app.data.local.entities.ChatEntity
import com.shade.app.data.local.entities.ContactEntity
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateGroupUiState(
    val contacts: List<ContactEntity> = emptyList(),
    val selectedUserIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdGroupId: String? = null,
)

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState

    init {
        // Kişi listesini dinle
        contactRepository.getAllContacts()
            .onEach { contacts ->
                _uiState.update { it.copy(contacts = contacts) }
            }
            .catch { }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleContact(userId: String) {
        _uiState.update { state ->
            val selected = state.selectedUserIds.toMutableSet()
            if (userId in selected) selected.remove(userId) else selected.add(userId)
            state.copy(selectedUserIds = selected)
        }
    }

    fun createGroup(name: String) {
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Grup adı boş olamaz") }
            return
        }
        val memberIds = _uiState.value.selectedUserIds.toList()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = groupRepository.createGroup(name.trim(), memberIds)
            result.fold(
                onSuccess = { group ->
                    chatRepository.insertOrUpdateChat(
                        ChatEntity(
                            chatId = group.groupId,
                            lastMessage = null,
                            lastMessageTimestamp = 0L,
                            isGroup = true,
                            groupName = group.name,
                        )
                    )
                    _uiState.update { it.copy(isLoading = false, createdGroupId = group.groupId) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Grup oluşturulamadı") }
                }
            )
        }
    }
}
