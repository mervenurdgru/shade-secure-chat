package com.shade.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.translationConsentDataStore by preferencesDataStore(name = "shade_translation_consent")

@Singleton
class TranslationConsentRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val acknowledgedKey = booleanPreferencesKey("translation_third_party_acknowledged")

    val disclaimerAccepted: Flow<Boolean> = context.translationConsentDataStore.data.map { prefs ->
        prefs[acknowledgedKey] == true
    }

    suspend fun setDisclaimerAccepted(accepted: Boolean = true) {
        context.translationConsentDataStore.edit { it[acknowledgedKey] = accepted }
    }
}
