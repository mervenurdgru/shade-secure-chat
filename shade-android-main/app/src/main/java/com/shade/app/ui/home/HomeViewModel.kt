package com.shade.app.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shade.app.data.local.model.ChatWithContact
import com.shade.app.data.remote.websocket.MessageListener
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.GroupRepository
import com.shade.app.security.KeyVaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class HomeUiState(
    val chats: List<ChatWithContact> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Kullanıcının üye OLMADIĞI grup sohbetlerinin chatId'leri */
    val nonMemberGroupIds: Set<String> = emptySet(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository,
    private val groupRepository: GroupRepository,
    private val messageListener: MessageListener,
    private val keyVaultManager: KeyVaultManager
) : ViewModel() {

    companion object {
        private const val TAG = "SHADE_HOME"
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    init {
        Log.d(TAG, "HomeViewModel başlatıldı")
        messageListener.ensureConnected()
        observeChats()
        prefetchMissingContactPhotos()
    }

    private fun observeChats() {
        Log.d(TAG, "Sohbetler dinleniyor...")
        chatRepository.getAllChatsWithContact()
            .onStart { _uiState.update { it.copy(isLoading = true) } }
            .onEach { chatList ->
                Log.d(TAG, "Sohbet listesi güncellendi: ${chatList.size} sohbet")
                val myShadeId = keyVaultManager.getShadeId().orEmpty()
                val nonMemberIds = chatList
                    .filter { it.chat.isGroup }
                    .filter { chatWithContact ->
                        val members = groupRepository.getCachedMembers(chatWithContact.chat.chatId)
                        members.none { it.shadeId == myShadeId || it.userId == myShadeId }
                    }
                    .map { it.chat.chatId }
                    .toSet()
                _uiState.update { it.copy(chats = chatList, isLoading = false, nonMemberGroupIds = nonMemberIds) }
            }
            .catch { e ->
                Log.e(TAG, "Sohbet listesi alınamadı: ${e.message}")
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Uygulama başlarken eksik profil fotoğraflarını arka planda indirir.
     * Profil ekranını açmaya gerek kalmadan tüm kişilerin fotoğrafları önbelleğe alınır.
     */
    private fun prefetchMissingContactPhotos() {
        viewModelScope.launch {
            try {
                val contacts = contactRepository.getAllContacts().first()
                val missing = contacts.filter { contact ->
                    contact.profileImagePath == null ||
                        !File(contact.profileImagePath!!).exists()
                }
                if (missing.isEmpty()) {
                    Log.d(TAG, "Tüm kişilerin fotoğrafları mevcut, prefetch atlanıyor")
                    return@launch
                }
                Log.d(TAG, "Eksik fotoğraf prefetch başlıyor: ${missing.size} kişi")
                missing.forEach { contact ->
                    launch {
                        try {
                            contactRepository.getOrFetchContact(contact.shadeId)
                            Log.d(TAG, "Fotoğraf prefetch tamamlandı: ${contact.shadeId}")
                        } catch (e: Exception) {
                            Log.w(TAG, "Fotoğraf prefetch başarısız: ${contact.shadeId} — ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Kişi listesi alınamadı, prefetch atlandı: ${e.message}")
            }
        }
    }

    fun deleteChat(chat: ChatWithContact) {
        Log.d(TAG, "Sohbet siliniyor: ${chat.chat.chatId}")
        viewModelScope.launch {
            chatRepository.deleteChat(chat.chat.chatId)
            Log.d(TAG, "Sohbet silindi: ${chat.chat.chatId}")
        }
    }

    fun logout() {
        Log.d(TAG, "Çıkış yapılıyor...")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                keyVaultManager.clearVault()
            }
            _loggedOut.value = true
            Log.d(TAG, "Çıkış tamamlandı")
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "HomeViewModel temizlendi")
    }
}
