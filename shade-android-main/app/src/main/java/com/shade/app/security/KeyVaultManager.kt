package com.shade.app.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "shade_vault")

@Singleton
class KeyVaultManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager
) {

    private object Keys {
        val ED25519_PRIVATE_KEY = stringPreferencesKey("ED25519_PRIVATE_KEY")
        val X25519_PRIVATE_KEY = stringPreferencesKey("X25519_PRIVATE_KEY")
        val JWT_ACCESS_TOKEN = stringPreferencesKey("JWT_ACCESS_TOKEN")
        val JWT_REFRESH_TOKEN = stringPreferencesKey("JWT_REFRESH_TOKEN")
        val SHADE_ID = stringPreferencesKey("SHADE_ID")
        val USER_ID = stringPreferencesKey("USER_ID")
        val DEVICE_ID = stringPreferencesKey("DEVICE_ID")
        val FCM_TOKEN = stringPreferencesKey("FCM_TOKEN")
    }

    private suspend fun saveValue(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        val encryptedValue = cryptoManager.encrypt(value)
        context.dataStore.edit { preferences ->
            preferences[key] = encryptedValue
        }
    }

    private suspend fun getValue(key: androidx.datastore.preferences.core.Preferences.Key<String>): String? {
        return context.dataStore.data.map { preferences ->
            preferences[key]?.let { encryptedValue ->
                try {
                    cryptoManager.decrypt(encryptedValue)
                } catch (e: Exception) {
                    null
                }
            }
        }.first()
    }

    suspend fun saveEd25519PrivateKey(privateKeyHex: String) = saveValue(Keys.ED25519_PRIVATE_KEY, privateKeyHex)
    suspend fun getEd25519PrivateKey(): String? = getValue(Keys.ED25519_PRIVATE_KEY)

    suspend fun saveX25519PrivateKey(privateKeyHex: String) = saveValue(Keys.X25519_PRIVATE_KEY, privateKeyHex)
    suspend fun getX25519PrivateKey(): String? = getValue(Keys.X25519_PRIVATE_KEY)

    suspend fun saveAccessToken(token: String) = saveValue(Keys.JWT_ACCESS_TOKEN, token)
    suspend fun getAccessToken(): String? = getValue(Keys.JWT_ACCESS_TOKEN)

    suspend fun saveRefreshToken(token: String) = saveValue(Keys.JWT_REFRESH_TOKEN, token)
    suspend fun getRefreshToken(): String? = getValue(Keys.JWT_REFRESH_TOKEN)

    /** DataStore + decrypt olmadan — sadece anahtar var mı (açılış rotası için). */
    suspend fun hasStoredAccessToken(): Boolean =
        context.dataStore.data.map { it[Keys.JWT_ACCESS_TOKEN] != null }.first()

    suspend fun saveShadeId(shadeId: String) = saveValue(Keys.SHADE_ID, shadeId)
    suspend fun getShadeId(): String? = getValue(Keys.SHADE_ID)

    suspend fun saveUserId(userId: String) = saveValue(Keys.USER_ID, userId)
    suspend fun getUserId(): String? = getValue(Keys.USER_ID)

    suspend fun saveFcmToken(token: String) = saveValue(Keys.FCM_TOKEN, token)
    suspend fun getFcmToken(): String? = getValue(Keys.FCM_TOKEN)

    suspend fun saveDeviceId(deviceId: String) = saveValue(Keys.DEVICE_ID, deviceId)
    suspend fun getDeviceId(): String? = getValue(Keys.DEVICE_ID)
    suspend fun clearVault() {
        context.dataStore.edit { it.clear() }
    }
}
