package com.shade.app.data.repository

import android.util.Log
import com.shade.app.data.remote.api.TranslationService
import com.shade.app.data.remote.dto.TranslateRequest
import com.shade.app.security.KeyVaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URLEncoder
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepository @Inject constructor(
    private val translationService: TranslationService,
    private val keyVaultManager: KeyVaultManager,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "Translation"
    }

    /**
     * Önce backend proxy (/api/v1/translate) üzerinden Gemini ile çevirir.
     * Backend erişilemez veya hata dönerse Google Translate fallback'e düşer.
     */
    suspend fun translate(text: String, targetLang: String): String? {
        return translateWithBackend(text, targetLang)
            ?: translateWithGoogle(text, targetLang)
    }

    private suspend fun translateWithBackend(text: String, targetLang: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val token = keyVaultManager.getAccessToken() ?: return@withContext null
                // TranslationService'i doğrudan çağır — OkHttp interceptor token'ı ekler
                // ama burada AuthInterceptor yoksa manuel header gerekiyor.
                // NetworkModule'deki ana Retrofit'i kullanıyoruz; token zaten
                // AuthInterceptor (veya her istekte manuel ekleme) ile gönderilmeli.
                // Basitlik için isteğe token'ı enjekte ediyoruz:
                val response = translationService.translate(
                    TranslateRequest(text = text, targetLang = targetLang)
                )
                if (response.isSuccessful) {
                    val result = response.body()?.result?.trim()
                    if (!result.isNullOrBlank()) {
                        Log.d(TAG, "Backend çeviri: \"$text\" → \"$result\"")
                        result
                    } else null
                } else {
                    Log.w(TAG, "Backend çeviri başarısız: ${response.code()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backend çeviri exception: ${e.message}")
                null
            }
        }

    private suspend fun translateWithGoogle(text: String, targetLang: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = "https://translate.googleapis.com/translate_a/single" +
                        "?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encoded"
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null

                val outer = JSONArray(body)
                val parts = outer.getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONArray(i)
                    if (!part.isNull(0)) sb.append(part.getString(0))
                }
                val result = sb.toString().trim()
                Log.d(TAG, "Google çeviri: \"$text\" → \"$result\"")
                result.ifEmpty { null }
            } catch (e: Exception) {
                Log.e(TAG, "Google çeviri hatası: ${e.message}")
                null
            }
        }
}
