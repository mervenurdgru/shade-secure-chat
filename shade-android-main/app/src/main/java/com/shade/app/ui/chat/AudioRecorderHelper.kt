package com.shade.app.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Basit ses kaydedici yardımcı sınıf.
 * Compose tarafında `remember { AudioRecorderHelper() }` ile kullanılır.
 */
class AudioRecorderHelper {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0L
    private var isStarted: Boolean = false   // çift stop/cancel koruması

    fun start(context: Context) {
        if (isStarted) return   // zaten kayıt var, tekrar başlatma
        val dir = File(context.cacheDir, "audio_temp").also { it.mkdirs() }
        outputFile = File(dir, "record_${System.currentTimeMillis()}.aac")
        startTime = System.currentTimeMillis()

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFile!!.absolutePath)
            try {
                prepare()
                start()
                isStarted = true
            } catch (e: Exception) {
                Log.e(TAG, "Kayıt başlatılamadı: ${e.message}")
                isStarted = false
            }
        }
    }

    /**
     * Kaydı durdurur ve dosyayı gönderim için döner.
     * @return Pair(dosya, süreMs). Hata durumunda dosya null, süre 0 döner.
     */
    fun stop(): Pair<File?, Long> {
        if (!isStarted) {
            Log.w(TAG, "stop() çağrıldı ama kayıt yok, atlandı")
            return Pair(null, 0L)
        }
        isStarted = false
        val duration = System.currentTimeMillis() - startTime
        return try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            if (duration > 300) Pair(outputFile, duration) else Pair(null, 0L)
        } catch (e: Exception) {
            Log.e(TAG, "Kayıt durdurulamadı: ${e.message}")
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            Pair(null, 0L)
        }
    }

    /**
     * Kaydı iptal eder — dosyayı silip hiçbir şey göndermez.
     */
    fun cancel() {
        if (!isStarted) {
            Log.w(TAG, "cancel() çağrıldı ama kayıt yok, atlandı")
            return
        }
        isStarted = false
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) { }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) { }
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
        Log.d(TAG, "Kayıt iptal edildi")
    }

    companion object {
        private const val TAG = "AudioRecorderHelper"
    }
}
