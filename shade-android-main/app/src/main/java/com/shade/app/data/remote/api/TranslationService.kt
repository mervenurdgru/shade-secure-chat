package com.shade.app.data.remote.api

import com.shade.app.data.remote.dto.TranslateRequest
import com.shade.app.data.remote.dto.TranslateResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface TranslationService {
    @POST("translate")
    suspend fun translate(
        @Body request: TranslateRequest
    ): Response<TranslateResponse>
}
