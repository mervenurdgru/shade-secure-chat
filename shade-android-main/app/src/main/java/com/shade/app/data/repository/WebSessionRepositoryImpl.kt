package com.shade.app.data.repository

import com.google.gson.Gson
import com.shade.app.data.remote.api.WebSessionService
import com.shade.app.data.remote.dto.AuthorizeWebSessionRequest
import com.shade.app.data.remote.dto.WebSessionAuthorizedResponse
import com.shade.app.domain.model.WebSessionAuthorizationPayload
import com.shade.app.domain.model.WebSessionCreated
import com.shade.app.domain.model.WebSessionStatus
import com.shade.app.domain.repository.WebSessionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSessionRepositoryImpl @Inject constructor(
    private val service: WebSessionService
) : WebSessionRepository {

    private val gson = Gson()

    override suspend fun createWebSession(): Result<WebSessionCreated> = runCatching {
        val response = service.createSession()
        if (!response.isSuccessful || response.body() == null) {
            error("createSession failed: ${response.code()}")
        }
        val body = response.body()!!
        WebSessionCreated(sessionId = body.sessionId, expiresAt = body.expiresAt)
    }

    override suspend fun getWebSession(sessionId: String): Result<WebSessionStatus> = runCatching {
        val response = service.getSession(sessionId)
        try {
            when (response.code()) {
                200 -> {
                    val raw = response.body()?.string()
                        ?: error("Empty body for authorized session")
                    val parsed = gson.fromJson(raw, WebSessionAuthorizedResponse::class.java)
                    WebSessionStatus.Authorized(
                        WebSessionAuthorizationPayload(
                            ciphertext = parsed.ciphertext,
                            nonce = parsed.nonce,
                            androidX25519Pub = parsed.androidX25519Pub
                        )
                    )
                }
                202 -> WebSessionStatus.Pending
                404 -> WebSessionStatus.NotFound
                410 -> WebSessionStatus.Expired
                else -> WebSessionStatus.Error("Unexpected status: ${response.code()}")
            }
        } finally {
            response.errorBody()?.close()
        }
    }

    override suspend fun authorizeWebSession(
        token: String,
        sessionId: String,
        payload: WebSessionAuthorizationPayload
    ): Result<Boolean> = runCatching {
        val response = service.authorizeSession(
            token = if (token.startsWith("Bearer ")) token else "Bearer $token",
            sessionId = sessionId,
            request = AuthorizeWebSessionRequest(
                ciphertext = payload.ciphertext,
                nonce = payload.nonce,
                androidX25519Pub = payload.androidX25519Pub
            )
        )
        when (response.code()) {
            200 -> response.body()?.ok ?: true
            404 -> error("Session not found")
            409 -> error("Session already authorized")
            410 -> error("Session expired")
            422 -> error("Validation error")
            else -> error("Authorize failed: ${response.code()}")
        }
    }
}
