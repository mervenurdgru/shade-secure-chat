package com.shade.app.data.remote.api

import com.shade.app.data.remote.dto.AuditLogsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface AuditService {
    @GET("audit/me")
    suspend fun getMyLogs(
        @Header("Authorization") token: String
    ): Response<AuditLogsResponse>
}
