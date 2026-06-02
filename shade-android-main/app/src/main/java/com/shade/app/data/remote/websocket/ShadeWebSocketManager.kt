package com.shade.app.data.remote.websocket

import com.shade.app.proto.WebSocketMessage
import kotlinx.coroutines.flow.Flow

interface ShadeWebSocketManager {
    fun connect(url: String)
    fun disconnect()
    fun sendMessage(message: WebSocketMessage): Boolean
    fun observeMessages(): Flow<WebSocketMessage>
    fun observeConnectionState(): Flow<ConnectionState>
}

enum class ConnectionState {
    CONNECTED, CONNECTING, DISCONNECTED, ERROR
}