package com.shade.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chatPrefsDataStore by preferencesDataStore(name = "chat_prefs")

@Singleton
class ChatPrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val BG_PREFIX = "bg_"
    }

    // ── Background color (stored as ARGB Int) ──────────────────────────────────
    fun getChatBackground(chatId: String): Flow<Int?> {
        val key = intPreferencesKey(BG_PREFIX + chatId)
        return context.chatPrefsDataStore.data.map { it[key] }
    }

    suspend fun setChatBackground(chatId: String, colorArgb: Int?) {
        val key = intPreferencesKey(BG_PREFIX + chatId)
        context.chatPrefsDataStore.edit { prefs ->
            if (colorArgb != null) prefs[key] = colorArgb else prefs.remove(key)
        }
    }
}
