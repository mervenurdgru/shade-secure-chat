package com.shade.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// Backend proxy (/api/v1/translate) request & response
data class TranslateRequest(
    @SerializedName("text") val text: String,
    @SerializedName("target_lang") val targetLang: String
)

data class TranslateResponse(
    @SerializedName("result") val result: String
)
