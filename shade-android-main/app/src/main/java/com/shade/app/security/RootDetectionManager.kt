package com.shade.app.security

import android.content.Context
import android.os.Build
import android.util.Log
import com.shade.app.BuildConfig
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cihazın root'lu veya tamper edilmiş olup olmadığını tespit eder.
 *
 * Kontrol edilen durumlar:
 * 1. Yaygın `su` binary varlığı
 * 2. Test-keys ile imzalanmış build (AOSP / custom ROM)
 * 3. Yaygın root yönetim uygulamaları
 * 4. /system bölümünün yazılabilir olup olmadığı
 */
@Singleton
class RootDetectionManager @Inject constructor() {

    companion object {
        private const val TAG = "RootDetection"

        private val SU_PATHS = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/su/bin/su", "/vendor/bin/su", "/data/local/su",
            "/data/local/bin/su", "/data/local/xbin/su"
        )

        private val ROOT_PACKAGES = listOf(
            "com.topjohnwu.magisk",        // Magisk
            "com.noshufou.android.su",     // SuperUser
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",        // SuperSU
            "com.koushikdutta.superuser",  // ClockworkMod
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.kingroot.kinguser",       // KingRoot
            "com.kingo.android.root",      // KingoRoot
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot"
        )
    }

    /**
     * @return true — cihaz root'lu veya tamper'lı olabilir
     *
     * Debug build'lerde (emülatör / geliştirici cihazı) kontrol atlanır.
     */
    fun isDeviceRooted(context: Context): Boolean {
        if (BuildConfig.DEBUG) return false   // ← debug'da uyarı çıkmaz

        val checks = listOf(
            "su_binary" to ::checkSuBinaries,
            "test_keys" to ::checkTestKeys,
            "root_packages" to { checkRootPackages(context) },
            "writable_system" to ::checkWritableSystem
        )

        for ((name, check) in checks) {
            if (check()) {
                Log.w(TAG, "Root indicator found: $name")
                return true
            }
        }
        return false
    }

    /** /su binary'lerinden herhangi birinin varlığını kontrol et. */
    private fun checkSuBinaries(): Boolean =
        SU_PATHS.any { File(it).exists() }

    /** Build etiketlerinde 'test-keys' varsa custom ROM / root imzası. */
    private fun checkTestKeys(): Boolean =
        Build.TAGS?.contains("test-keys") == true

    /** Yaygın root yönetim paketlerini kontrol et. */
    private fun checkRootPackages(context: Context): Boolean {
        val pm = context.packageManager
        return ROOT_PACKAGES.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /** /system bölümünün RW mount edilip edilmediğini kontrol et. */
    private fun checkWritableSystem(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/mount"))
            val mountOutput = process.inputStream.bufferedReader().readText()
            process.destroy()
            mountOutput.contains("/system") && mountOutput.contains("rw,")
        } catch (_: Exception) {
            false
        }
    }
}
