package com.shade.app.domain.model

data class FileMessageContent(
    val fileId: String,
    val fileNonceHex: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)
