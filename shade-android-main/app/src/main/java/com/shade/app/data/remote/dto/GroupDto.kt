package com.shade.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GroupMemberResponse(
    @SerializedName("user_id")  val userId: String,
    @SerializedName("shade_id") val shadeId: String,
    @SerializedName("role")     val role: String,
)

data class GroupResponse(
    @SerializedName("group_id")   val groupId: String,
    @SerializedName("name")       val name: String,
    @SerializedName("owner_id")   val ownerId: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("members")    val members: List<GroupMemberResponse>,
    @SerializedName("created_at") val createdAt: String,
)

data class CreateGroupRequest(
    @SerializedName("name")       val name: String,
    @SerializedName("member_ids") val memberIds: List<String>,
)

data class AddMemberRequest(
    @SerializedName("user_id") val userId: String,
)

data class CreateInviteRequest(
    @SerializedName("group_id")  val groupId: String? = null,
    @SerializedName("max_uses")  val maxUses: Int = 1,
)

data class InviteResponse(
    @SerializedName("code")       val code: String,
    @SerializedName("group_id")   val groupId: String?,
    @SerializedName("max_uses")   val maxUses: Int,
    @SerializedName("use_count")  val useCount: Int,
    @SerializedName("expires_at") val expiresAt: String?,
)

data class RedeemInviteResponse(
    @SerializedName("type")    val type: String,          // "contact" | "group"
    @SerializedName("contact") val contact: LookupResponse?,
    @SerializedName("group")   val group: GroupResponse?,
)
