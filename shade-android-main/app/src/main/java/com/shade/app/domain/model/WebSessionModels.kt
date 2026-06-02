package com.shade.app.domain.model

data class WebSessionCreated(
    val sessionId: String,
    val expiresAt: String
)

data class WebSessionAuthorizationPayload(
    val ciphertext: String,
    val nonce: String,
    val androidX25519Pub: String
)

sealed class WebSessionStatus {
    data object Pending : WebSessionStatus()
    data class Authorized(val payload: WebSessionAuthorizationPayload) : WebSessionStatus()
    data object NotFound : WebSessionStatus()
    data object Expired : WebSessionStatus()
    data class Error(val message: String) : WebSessionStatus()
}
