package com.shade.app.data.remote.api

import com.shade.app.data.remote.dto.AddMemberRequest
import com.shade.app.data.remote.dto.CreateGroupRequest
import com.shade.app.data.remote.dto.CreateInviteRequest
import com.shade.app.data.remote.dto.GroupResponse
import com.shade.app.data.remote.dto.InviteResponse
import com.shade.app.data.remote.dto.RedeemInviteResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GroupService {

    @POST("groups/")
    suspend fun createGroup(
        @Header("Authorization") token: String,
        @Body request: CreateGroupRequest,
    ): Response<GroupResponse>

    @GET("groups/")
    suspend fun listGroups(
        @Header("Authorization") token: String,
    ): Response<List<GroupResponse>>

    @GET("groups/{id}")
    suspend fun getGroup(
        @Header("Authorization") token: String,
        @Path("id") groupId: String,
    ): Response<GroupResponse>

    @DELETE("groups/{id}")
    suspend fun deleteGroup(
        @Header("Authorization") token: String,
        @Path("id") groupId: String,
    ): Response<Unit>

    @POST("groups/{id}/members")
    suspend fun addMember(
        @Header("Authorization") token: String,
        @Path("id") groupId: String,
        @Body request: AddMemberRequest,
    ): Response<Unit>

    @DELETE("groups/{id}/members/{userId}")
    suspend fun removeMember(
        @Header("Authorization") token: String,
        @Path("id") groupId: String,
        @Path("userId") userId: String,
    ): Response<Unit>

    @POST("invites/")
    suspend fun createInvite(
        @Header("Authorization") token: String,
        @Body request: CreateInviteRequest,
    ): Response<InviteResponse>

    @GET("invites/{code}")
    suspend fun redeemInvite(
        @Header("Authorization") token: String,
        @Path("code") code: String,
    ): Response<RedeemInviteResponse>
}
