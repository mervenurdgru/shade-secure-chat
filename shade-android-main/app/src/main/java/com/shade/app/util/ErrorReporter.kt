package com.shade.app.util

import android.util.Log
import com.shade.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merkezi hata raporlama servisi.
 *
 * Şu an yapılandırılmış log kullanır. Crashlytics veya Sentry eklemek için
 * [report] metoduna aşağıdaki satırları eklemek yeterli:
 *
 * ```kotlin
 * // Crashlytics:
 * Firebase.crashlytics.recordException(throwable ?: RuntimeException(message))
 *
 * // Sentry:
 * Sentry.captureException(throwable ?: RuntimeException(message))
 * ```
 *
 * Dev ortamında hatalar Logcat'e yazılır; staging/prod'da sessizce raporlanır.
 */
@Singleton
class ErrorReporter @Inject constructor() {

    /**
     * Bir hatayı raporlar.
     *
     * @param tag     Log etiketi (genellikle sınıf adı)
     * @param message Bağlamsal açıklama
     * @param throwable Orijinal istisna (null olabilir)
     */
    fun report(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.STRICT_LOGGING) {
            // Dev: her şeyi göster
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        } else {
            // Staging/Prod: sessiz log + crash service
            Log.w(tag, "$message — ${throwable?.message}")
            // TODO: Firebase.crashlytics.recordException(throwable ?: RuntimeException(message))
        }
    }

    /** [AppError] için kısayol. */
    fun report(tag: String, error: AppError) {
        report(tag, error.message, error.cause ?: error)
    }

    /**
     * Non-fatal bilgi logu — hata değil ama izlenebilir.
     * Crash reporting servisine breadcrumb olarak gönderilebilir.
     */
    fun breadcrumb(tag: String, message: String) {
        if (BuildConfig.STRICT_LOGGING) {
            Log.d(tag, "[breadcrumb] $message")
        }
        // TODO: Firebase.crashlytics.log("$tag: $message")
    }
}
