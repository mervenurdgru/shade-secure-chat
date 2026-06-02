package com.shade.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AuditLogItem(
    @SerializedName("action_type") val actionType: String,
    @SerializedName("ip_address") val ipAddress: String,
    @SerializedName("timestamp") val timestamp: String
)

data class AuditLogsResponse(
    @SerializedName("logs") val logs: List<AuditLogItem>
)
