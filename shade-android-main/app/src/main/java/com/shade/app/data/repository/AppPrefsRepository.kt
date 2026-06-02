package com.shade.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appPrefsDataStore by preferencesDataStore(name = "app_prefs")

@Singleton
class AppPrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val DARK_THEME = booleanPreferencesKey("dark_theme")
        private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val APP_LOCK_PIN_HASH = stringPreferencesKey("app_lock_pin_hash")
        private val DISPLAY_NAME = stringPreferencesKey("display_name")
        private val PROFILE_PHOTO_PATH = stringPreferencesKey("profile_photo_path")
    }

    // ── Theme ──────────────────────────────────────────────────────────────────
    val isDarkTheme: Flow<Boolean> = context.appPrefsDataStore.data
        .map { it[DARK_THEME] ?: true }

    suspend fun setDarkTheme(dark: Boolean) {
        context.appPrefsDataStore.edit { it[DARK_THEME] = dark }
    }

    // ── App Lock ───────────────────────────────────────────────────────────────
    val isAppLockEnabled: Flow<Boolean> = context.appPrefsDataStore.data
        .map { it[APP_LOCK_ENABLED] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.appPrefsDataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    }

    /** Stores a simple hash of the 4-digit PIN (not cryptographically strong,
     *  but sufficient for a convenience lock on a private app). */
    suspend fun setPin(pin: String) {
        val hash = pin.fold(0L) { acc, c -> acc * 31 + c.code }.toString()
        context.appPrefsDataStore.edit { it[APP_LOCK_PIN_HASH] = hash }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val hash = pin.fold(0L) { acc, c -> acc * 31 + c.code }.toString()
        return context.appPrefsDataStore.data
            .map { it[APP_LOCK_PIN_HASH] == hash }
            .first()
    }

    /** Returns true if a PIN has been configured. */
    suspend fun hasPin(): Boolean =
        context.appPrefsDataStore.data.map { it[APP_LOCK_PIN_HASH] != null }.first()

    // ── Display Name ───────────────────────────────────────────────────────────
    val displayName: Flow<String> = context.appPrefsDataStore.data
        .map { it[DISPLAY_NAME] ?: "" }

    suspend fun setDisplayName(name: String) {
        context.appPrefsDataStore.edit { it[DISPLAY_NAME] = name }
    }

    // ── Profile Photo ──────────────────────────────────────────────────────────
    val profilePhotoPath: Flow<String?> = context.appPrefsDataStore.data
        .map { it[PROFILE_PHOTO_PATH] }

    suspend fun setProfilePhotoPath(path: String?) {
        context.appPrefsDataStore.edit {
            if (path != null) it[PROFILE_PHOTO_PATH] = path
            else it.remove(PROFILE_PHOTO_PATH)
        }
    }

    suspend fun removePin() {
        context.appPrefsDataStore.edit {
            it.remove(APP_LOCK_PIN_HASH)
            it[APP_LOCK_ENABLED] = false
        }
    }
}
