package com.shade.app.ui.chat

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.data.preferences.TranslationConsentRepository
import com.shade.app.data.repository.TranslationRepository
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.GroupRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.data.repository.ChatPrefsRepository
import com.shade.app.domain.usecase.message.DeleteMessageForEveryoneUseCase
import com.shade.app.domain.usecase.message.DownloadFileUseCase
import com.shade.app.domain.usecase.message.DownloadImageUseCase
import com.shade.app.domain.usecase.message.EditMessageUseCase
import com.shade.app.domain.usecase.message.MarkChatAsReadUseCase
import com.shade.app.domain.usecase.message.SendAudioMessageUseCase
import com.shade.app.domain.usecase.message.SendFileMessageUseCase
import com.shade.app.domain.usecase.message.SendGroupAudioMessageUseCase
import com.shade.app.domain.usecase.message.SendGroupImageMessageUseCase
import com.shade.app.domain.usecase.message.SendGroupMessageUseCase
import com.shade.app.domain.usecase.message.SendImageMessageUseCase
import com.shade.app.domain.usecase.message.SendMessageUseCase
import java.io.File
import com.shade.app.security.KeyVaultManager
import com.shade.app.util.ActiveChatTracker
import com.shade.app.util.AppError
import com.shade.app.util.ErrorReporter
import com.shade.app.util.NotificationHelper
import com.shade.app.util.toAppError
import com.shade.app.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val chatName: String = "",
    val chatId: String = "",
    val contactShadeId: String? = null,
    val contactImagePath: String? = null,
    val isGroupChat: Boolean = false,
    val myShadeId: String = "",
    val initialScrollIndex: Int? = null,
    val firstUnreadMessageId: String? = null,
    val isSendingImage: Boolean = false,
    val isSendingAudio: Boolean = false,
    val isSendingFile: Boolean = false,
    val downloadingMessageId: String? = null,
    val downloadingFileMessageId: String? = null,
    val downloadProgress: Float = 0f,
    // Search
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MessageEntity> = emptyList(),
    // Last seen
    val lastSeenText: String = "",
    // Translation
    val translatedMessages: Map<String, String> = emptyMap(),
    val translatingMessageId: String? = null,
    // Edit
    val editingMessage: MessageEntity? = null,
    // Reply
    val replyingToMessage: MessageEntity? = null,
    // Chat customisation
    val chatBackgroundColor: Int? = null,   // ARGB int, null = default
    val errorMessage: String? = null,       // Snackbar mesajı — null = gösterme
    /** Grup sohbetinde senderId → görünen ad haritası */
    val groupSenderNames: Map<String, String> = emptyMap(),
    /** Grup sohbetinde senderId → shadeId haritası (ikinci satır için) */
    val groupSenderShadeIds: Map<String, String> = emptyMap(),
    /** Kullanıcının bu grupta hâlâ üye olup olmadığı; 1:1 sohbetlerde her zaman true */
    val isGroupMember: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sendGroupMessageUseCase: SendGroupMessageUseCase,
    private val sendGroupImageMessageUseCase: SendGroupImageMessageUseCase,
    private val sendGroupAudioMessageUseCase: SendGroupAudioMessageUseCase,
    private val sendImageMessageUseCase: SendImageMessageUseCase,
    private val downloadImageUseCase: DownloadImageUseCase,
    private val markChatAsReadUseCase: MarkChatAsReadUseCase,
    private val deleteMessageForEveryoneUseCase: DeleteMessageForEveryoneUseCase,
    private val editMessageUseCase: EditMessageUseCase,
    private val sendAudioMessageUseCase: SendAudioMessageUseCase,
    private val sendFileMessageUseCase: SendFileMessageUseCase,
    private val downloadFileUseCase: DownloadFileUseCase,
    private val chatRepository: ChatRepository,
    private val groupRepository: GroupRepository,
    private val contactRepository: ContactRepository,
    private val keyVaultManager: KeyVaultManager,
    private val translationRepository: TranslationRepository,
    private val translationConsentRepository: TranslationConsentRepository,
    private val activeChatTracker: ActiveChatTracker,
    private val notificationHelper: NotificationHelper,
    private val chatPrefsRepository: ChatPrefsRepository,
    private val errorReporter: ErrorReporter,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "SHADE_CHAT"
    }

    private val chatId: String = savedStateHandle["chatId"] ?: ""
    private val initialChatName: String = savedStateHandle["chatName"] ?: ""

    private val _uiState = MutableStateFlow(
        ChatUiState(
            chatId = chatId,
            chatName = initialChatName
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private fun threadIsGroupFlow(): Flow<Boolean> =
        combine(
            chatRepository.observeChatWithContact(chatId),
            groupRepository.observeCachedGroup(chatId),
        ) { cw, grp ->
            cw?.chat?.isGroup == true || grp != null
        }.distinctUntilChanged()

    /**
     * Sayfalı mesaj akışı — büyük sohbetlerde RAM kullanımını sınırlar.
     * [cachedIn] ile ViewModel yeniden başlatılsa da cache'den beslenir.
     *
     * NOT: Şu an [ChatUiState.messages] ile paralel çalışır.
     * Arama, okunmamış sayacı gibi feature'lar hâlâ [messages]'ı kullanır.
     * İleride [messages] tamamen kaldırılıp yalnızca sayfalı veri kullanılabilir.
     */
    val pagedMessages: Flow<PagingData<MessageEntity>> =
        threadIsGroupFlow()
            .flatMapLatest { isGroup ->
                messageRepository.getMessagesForChatPaged(chatId, isGroup)
            }
            .cachedIn(viewModelScope)

    val translationDisclaimerAccepted: StateFlow<Boolean> =
        translationConsentRepository.disclaimerAccepted.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            false
        )

    private var hasCalculatedInitialScroll = false

    /** 1-to-1 sohbeti için tek sefer `/user/lookup` — grubu yanlışlıkla tetiklememek için chat satırı yoksa bekleme. */
    private var prefetchedDmPeerProfile = false

    init {
        Log.d(TAG, "ChatViewModel başlatıldı: chatId=$chatId")
        activeChatTracker.setActive(chatId)
        notificationHelper.clearNotifications(chatId)
        viewModelScope.launch {
            val shadeId = keyVaultManager.getShadeId() ?: ""
            _uiState.update { it.copy(myShadeId = shadeId) }
        }
        observeMessages()
        observeChatDetails()
        observeChatPrefs()
        viewModelScope.launch { markChatAsReadUseCase(chatId) }
    }

    override fun onCleared() {
        super.onCleared()
        activeChatTracker.clear()
        Log.d(TAG, "ChatViewModel temizlendi: chatId=$chatId")
    }

    private fun observeMessages() {
        threadIsGroupFlow()
            .flatMapLatest { isGroup ->
                messageRepository.getMessagesForChat(chatId, isGroup)
            }
            .onEach { messages ->
                Log.d(TAG, "Mesaj listesi güncellendi: ${messages.size} mesaj")
                val myId = _uiState.value.myShadeId

                if (!hasCalculatedInitialScroll && messages.isNotEmpty()) {
                    val firstUnreadIdx = messages.indexOfFirst {
                        it.senderId != myId && it.status != MessageStatus.READ
                    }
                    if (firstUnreadIdx != -1) {
                        val firstUnreadMessageId = messages[firstUnreadIdx].messageId
                        val reversedIndex = (messages.size - 1 - firstUnreadIdx) + 1
                        _uiState.update {
                            it.copy(
                                initialScrollIndex = reversedIndex,
                                firstUnreadMessageId = firstUnreadMessageId
                            )
                        }
                    }
                    hasCalculatedInitialScroll = true
                }

                // Gönderen adlarını çek (grup ve 1:1 — isGroupChat state'i henüz gelmemiş olabilir)
                val uniqueSenders = messages
                    .filter { it.senderId != myId }
                    .map { it.senderId }
                    .distinct()
                data class SenderInfo(val displayName: String, val shadeId: String)
                val senderInfos: Map<String, SenderInfo> = uniqueSenders.associateWith { senderId ->
                    val contact = contactRepository.getOrFetchContact(senderId)
                    val shadeId = contact?.shadeId ?: senderId
                    val displayName = contact?.let { c -> c.savedName ?: c.profileName ?: c.shadeId } ?: senderId
                    SenderInfo(displayName, shadeId)
                }
                val senderNames = senderInfos.mapValues { it.value.displayName }
                val senderShadeIds = senderInfos.mapValues { it.value.shadeId }

                _uiState.update { it.copy(messages = messages, groupSenderNames = senderNames, groupSenderShadeIds = senderShadeIds) }

                val hasUnread = messages.any {
                    it.senderId != myId && it.status != MessageStatus.READ
                }
                if (hasUnread) {
                    viewModelScope.launch { markChatAsReadUseCase(chatId) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeChatDetails() {
        combine(
            chatRepository.observeChatWithContact(chatId),
            groupRepository.observeCachedGroup(chatId),
            groupRepository.observeCachedMembers(chatId),
        ) { cw, grp, members -> Triple(cw, grp, members) }
            .onEach { (chatWithContact, cachedGroup, members) ->
                // `chats.isGroup` is wrong if the chat row was first created via
                // inbound message helpers that forgot the flag — also trust the
                // groups table backing `observeCachedGroup`.
                val isGroupChat =
                    chatWithContact?.chat?.isGroup == true || cachedGroup != null

                if (cachedGroup != null &&
                    chatWithContact != null &&
                    !chatWithContact.chat.isGroup
                ) {
                    viewModelScope.launch {
                        chatRepository.alignChatRowFromGroupCache(chatId)
                    }
                }

                val photoMissing = chatWithContact?.contact?.profileImagePath
                    ?.let { java.io.File(it).exists() } != true
                if (!isGroupChat && chatWithContact != null && (!prefetchedDmPeerProfile || photoMissing)) {
                    prefetchedDmPeerProfile = true
                    viewModelScope.launch {
                        contactRepository.getOrFetchContact(chatId)
                    }
                }

                val headerTitle = when {
                    isGroupChat ->
                        cachedGroup?.name?.takeIf { it.isNotBlank() }
                            ?: chatWithContact?.chat?.groupName?.takeIf { it.isNotBlank() }
                            ?: initialChatName.takeIf { it.isNotBlank() }
                            ?: chatId
                    else ->
                        chatWithContact?.headerName?.takeIf { it.isNotBlank() }
                            ?: initialChatName.takeIf { it.isNotBlank() }
                            ?: chatId
                }

                // Kullanıcının grupta üye olup olmadığını kontrol et
                val isGroupMember = if (isGroupChat) {
                    val myId = _uiState.value.myShadeId
                    members.any { it.shadeId == myId || it.userId == myId }
                } else {
                    true
                }

                _uiState.update {
                    it.copy(
                        chatName = headerTitle,
                        contactShadeId =
                            if (!isGroupChat) chatWithContact?.contact?.shadeId else null,
                        contactImagePath =
                            if (!isGroupChat) chatWithContact?.contact?.profileImagePath else null,
                        isGroupChat = isGroupChat,
                        isGroupMember = isGroupMember,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // Geçici: user/status API devre dışı — aşağıdaki gövdeyi geri alıp init'te fetchUserStatus() çağır.
//    private fun fetchUserStatus() {
//        viewModelScope.launch {
//            try {
//                val token = "Bearer ${keyVaultManager.getAccessToken()}"
//                val response = userService.getUserStatus(token, chatId)
//                if (response.isSuccessful) {
//                    val status = response.body() ?: return@launch
//                    val text = when {
//                        status.isOnline -> "Çevrimiçi"
//                        status.lastActive.isNullOrBlank() -> ""
//                        else -> {
//                            val instant = Instant.parse(status.lastActive)
//                            val minutesAgo = ChronoUnit.MINUTES.between(instant, Instant.now())
//                            when {
//                                minutesAgo < 60 -> "Son görülme: $minutesAgo dakika önce"
//                                minutesAgo < 1440 -> "Son görülme: ${minutesAgo / 60} saat önce"
//                                else -> {
//                                    val formatter = DateTimeFormatter.ofPattern("d MMM")
//                                        .withZone(ZoneId.systemDefault())
//                                    "Son görülme: ${formatter.format(instant)}"
//                                }
//                            }
//                        }
//                    }
//                    _uiState.update { it.copy(lastSeenText = text) }
//                    Log.d(TAG, "Son görülme: $text")
//                }
//            } catch (e: Exception) {
//                errorReporter.report(TAG, "Son görülme alınamadı", e)
//                // Non-fatal: kullanıcıya gösterme, sadece raporla
//            }
//        }
//    }

    private fun observeChatPrefs() {
        chatPrefsRepository.getChatBackground(chatId)
            .onEach { color -> _uiState.update { it.copy(chatBackgroundColor = color) } }
            .launchIn(viewModelScope)
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        val replyTo = _uiState.value.replyingToMessage
        val isGroup = _uiState.value.isGroupChat
        Log.d(TAG, "Metin mesajı gönderiliyor → chatId=$chatId, group=$isGroup, reply=${replyTo?.messageId}")
        viewModelScope.launch {
            if (isGroup) {
                // Group messaging uses the Sender Keys ratchet — replies are
                // not supported on the group wire today (the metadata for
                // reply-to lives inside the encrypted plaintext for 1-to-1
                // chats; we'd need to wrap the JSON the same way here).
                sendGroupMessageUseCase(
                    groupId = chatId,
                    groupName = _uiState.value.chatName,
                    text = content,
                )
            } else {
                sendMessageUseCase(
                    receiverShadeId = chatId,
                    content = content,
                    replyToId = replyTo?.messageId,
                    replyToContent = replyTo?.content?.take(80)
                )
            }
            _uiState.update { it.copy(replyingToMessage = null) }
            Log.d(TAG, "Metin mesajı gönderildi")
        }
    }

    fun sendImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingImage = true, errorMessage = null) }
            try {
                if (_uiState.value.isGroupChat) {
                    sendGroupImageMessageUseCase(
                        groupId = chatId,
                        groupName = _uiState.value.chatName,
                        imageUri = uri,
                    )
                } else {
                    sendImageMessageUseCase(receiverShadeId = chatId, imageUri = uri)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Görsel gönderilemedi: ${e.message}", e)
                _uiState.update {
                    it.copy(errorMessage = "Görsel gönderilemedi: ${e.message ?: "Bilinmeyen hata"}")
                }
            }
            _uiState.update { it.copy(isSendingImage = false) }
        }
    }

    fun sendAudio(audioFile: File, durationMs: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingAudio = true, errorMessage = null) }
            val result = if (_uiState.value.isGroupChat) {
                sendGroupAudioMessageUseCase(
                    groupId = chatId,
                    audioFile = audioFile,
                    durationMs = durationMs,
                )
            } else {
                sendAudioMessageUseCase(
                    receiverShadeId = chatId,
                    audioFile = audioFile,
                    durationMs = durationMs,
                )
            }
            _uiState.update { it.copy(isSendingAudio = false) }
            result.onFailure { e ->
                Log.e(TAG, "Ses mesajı gönderilemedi: ${e.message}", e)
                _uiState.update {
                    it.copy(errorMessage = "Ses mesajı gönderilemedi: ${e.message ?: "Bilinmeyen hata"}")
                }
            }
        }
    }

    fun sendFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingFile = true) }
            sendFileMessageUseCase(receiverShadeId = chatId, fileUri = uri)
            _uiState.update { it.copy(isSendingFile = false) }
        }
    }

    fun downloadAudio(message: MessageEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingFileMessageId = message.messageId) }
            downloadFileUseCase.downloadAudio(message)
            _uiState.update { it.copy(downloadingFileMessageId = null) }
        }
    }

    fun downloadFile(message: MessageEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingFileMessageId = message.messageId) }
            downloadFileUseCase.downloadFile(message)
            _uiState.update { it.copy(downloadingFileMessageId = null) }
        }
    }

    fun downloadImage(message: MessageEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingMessageId = message.messageId, downloadProgress = 0f) }
            val result = downloadImageUseCase(message) { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
            result.onFailure { e ->
                errorReporter.report(TAG, "Görsel indirme başarısız", e)
                _uiState.update { it.copy(errorMessage = "Görsel indirilemedi") }
            }
            _uiState.update { it.copy(downloadingMessageId = null, downloadProgress = 0f) }
        }
    }

    fun clearUnreadNotification() {
        _uiState.update { it.copy(firstUnreadMessageId = null) }
    }

    fun toggleSearch() {
        val nowActive = !_uiState.value.isSearchActive
        _uiState.update { it.copy(isSearchActive = nowActive, searchQuery = "", searchResults = emptyList()) }
        Log.d(TAG, "Arama modu: $nowActive")
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        messageRepository.searchMessages(chatId, query, _uiState.value.isGroupChat)
            .onEach { results ->
                Log.d(TAG, "Arama sonucu: ${results.size} mesaj ('$query')")
                _uiState.update { it.copy(searchResults = results) }
            }
            .catch { e ->
                errorReporter.report(TAG, "Arama hatası", e)
                _uiState.update { it.copy(errorMessage = e.toAppError().toUserMessage()) }
            }
            .launchIn(viewModelScope)
    }

    fun deleteForMe(message: MessageEntity) {
        viewModelScope.launch {
            messageRepository.deleteMessage(message)
            Log.d(TAG, "Mesaj kendimden silindi: ${message.messageId}")
        }
    }

    fun deleteForEveryone(message: MessageEntity) {
        viewModelScope.launch {
            deleteMessageForEveryoneUseCase(message)
            Log.d(TAG, "Mesaj herkesten silindi: ${message.messageId}")
        }
    }

    fun startEditing(message: MessageEntity) {
        _uiState.update { it.copy(editingMessage = message) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingMessage = null) }
    }

    fun confirmEdit(newContent: String) {
        val message = _uiState.value.editingMessage ?: return
        _uiState.update { it.copy(editingMessage = null) }
        viewModelScope.launch {
            editMessageUseCase(message, newContent)
            Log.d(TAG, "Mesaj düzenlendi: ${message.messageId}")
        }
    }

    // ── Reply ──────────────────────────────────────────────────────────────────
    fun startReply(message: MessageEntity) {
        _uiState.update { it.copy(replyingToMessage = message) }
    }

    fun cancelReply() {
        _uiState.update { it.copy(replyingToMessage = null) }
    }

    // ── Chat background ────────────────────────────────────────────────────────
    fun setChatBackground(colorArgb: Int?) {
        viewModelScope.launch {
            chatPrefsRepository.setChatBackground(chatId, colorArgb)
        }
    }

    /** Snackbar gösterildikten sonra hata durumunu temizle. */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun acknowledgeTranslationDisclaimer() {
        viewModelScope.launch {
            translationConsentRepository.setDisclaimerAccepted(true)
        }
    }

    fun translateMessage(messageId: String, content: String, targetLang: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(translatingMessageId = messageId) }
            val translated = translationRepository.translate(content, targetLang)
            if (!translated.isNullOrBlank()) {
                _uiState.update { state ->
                    state.copy(
                        translatedMessages = state.translatedMessages + (messageId to translated),
                        translatingMessageId = null
                    )
                }
                Log.d(TAG, "Çeviri tamamlandı: $translated")
            } else {
                errorReporter.breadcrumb(TAG, "Çeviri boş sonuç döndü: lang=$targetLang")
                _uiState.update { it.copy(translatingMessageId = null, errorMessage = "Çeviri yapılamadı") }
            }
        }
    }
}
