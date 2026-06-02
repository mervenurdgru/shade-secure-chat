package com.shade.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Inbox queue item — drained from `GET /api/v1/messages/inbox`.
 * [data] is a Base64-encoded [com.shade.app.proto.WebSocketMessage] (same bytes as a WS binary frame).
 */
data class InboxItemDto(
    @SerializedName("data") val data: String,
)

data class InboxResponse(
    @SerializedName("items") val items: List<InboxItemDto> = emptyList(),
    @SerializedName("count") val count: Int = 0,
    @SerializedName("has_more") val hasMore: Boolean = false,
)

data class ReceiptRequest(
    @SerializedName("message_id") val messageId: String,
    @SerializedName("status")     val status: String, // "READ"
)

data class BatchReceiptRequest(
    @SerializedName("receipts") val receipts: List<ReceiptRequest>,
)
