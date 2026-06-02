package com.shade.app.data.local.entities

import androidx.room.Entity

@Entity(
    tableName = "group_read_receipts",
    primaryKeys = ["messageId", "readerShadeId"],
)
data class GroupReadReceiptEntity(
    val messageId: String,
    val readerShadeId: String,
)
