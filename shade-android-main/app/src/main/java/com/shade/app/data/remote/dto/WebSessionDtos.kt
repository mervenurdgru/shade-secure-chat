package com.shade.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CreateWebSessionResponse(
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("expires_at")
    val expiresAt: String
)

data class WebSessionPendingResponse(
    val status: String
)

data class WebSessionAuthorizedResponse(
    val ciphertext: String,
    val nonce: String,
    @SerializedName("android_x25519_pub")
    val androidX25519Pub: String
)

data class AuthorizeWebSessionRequest(
    val ciphertext: String,
    val nonce: String,
    @SerializedName("android_x25519_pub")
    val androidX25519Pub: String
)

data class OkResponse(
    val ok: Boolean
)
