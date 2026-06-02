package com.shade.app.domain.model

/**
 * Wire-format wrapper for text messages that include a reply reference.
 *
 * When a message has a reply, the encrypted payload contains the JSON of this
 * class instead of a bare string.  Non-reply messages remain bare strings for
 * backwards compatibility.
 *
 * Fields intentionally kept short to reduce payload size.
 *   t   = actual message text
 *   rId = messageId of the quoted message
 *   rC  = short preview of the quoted message (≤ 80 chars)
 */
data class ReplyContent(
    val t: String,
    val rId: String? = null,
    val rC: String? = null
)
