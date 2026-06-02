package com.shade.app.domain.repository

import com.shade.app.domain.model.WebSessionAuthorizationPayload
import com.shade.app.domain.model.WebSessionCreated
import com.shade.app.domain.model.WebSessionStatus

interface WebSessionRepository {
    suspend fun createWebSession(): Result<WebSessionCreated>
    suspend fun getWebSession(sessionId: String): Result<WebSessionStatus>
    suspend fun authorizeWebSession(
        token: String,
        sessionId: String,
        payload: WebSessionAuthorizationPayload
    ): Result<Boolean>
}
