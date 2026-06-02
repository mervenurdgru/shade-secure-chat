package com.shade.app.util

/**
 * Uygulama genelinde tutarlı hata modeli.
 *
 * Tüm Repository ve UseCase katmanları `Result<T>` yerine
 * `Result<T>` döndürürken hata olarak bu sealed sınıfı kullanır.
 *
 * UI katmanı hataları [toUserMessage] ile gösterilebilir metne dönüştürür.
 */
sealed class AppError(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    // ── Ağ hataları ──────────────────────────────────────────────────────────

    /** HTTP yanıtı 4xx/5xx döndü. */
    data class HttpError(val code: Int, val body: String = "") :
        AppError("HTTP $code: $body")

    /** Cihaz internet bağlantısı yok. */
    data object NoInternetError :
        AppError("İnternet bağlantısı yok")

    /** İstek zaman aşımına uğradı. */
    data object TimeoutError :
        AppError("İstek zaman aşımına uğradı")

    /** Sunucudan geçersiz / boş yanıt geldi. */
    data object EmptyResponseError :
        AppError("Sunucudan yanıt alınamadı")

    // ── Kimlik doğrulama hataları ─────────────────────────────────────────────

    /** JWT geçersiz veya süresi dolmuş. */
    data object UnauthorizedError :
        AppError("Oturum süresi doldu, lütfen tekrar giriş yapın")

    /** Refresh token geçersiz — kullanıcı yeniden giriş yapmalı. */
    data object SessionExpiredError :
        AppError("Oturum sona erdi, lütfen tekrar giriş yapın")

    // ── Şifreleme hataları ────────────────────────────────────────────────────

    /** Anahtar çözümleme veya şifre çözme başarısız. */
    data class CryptoError(val detail: String) :
        AppError("Şifreleme hatası: $detail")

    // ── Veritabanı hataları ───────────────────────────────────────────────────

    /** Room sorgusu veya işlemi başarısız. */
    data class DatabaseError(val detail: String) :
        AppError("Veritabanı hatası: $detail")

    // ── Genel / beklenmeyen hatalar ───────────────────────────────────────────

    /** Bilinmeyen / yakalanmamış istisna. */
    data class UnknownError(val throwable: Throwable) :
        AppError("Beklenmeyen hata: ${throwable.message}", throwable)
}

// ── Uzantı fonksiyonları ──────────────────────────────────────────────────────

/** Kullanıcıya gösterilecek kısa mesaj. */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.NoInternetError    -> "İnternet bağlantısı yok"
    is AppError.TimeoutError       -> "Bağlantı zaman aşımına uğradı"
    is AppError.UnauthorizedError  -> "Oturum süresi doldu"
    is AppError.SessionExpiredError-> "Lütfen tekrar giriş yapın"
    is AppError.HttpError          -> if (code in 500..599) "Sunucu hatası" else "İstek başarısız ($code)"
    is AppError.EmptyResponseError -> "Sunucudan yanıt alınamadı"
    is AppError.CryptoError        -> "Şifreleme hatası"
    is AppError.DatabaseError      -> "Veri okuma hatası"
    is AppError.UnknownError       -> "Beklenmeyen bir hata oluştu"
}

/** Herhangi bir [Throwable]'ı [AppError]'a dönüştür. */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is java.net.UnknownHostException,
    is java.net.ConnectException -> AppError.NoInternetError
    is java.net.SocketTimeoutException -> AppError.TimeoutError
    else -> AppError.UnknownError(this)
}
