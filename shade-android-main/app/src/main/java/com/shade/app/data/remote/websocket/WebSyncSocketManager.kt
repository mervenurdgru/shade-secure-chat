package com.shade.app.data.remote.websocket

import android.util.Log
import com.shade.app.BuildConfig
import com.shade.app.security.KeyVaultManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Browser ↔ Mobile sync WebSocket'i için yönetici.
 *
 * Endpoint: WS `/sync/:session_id?token=<jwt>&role=android`
 *
 * Sync payload’ları **text** JSON olarak gönderilir (`groups_snapshot` isteğe bağlı,
 * `batch`, `sync_complete`).
 *
 * Singleton; UI life-cycle'ından bağımsız yaşar. Authorize başarılı olduktan
 * sonra connect() çağrılır, kullanıcı manuel disconnect edene kadar veya
 * server tarafı session'ı düşürene kadar açık kalır.
 */
@Singleton
class WebSyncSocketManager @Inject constructor(
    private val client: OkHttpClient,
    private val keyVaultManager: KeyVaultManager
) {

    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data object Connected : State
        data object Closed : State
        data class Failed(val reason: String, val code: Int? = null) : State
    }

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.IO)

    private var socket: WebSocket? = null
    private var currentSessionId: String? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state = _state.asStateFlow()

    fun connect(sessionId: String) {
        if (currentSessionId == sessionId &&
            (_state.value == State.Connected || _state.value == State.Connecting)
        ) return

        currentSessionId = sessionId
        scope.launch {
            val token = keyVaultManager.getAccessToken()
            if (token.isNullOrBlank()) {
                _state.value = State.Failed("Missing JWT")
                return@launch
            }

            val baseWs = BuildConfig.WS_URL.trimEnd('/')
            val url = "$baseWs/sync/$sessionId?token=$token&role=android"

            Log.i(TAG, "▶ SYNC_SESSION_ID Android sends: \"$sessionId\"")
            Log.i(TAG, "▶ SYNC_URL full: $url")
            Log.d(TAG, "Connecting sync WS → $url")
            _state.value = State.Connecting

            socket?.cancel()
            val req = Request.Builder().url(url).build()
            socket = client.newWebSocket(req, listener)
        }
    }

    fun sendText(text: String): Boolean =
        socket?.send(text) ?: false

    fun disconnect() {
        socket?.close(1000, "client_disconnect")
        socket = null
        currentSessionId = null
        _state.value = State.Idle
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            Log.d(TAG, "Sync WS open  http=${response.code}  url=${ws.request().url}")
            _state.value = State.Connected
        }

        override fun onMessage(ws: WebSocket, bytes: ByteString) {
            Log.d(TAG, "Sync WS bin recv: ${bytes.size}B (ignored)")
        }

        override fun onMessage(ws: WebSocket, text: String) {
            Log.d(TAG, "Sync WS txt recv: ${text.take(200)}")
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "Sync WS closing: code=$code reason=$reason  url=${ws.request().url}")
            ws.close(code, reason)
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "Sync WS closed:  code=$code reason=$reason")
            socket = null
            currentSessionId = null
            if (_state.value == State.Idle) return
            _state.value = if (code == 1000) State.Closed else State.Failed(reason, code)
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Log.e(
                TAG,
                "Sync WS failure  url=${ws.request().url}  http=${response?.code}  msg=${response?.message}",
                t
            )
            socket = null
            currentSessionId = null
            if (_state.value == State.Idle) return
            _state.value = State.Failed(t.message ?: "Unknown")
        }
    }

    private companion object {
        const val TAG = "ShadeWebSync"
    }
}
