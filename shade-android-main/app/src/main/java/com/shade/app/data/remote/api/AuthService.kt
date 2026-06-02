package com.shade.app.data.remote.api

import com.shade.app.data.remote.dto.LoginInitRequest
import com.shade.app.data.remote.dto.LoginInitResponse
import com.shade.app.data.remote.dto.LoginVerifyRequest
import com.shade.app.data.remote.dto.LoginVerifyResponse
import com.shade.app.data.remote.dto.RefreshRequest
import com.shade.app.data.remote.dto.RefreshResponse
import com.shade.app.data.remote.dto.RegisterRequest
import com.shade.app.data.remote.dto.RegisterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<RegisterResponse>

    @POST("auth/login/init")
    suspend fun loginInit(
        @Body request: LoginInitRequest
    ): Response<LoginInitResponse>

    @POST("auth/login/verify")
    suspend fun loginVerify(
        @Body request: LoginVerifyRequest
    ): Response<LoginVerifyResponse>

    @POST("auth/refresh")
    suspend fun refresh(
        @Body request: RefreshRequest
    ): Response<RefreshResponse>
}