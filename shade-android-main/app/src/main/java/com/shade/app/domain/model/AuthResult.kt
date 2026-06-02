package com.shade.app.domain.model

data class AuthResult(
    val shadeId: String,
    val userId: String?,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val idPrivateKey: String? = null,
    val encPrivateKey: String? = null,
    val deviceId: String? = null
)
