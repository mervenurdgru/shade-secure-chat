package com.shade.app.ui.user

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shade.app.data.local.entities.ContactEntity
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val contact: ContactEntity? = null,
    val isOnline: Boolean = false,
    val lastSeenText: String = "",
    val mediaCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val shadeId: String = checkNotNull(savedStateHandle["shadeId"])

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    // Geriye dönük uyumluluk için
    val contactState = MutableStateFlow<ContactEntity?>(null)

    private val _saveSuccess = MutableSharedFlow<Unit>()
    val saveSuccess = _saveSuccess.asSharedFlow()

    init {
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            // getOrFetchContact: retry once on failure (context cancellation / network hiccup)
            var contact = contactRepository.getOrFetchContact(shadeId)
            if (contact?.profileImagePath == null) {
                kotlinx.coroutines.delay(800)
                val retried = contactRepository.getOrFetchContact(shadeId)
                if (retried != null) contact = retried
            }
            if (contact == null) contact = contactRepository.getContactByShadeId(shadeId)
            contactState.value = contact
            _uiState.update { it.copy(contact = contact, isLoading = false) }
        }

        // Geçici: GET user/status/{shadeId} — backend hazır olunca açılabilir.
//        viewModelScope.launch {
//            try {
//                val token = "Bearer ${keyVaultManager.getAccessToken()}"
//                val response = userService.getUserStatus(token, shadeId)
//                if (response.isSuccessful) {
//                    val status = response.body() ?: return@launch
//                    val lastSeenText = when {
//                        status.isOnline -> "Çevrimiçi"
//                        status.lastActive.isNullOrBlank() -> "Son görülme bilinmiyor"
//                        else -> {
//                            val instant = Instant.parse(status.lastActive)
//                            val minutesAgo = ChronoUnit.MINUTES.between(instant, Instant.now())
//                            when {
//                                minutesAgo < 1 -> "Az önce görüldü"
//                                minutesAgo < 60 -> "Son görülme: $minutesAgo dakika önce"
//                                minutesAgo < 1440 -> "Son görülme: ${minutesAgo / 60} saat önce"
//                                else -> {
//                                    val formatter = DateTimeFormatter.ofPattern("d MMM HH:mm")
//                                        .withZone(ZoneId.systemDefault())
//                                    "Son görülme: ${formatter.format(instant)}"
//                                }
//                            }
//                        }
//                    }
//                    _uiState.update {
//                        it.copy(isOnline = status.isOnline, lastSeenText = lastSeenText)
//                    }
//                }
//            } catch (_: Exception) {}
//        }

        viewModelScope.launch {
            try {
                val count = messageRepository.countMediaMessages(shadeId, isGroupThread = false)
                _uiState.update { it.copy(mediaCount = count) }
            } catch (_: Exception) {}
        }
    }

    fun saveContact(name: String) {
        val currentContact = _uiState.value.contact
        if (currentContact == null) {
            Log.w("ProfileVM", "saveContact erken çıkış: contact null (lookup başarısız olmuş olabilir)")
            return
        }
        if (name.isBlank()) {
            Log.w("ProfileVM", "saveContact erken çıkış: name boş")
            return
        }
        if (currentContact.savedName == name) {
            Log.d("ProfileVM", "saveContact erken çıkış: aynı isim zaten kayıtlı ('$name')")
            return
        }
        viewModelScope.launch {
            contactRepository.updateContactName(shadeId, name)
            val updated = contactRepository.getContactByShadeId(shadeId)
            contactState.value = updated
            _uiState.update { it.copy(contact = updated) }
            _saveSuccess.emit(Unit)
        }
    }

    fun toggleBlock() {
        val contact = _uiState.value.contact ?: return
        val newBlocked = !contact.isBlocked
        viewModelScope.launch {
            contactRepository.setBlocked(contact.userId, newBlocked)
            val updated = contactRepository.getContactByShadeId(shadeId)
            contactState.value = updated
            _uiState.update { it.copy(contact = updated) }
        }
    }
}
