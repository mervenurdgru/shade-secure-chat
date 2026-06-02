package com.shade.app.domain.usecase.message

import android.util.Base64
import android.util.Log
import com.shade.app.data.remote.api.MessageService
import com.shade.app.data.remote.websocket.IncomingWebSocketMessageHandler
import com.shade.app.proto.WebSocketMessage
import com.shade.app.security.KeyVaultManager
import javax.inject.Inject

/**
 * Drains the per-device RabbitMQ queue via `GET /messages/inbox`.
 * Each item is a Base64-encoded [WebSocketMessage] (identical to a WS binary frame).
 * Triggered by FCM wake-ups and on every WebSocket (re)connect.
 */
class FetchInboxUseCase @Inject constructor(
    private val messageService: MessageService,
    private val keyVaultManager: KeyVaultManager,
    private val incomingWebSocketMessageHandler: IncomingWebSocketMessageHandler,
) {
    suspend operator fun invoke(limit: Int = DEFAULT_LIMIT) {
        try {
            val token = keyVaultManager.getAccessToken() ?: return
            val safeLimit = limit.coerceIn(1, MAX_LIMIT)
            do {
                val response = messageService.getInbox("Bearer $token", safeLimit)

                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP ${response.code()}: ${response.message()}")
                    return
                }

                val body = response.body() ?: return
                Log.d(TAG, "Inbox batch: ${body.count} item(s), has_more=${body.hasMore}")

                if (body.items.isEmpty()) break

                for (item in body.items) {
                    try {
                        val bytes = Base64.decode(item.data, Base64.DEFAULT)
                        val wsMsg = WebSocketMessage.parseFrom(bytes)
                        incomingWebSocketMessageHandler.handle(wsMsg, sendPayloadAck = true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process inbox item: ${e.message}")
                    }
                }
            } while (body.hasMore)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch inbox: ${e.message}")
        }
    }

    private companion object {
        private const val TAG = "FetchInbox"
        private const val DEFAULT_LIMIT = 100
        private const val MAX_LIMIT = 500
    }
}
