package com.shade.app.data.remote.websocket

import android.util.Log
import com.shade.app.proto.WebSocketMessage
import com.shade.app.security.KeyVaultManager
import com.shade.app.util.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShadeWebSocketManagerImpl @Inject constructor(
    private val client: OkHttpClient,
    private val keyVaultManager: KeyVaultManager,
    private val connectivityObserver: ConnectivityObserver
) : ShadeWebSocketManager, WebSocketListener() {

    private var webSocket: WebSocket? = null
    private var lastUrl: String? = null
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.IO)
    private val TAG = "ShadeWS"

    private var retryCount = 0
    private val maxRetryCount = 5
    private val baseDelayMs = 1000L
    private var retryJob: Job? = null

    private val _messages = MutableSharedFlow<WebSocketMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    private fun observeConnectivity() {
        connectivityObserver.status.onEach { status ->
            Log.d(TAG, "Network state: $status")
            when (status) {
                ConnectivityObserver.Status.Available -> {
                    if (_connectionState.value == ConnectionState.ERROR ||
                        _connectionState.value == ConnectionState.DISCONNECTED) {
                        lastUrl?.let { reconnect(it) }
                    }
                }

                ConnectivityObserver.Status.Lost,
                ConnectivityObserver.Status.Unavailable -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
                else -> {}
            }
        }.launchIn(scope)
    }

    private fun reconnect(url: String) {
        retryCount = 0
        retryJob?.cancel()
        webSocket?.cancel()
        webSocket = null
        performConnect(url)
    }

    private fun performConnect(url: String) {
        scope.launch {
            val token = keyVaultManager.getAccessToken() ?: ""
            if (token.isEmpty()) {
                Log.e(TAG, "Connection cancelled: Token not found")
                return@launch
            }

            Log.d(TAG, "Connecting to WebSocket (token sent via header)")
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            _connectionState.value = ConnectionState.CONNECTING
            webSocket = client.newWebSocket(request, this@ShadeWebSocketManagerImpl)
        }
    }

    private fun scheduleRetry() {
        if (retryCount > maxRetryCount) {
            Log.w(TAG, "Reached to the maximum retry count ($maxRetryCount)")
            return
        }

        val delay = baseDelayMs * (1L shl retryCount.coerceAtMost(4))
        retryCount++

        Log.d(TAG, "Retry #$retryCount, will retry after $delay ms")

        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delay)
            lastUrl?.let { performConnect(it) }
        }
    }

    override fun connect(url: String) {
        if (_connectionState.value == ConnectionState.CONNECTED) return

        lastUrl = url
        retryCount = 0
        retryJob?.cancel()
        performConnect(url)
    }

    override fun disconnect() {
        Log.d(TAG, "Connection is closing...")
        retryJob?.cancel()
        retryCount = 0
        lastUrl = null
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun sendMessage(message: WebSocketMessage): Boolean {
        val bytes = message.toByteArray()
        val result = webSocket?.send(ByteString.of(*bytes)) ?: false
        Log.d(TAG, "Mesaj gönderildi mi?: $result")
        return result
    }

    override fun observeMessages() = _messages.asSharedFlow()
    override fun observeConnectionState() = _connectionState.asStateFlow()

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.i(TAG, "Bağlantı AÇILDI")
        _connectionState.value = ConnectionState.CONNECTED
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        try {
            Log.d(TAG, "Yeni Binary mesaj geldi, boyutu: ${bytes.size}")
            val protoMessage = WebSocketMessage.parseFrom(bytes.toByteArray())
            val emitted = _messages.tryEmit(protoMessage)
            Log.d(TAG, "Mesaj Flow'a aktarıldı mı?: $emitted")
        } catch (e: Exception) {
            Log.e(TAG, "Mesaj parse hatası: ${e.message}")
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.w(TAG, "Bağlantı Kapanıyor: $code / $reason")
        _connectionState.value = ConnectionState.DISCONNECTED
        this.webSocket = null

        if (code != 1000) {
            scheduleRetry()
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "WebSocket HATASI: ${t.message}", t)
        _connectionState.value = ConnectionState.ERROR
        this.webSocket = null
        scheduleRetry()
    }
}
