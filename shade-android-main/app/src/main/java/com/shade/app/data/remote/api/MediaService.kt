package com.shade.app.data.remote.api

import com.shade.app.data.remote.dto.ImageUploadResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

interface MediaService {

    @Multipart
    @POST("media/upload")
    suspend fun uploadImage(
        @Header("Authorization") token: String,
        @Part image: MultipartBody.Part
    ): Response<ImageUploadResponse>

    @Multipart
    @POST("media/upload")
    suspend fun uploadFile(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<ImageUploadResponse>

    @GET("media/{imageId}")
    @Streaming
    suspend fun downloadImage(
        @Header("Authorization") token: String,
        @Path("imageId") imageId: String
    ): Response<ResponseBody>

    @GET("media/{fileId}")
    @Streaming
    suspend fun downloadFile(
        @Header("Authorization") token: String,
        @Path("fileId") fileId: String
    ): Response<ResponseBody>
}