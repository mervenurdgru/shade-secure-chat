package com.shade.app.data.remote.websocket

import android.util.Log
import com.shade.app.domain.usecase.group.HandleGroupKeyDistributionUseCase
import com.shade.app.domain.usecase.group.HandleGroupMembershipEventUseCase
import com.shade.app.domain.usecase.message.HandleIncomingReceiptUseCase
import com.shade.app.domain.usecase.message.ReceiveGroupMessageUseCase
import com.shade.app.domain.usecase.message.ReceiveMessageUseCase
import com.shade.app.proto.MessageAck
import com.shade.app.proto.WebSocketMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches a decoded [WebSocketMessage] to the appropriate use case.
 * Shared by the live WebSocket listener and inbox drain.
 */
@Singleton
class IncomingWebSocketMessageHandler @Inject constructor(
    private val receiveMessageUseCase: ReceiveMessageUseCase,
    private val receiveGroupMessageUseCase: ReceiveGroupMessageUseCase,
    private val handleIncomingReceiptUseCase: HandleIncomingReceiptUseCase,
    private val handleGroupKeyDistribution: HandleGroupKeyDistributionUseCase,
    private val handleGroupMembershipEvent: HandleGroupMembershipEventUseCase,
    private val webSocketManager: ShadeWebSocketManager,
) {
    suspend fun handle(wsMsg: WebSocketMessage, sendPayloadAck: Boolean = true) {
        when {
            wsMsg.hasPayload() -> handlePayload(wsMsg, sendPayloadAck)
            wsMsg.hasReceipt() -> {
                Log.d(TAG, "Receipt for ${wsMsg.receipt.messageId}")
                handleIncomingReceiptUseCase(wsMsg.receipt)
            }
            wsMsg.hasGkd() -> {
                Log.d(
                    TAG,
                    "SKDM in: group=${wsMsg.gkd.groupId} from=${wsMsg.gkd.senderUserId}"
                )
                handleGroupKeyDistribution(wsMsg.gkd)
            }
            wsMsg.hasGme() -> {
                Log.d(
                    TAG,
                    "GME in: group=${wsMsg.gme.groupId} kind=${wsMsg.gme.kind} " +
                            "subject=${wsMsg.gme.subjectId}"
                )
                handleGroupMembershipEvent(wsMsg.gme)
            }
            wsMsg.hasAck() -> Log.d(TAG, "Ignoring inbound ACK for ${wsMsg.ack.messageId}")
        }
    }

    private suspend fun handlePayload(wsMsg: WebSocketMessage, sendPayloadAck: Boolean) {
        val payload = wsMsg.payload
        val isGroup = payload.groupId.isNotEmpty()
        Log.d(TAG, "Payload in: id=${payload.messageId} group=$isGroup")

        if (isGroup) {
            receiveGroupMessageUseCase(payload)
        } else {
            receiveMessageUseCase(payload)
            if (sendPayloadAck) {
                val ack = WebSocketMessage.newBuilder()
                    .setAck(MessageAck.newBuilder().setMessageId(payload.messageId).build())
                    .build()
                webSocketManager.sendMessage(ack)
                Log.d(TAG, "ACK sent for: ${payload.messageId}")
            }
        }
    }

    private companion object {
        private const val TAG = "IncomingWsHandler"
    }
}
