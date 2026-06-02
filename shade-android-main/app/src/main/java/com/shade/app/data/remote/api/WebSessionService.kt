package com.shade.app.data.remote.api

import com.shade.app.data.remote.dto.AuthorizeWebSessionRequest
import com.shade.app.data.remote.dto.CreateWebSessionResponse
import com.shade.app.data.remote.dto.OkResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface WebSessionService {
    @POST("auth/web/session")
    suspend fun createSession(): Response<CreateWebSessionResponse>

    /**
     * 200 -> WebSessionAuthorizedResponse
     * 202 -> { "status": "pending" }
     * 404/410 -> empty body
     * Body raw olarak parse edilir; status code'a göre yorumlanır.
     */
    @GET("auth/web/session/{sessionId}")
    suspend fun getSession(
        @Path("sessionId") sessionId: String
    ): Response<ResponseBody>

    @POST("auth/web/session/{sessionId}/authorize")
    suspend fun authorizeSession(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Body request: AuthorizeWebSessionRequest
    ): Response<OkResponse>
}
