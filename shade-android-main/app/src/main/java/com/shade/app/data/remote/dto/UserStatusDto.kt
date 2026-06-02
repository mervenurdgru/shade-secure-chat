package com.shade.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserStatusResponse(
    @SerializedName("shade_id") val shadeId: String,
    @SerializedName("last_active") val lastActive: String?, // ISO 8601
    @SerializedName("is_online") val isOnline: Boolean
)
