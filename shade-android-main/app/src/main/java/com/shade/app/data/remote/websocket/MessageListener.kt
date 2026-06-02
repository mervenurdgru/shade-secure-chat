package com.shade.app.data.remote.websocket

import android.util.Log
import com.shade.app.BuildConfig
import com.shade.app.domain.repository.MessageRepository
import com.shade.app.domain.usecase.message.FetchInboxUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subscribes to [ShadeWebSocketManager] and forwards each [WebSocketMessage]
 * to [IncomingWebSocketMessageHandler]. Drains the HTTP inbox on connect.
 */
@Singleton
class MessageListener @Inject constructor(
    private val incomingWebSocketMessageHandler: IncomingWebSocketMessageHandler,
    private val fetchInboxUseCase: FetchInboxUseCase,
    private val messageRepository: MessageRepository,
    private val webSocketManager: ShadeWebSocketManager,
) {
    private var managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isListening: Boolean = false

    fun startListening() {
        if (isListening) return
        isListening = true

        webSocketManager.connect(BuildConfig.WS_URL)
        Log.d(TAG, "Listening to WebSocket ...")

        managerScope.launch { fetchInboxUseCase() }

        messageRepository.observeIncomingMessages()
            .onEach { wsMsg ->
                incomingWebSocketMessageHandler.handle(wsMsg, sendPayloadAck = true)
            }
            .launchIn(managerScope)
    }

    fun ensureConnected() {
        if (isListening) {
            webSocketManager.connect(BuildConfig.WS_URL)
            managerScope.launch { fetchInboxUseCase() }
        } else {
            startListening()
        }
    }

    fun stopListening() {
        isListening = false
        managerScope.cancel()
        managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private companion object {
        private const val TAG = "MessageManager"
    }
}
