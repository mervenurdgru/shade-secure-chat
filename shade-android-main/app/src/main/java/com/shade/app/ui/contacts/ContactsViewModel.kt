package com.shade.app.ui.contacts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shade.app.R
import com.shade.app.data.local.entities.ContactEntity
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.ui.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsUiState(
    val contacts: List<ContactEntity> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = ""
)

sealed class ContactLookupState {
    object Idle : ContactLookupState()
    object Loading : ContactLookupState()
    object Success : ContactLookupState()
    data class Error(val message: UiText) : ContactLookupState()
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactRepository: ContactRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SHADE_CONTACTS"
    }

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val _lookupState = MutableStateFlow<ContactLookupState>(ContactLookupState.Idle)
    val lookupState: StateFlow<ContactLookupState> = _lookupState.asStateFlow()

    init {
        Log.d(TAG, "ContactsViewModel başlatıldı")
        observeContacts()
    }

    private fun observeContacts() {
        Log.d(TAG, "Kişiler dinleniyor...")
        contactRepository.getAllContacts()
            .onStart { _uiState.update { it.copy(isLoading = true) } }
            .onEach { contacts ->
                Log.d(TAG, "Kişi listesi güncellendi: ${contacts.size} kişi")
                _uiState.update { it.copy(contacts = contacts, isLoading = false) }
            }
            .catch { e ->
                Log.e(TAG, "Kişi listesi alınamadı: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        Log.d(TAG, "Arama sorgusu değişti: '$query'")
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            observeContacts()
        } else {
            contactRepository.searchContacts(query)
                .onEach { contacts ->
                    Log.d(TAG, "Arama sonucu: ${contacts.size} kişi ('$query')")
                    _uiState.update { it.copy(contacts = contacts) }
                }
                .catch { e -> Log.e(TAG, "Arama hatası: ${e.message}") }
                .launchIn(viewModelScope)
        }
    }

    fun startLookup(shadeId: String, onNavigateToChat: (String, String) -> Unit) {
        Log.d(TAG, "Kişi aranıyor backend'den: $shadeId")
        viewModelScope.launch {
            _lookupState.value = ContactLookupState.Loading
            val contact = contactRepository.getOrFetchContact(shadeId)
            if (contact != null) {
                Log.d(TAG, "Kişi bulundu: $shadeId → chat başlatılıyor")
                _lookupState.value = ContactLookupState.Success
                onNavigateToChat(contact.shadeId, contact.savedName ?: contact.shadeId)
            } else {
                Log.w(TAG, "Kişi bulunamadı: $shadeId")
                _lookupState.value = ContactLookupState.Error(UiText.StringResource(R.string.user_not_found))
            }
        }
    }

    fun resetLookupState() {
        _lookupState.value = ContactLookupState.Idle
    }

    fun deleteContact(contact: ContactEntity) {
        Log.d(TAG, "Kişi siliniyor: ${contact.shadeId}")
        viewModelScope.launch {
            contactRepository.deleteContact(contact)
            Log.d(TAG, "Kişi silindi: ${contact.shadeId}")
        }
    }

    fun toggleBlock(contact: ContactEntity) {
        val newState = !contact.isBlocked
        Log.d(TAG, "Kişi engel durumu değiştiriliyor: ${contact.shadeId} → isBlocked=$newState")
        viewModelScope.launch {
            contactRepository.setBlocked(contact.userId, newState)
            Log.d(TAG, "Engel durumu güncellendi: ${contact.shadeId}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ContactsViewModel temizlendi")
    }
}
