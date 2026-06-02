package com.shade.app.data.remote.api

import com.shade.app.data.remote.dto.KeysResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * Backend `/api/v1/keys/:id` endpoint — returns the encryption public key for
 * a given user UUID. Used for group flows where we only know the peer's
 * user_id (e.g. from a `GroupMembershipEvent`) and need the X25519 public key
 * to encrypt an SKDM.
 */
interface KeysService {

    @GET("keys/{id}")
    suspend fun getKeys(
        @Header("Authorization") token: String,
        @Path("id") userId: String,
    ): Response<KeysResponse>
}
