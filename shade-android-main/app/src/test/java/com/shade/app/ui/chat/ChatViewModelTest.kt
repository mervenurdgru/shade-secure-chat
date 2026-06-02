package com.shade.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.google.common.truth.Truth.assertThat
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.MessageStatus
import com.shade.app.data.local.entities.MessageType
import com.shade.app.data.preferences.TranslationConsentRepository
import com.shade.app.data.repository.ChatPrefsRepository
import com.shade.app.data.repository.TranslationRepository
import com.shade.app.domain.repository.ChatRepository
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.domain.repository.GroupRepository
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.domain.usecase.message.DeleteMessageForEveryoneUseCase
import com.shade.app.domain.usecase.message.DownloadFileUseCase
import com.shade.app.domain.usecase.message.DownloadImageUseCase
import com.shade.app.domain.usecase.message.EditMessageUseCase
import com.shade.app.domain.usecase.message.MarkChatAsReadUseCase
import com.shade.app.domain.usecase.message.SendAudioMessageUseCase
import com.shade.app.domain.usecase.message.SendFileMessageUseCase
import com.shade.app.domain.usecase.message.SendGroupImageMessageUseCase
import com.shade.app.domain.usecase.message.SendGroupMessageUseCase
import com.shade.app.domain.usecase.message.SendImageMessageUseCase
import com.shade.app.domain.usecase.message.SendMessageUseCase
import com.shade.app.security.KeyVaultManager
import com.shade.app.util.ActiveChatTracker
import com.shade.app.util.ErrorReporter
import com.shade.app.util.MainDispatcherRule
import com.shade.app.util.NotificationHelper
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.coWhenever
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ── Bağımlılıklar ─────────────────────────────────────────────────────────
    private val messageRepository: MessageRepository = mock()
    private val sendMessageUseCase: SendMessageUseCase = mock()
    private val sendGroupMessageUseCase: SendGroupMessageUseCase = mock()
    private val sendGroupImageMessageUseCase: SendGroupImageMessageUseCase = mock()
    private val sendImageMessageUseCase: SendImageMessageUseCase = mock()
    private val downloadImageUseCase: DownloadImageUseCase = mock()
    private val markChatAsReadUseCase: MarkChatAsReadUseCase = mock()
    private val deleteMessageForEveryoneUseCase: DeleteMessageForEveryoneUseCase = mock()
    private val editMessageUseCase: EditMessageUseCase = mock()
    private val sendAudioMessageUseCase: SendAudioMessageUseCase = mock()
    private val sendFileMessageUseCase: SendFileMessageUseCase = mock()
    private val downloadFileUseCase: DownloadFileUseCase = mock()
    private val chatRepository: ChatRepository = mock()
    private val groupRepository: GroupRepository = mock()
    private val contactRepository: ContactRepository = mock()
    private val keyVaultManager: KeyVaultManager = mock()
    private val translationRepository: TranslationRepository = mock()
    private val translationConsentRepository: TranslationConsentRepository = mock()
    private val activeChatTracker: ActiveChatTracker = mock()
    private val notificationHelper: NotificationHelper = mock()
    private val chatPrefsRepository: ChatPrefsRepository = mock()
    private val errorReporter: ErrorReporter = mock()

    private lateinit var viewModel: ChatViewModel

    companion object {
        private const val CHAT_ID = "chat_abc"
        private const val CHAT_NAME = "Alice"
        private const val MY_SHADE_ID = "me_123"
    }

    @Before
    fun setUp() = runBlocking {
        // Flow bağımlılıkları — init() içindeki observer'lar için gerekli
        whenever(messageRepository.getMessagesForChat(any(), any())).thenReturn(flowOf(emptyList()))
        whenever(messageRepository.getMessagesForChatPaged(any(), any())).thenReturn(flowOf(PagingData.empty()))
        whenever(chatRepository.observeChatWithContact(any())).thenReturn(flowOf(null))
        whenever(groupRepository.observeCachedGroup(any())).thenReturn(flowOf(null))
        whenever(chatPrefsRepository.getChatBackground(any())).thenReturn(flowOf(null))
        whenever(chatPrefsRepository.getAutoDeleteMinutes(any())).thenReturn(flowOf(0))
        whenever(translationConsentRepository.disclaimerAccepted).thenReturn(flowOf(false))

        // Suspend fonksiyonlar — runBlocking içinde stublanır
        whenever(keyVaultManager.getShadeId()).thenReturn(MY_SHADE_ID)
        coWhenever { contactRepository.getOrFetchContact(any()) }.thenReturn(null)
        coWhenever { chatRepository.alignChatRowFromGroupCache(any()) }.then { }
        val savedStateHandle = SavedStateHandle(
            mapOf("chatId" to CHAT_ID, "chatName" to CHAT_NAME)
        )
        viewModel = ChatViewModel(
            messageRepository                = messageRepository,
            sendMessageUseCase               = sendMessageUseCase,
            sendGroupMessageUseCase          = sendGroupMessageUseCase,
            sendGroupImageMessageUseCase     = sendGroupImageMessageUseCase,
            sendImageMessageUseCase          = sendImageMessageUseCase,
            downloadImageUseCase             = downloadImageUseCase,
            markChatAsReadUseCase            = markChatAsReadUseCase,
            deleteMessageForEveryoneUseCase  = deleteMessageForEveryoneUseCase,
            editMessageUseCase               = editMessageUseCase,
            sendAudioMessageUseCase          = sendAudioMessageUseCase,
            sendFileMessageUseCase           = sendFileMessageUseCase,
            downloadFileUseCase              = downloadFileUseCase,
            chatRepository                   = chatRepository,
            groupRepository                  = groupRepository,
            contactRepository                = contactRepository,
            keyVaultManager                  = keyVaultManager,
            translationRepository            = translationRepository,
            translationConsentRepository     = translationConsentRepository,
            activeChatTracker                = activeChatTracker,
            notificationHelper               = notificationHelper,
            chatPrefsRepository              = chatPrefsRepository,
            errorReporter                    = errorReporter,
            savedStateHandle                 = savedStateHandle
        )
    }

    // ── Başlangıç durumu ─────────────────────────────────────────────────────

    @Test
    fun `initial state carries chatId and chatName from SavedStateHandle`() {
        val state = viewModel.uiState.value
        assertThat(state.chatId).isEqualTo(CHAT_ID)
        assertThat(state.chatName).isEqualTo(CHAT_NAME)
    }

    @Test
    fun `init loads myShadeId from KeyVaultManager`() = runTest {
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.myShadeId).isEqualTo(MY_SHADE_ID)
    }

    // ── Hata yönetimi ─────────────────────────────────────────────────────────

    @Test
    fun `dismissError clears errorMessage in state`() = runTest {
        whenever(downloadImageUseCase.invoke(any(), any())).thenReturn(
            Result.failure(RuntimeException("disk full"))
        )
        viewModel.downloadImage(makeMessage("msg1"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage).isNotNull()

        viewModel.dismissError()

        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    // ── Düzenleme (edit) ──────────────────────────────────────────────────────

    @Test
    fun `startEditing sets editingMessage in state`() {
        val message = makeMessage("msg_edit")
        viewModel.startEditing(message)
        assertThat(viewModel.uiState.value.editingMessage).isEqualTo(message)
    }

    @Test
    fun `cancelEditing clears editingMessage`() {
        val message = makeMessage("msg_edit")
        viewModel.startEditing(message)
        viewModel.cancelEditing()
        assertThat(viewModel.uiState.value.editingMessage).isNull()
    }

    @Test
    fun `confirmEdit clears editingMessage immediately before use-case completes`() = runTest {
        whenever(editMessageUseCase.invoke(any(), any())).thenReturn(Result.success(Unit))
        val message = makeMessage("msg_edit")
        viewModel.startEditing(message)

        viewModel.confirmEdit("new content")

        // editingMessage state senkron olarak temizlenmeli
        assertThat(viewModel.uiState.value.editingMessage).isNull()
    }

    @Test
    fun `confirmEdit is no-op when no editing is active`() = runTest {
        viewModel.confirmEdit("orphan content")
        advanceUntilIdle()
        verify(editMessageUseCase, never()).invoke(any(), any())
    }

    // ── Yanıt (reply) ─────────────────────────────────────────────────────────

    @Test
    fun `startReply sets replyingToMessage`() {
        val message = makeMessage("reply_target")
        viewModel.startReply(message)
        assertThat(viewModel.uiState.value.replyingToMessage).isEqualTo(message)
    }

    @Test
    fun `cancelReply clears replyingToMessage`() {
        val message = makeMessage("reply_target")
        viewModel.startReply(message)
        viewModel.cancelReply()
        assertThat(viewModel.uiState.value.replyingToMessage).isNull()
    }

    // ── Arama ─────────────────────────────────────────────────────────────────

    @Test
    fun `toggleSearch activates search mode`() {
        assertThat(viewModel.uiState.value.isSearchActive).isFalse()
        viewModel.toggleSearch()
        assertThat(viewModel.uiState.value.isSearchActive).isTrue()
    }

    @Test
    fun `toggleSearch twice returns to inactive`() {
        viewModel.toggleSearch()
        viewModel.toggleSearch()
        assertThat(viewModel.uiState.value.isSearchActive).isFalse()
    }

    @Test
    fun `toggleSearch always resets query and results`() {
        viewModel.toggleSearch()
        viewModel.toggleSearch()
        val state = viewModel.uiState.value
        assertThat(state.searchQuery).isEmpty()
        assertThat(state.searchResults).isEmpty()
    }

    @Test
    fun `onSearchQueryChange with blank input clears results and skips repository`() = runTest {
        viewModel.onSearchQueryChange("  ")
        assertThat(viewModel.uiState.value.searchResults).isEmpty()
        verify(messageRepository, never()).searchMessages(any(), any(), any())
    }

    @Test
    fun `onSearchQueryChange with non-blank query triggers repository search`() = runTest {
        whenever(messageRepository.searchMessages(any(), any(), any())).thenReturn(flowOf(emptyList()))
        viewModel.onSearchQueryChange("hello")
        advanceUntilIdle()
        verify(messageRepository).searchMessages(CHAT_ID, "hello", false)
    }

    // ── Okunmamış bildirim ────────────────────────────────────────────────────

    @Test
    fun `clearUnreadNotification nullifies firstUnreadMessageId`() {
        viewModel.clearUnreadNotification()
        assertThat(viewModel.uiState.value.firstUnreadMessageId).isNull()
    }

    // ── Çeviri ────────────────────────────────────────────────────────────────

    @Test
    fun `translateMessage stores successful result in translatedMessages`() = runTest {
        whenever(translationRepository.translate("hello", "tr")).thenReturn("merhaba")

        viewModel.translateMessage("msg_1", "hello", "tr")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.translatedMessages["msg_1"]).isEqualTo("merhaba")
        assertThat(viewModel.uiState.value.translatingMessageId).isNull()
    }

    @Test
    fun `translateMessage with empty result sets errorMessage`() = runTest {
        whenever(translationRepository.translate(any(), any())).thenReturn(null)

        viewModel.translateMessage("msg_2", "text", "en")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage).isNotNull()
        assertThat(viewModel.uiState.value.translatingMessageId).isNull()
    }

    @Test
    fun `translateMessage clears translatingMessageId after completion`() = runTest {
        whenever(translationRepository.translate(any(), any())).thenReturn("result")

        viewModel.translateMessage("msg_3", "content", "de")
        advanceUntilIdle()

        // Tamamlandı — translatingMessageId temizlenmeli
        assertThat(viewModel.uiState.value.translatingMessageId).isNull()
        // Sonuç kaydedilmeli
        assertThat(viewModel.uiState.value.translatedMessages["msg_3"]).isEqualTo("result")
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private fun makeMessage(id: String) = MessageEntity(
        messageId   = id,
        senderId    = MY_SHADE_ID,
        receiverId  = CHAT_ID,
        messageType = MessageType.TEXT,
        content     = "test content",
        timestamp   = System.currentTimeMillis(),
        status      = MessageStatus.SENT,
        isDeleted   = false
    )
}
