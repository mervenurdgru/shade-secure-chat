package com.shade.app.ui.webpairing

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.shade.app.crypto.WebPairingCryptoManager
import com.shade.app.data.remote.websocket.WebSyncSocketManager
import com.shade.app.domain.model.WebSessionAuthorizationPayload
import com.shade.app.domain.repository.WebSessionRepository
import com.shade.app.domain.usecase.websession.SyncWebSessionUseCase
import com.shade.app.R
import com.shade.app.security.KeyVaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WebPairingUiState {
    data object Idle : WebPairingUiState
    data object Authorizing : WebPairingUiState
    data object Connecting : WebPairingUiState
    data object Syncing : WebPairingUiState
    data object Connected : WebPairingUiState
    data class Error(val message: String) : WebPairingUiState
}

@HiltViewModel
class WebPairingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: WebSessionRepository,
    private val crypto: WebPairingCryptoManager,
    private val keyVault: KeyVaultManager,
    private val syncSocketManager: WebSyncSocketManager,
    private val syncWebSessionUseCase: SyncWebSessionUseCase
) : ViewModel() {

    private val gson = Gson()

    private val _state = MutableStateFlow<WebPairingUiState>(WebPairingUiState.Idle)
    val state: StateFlow<WebPairingUiState> = _state.asStateFlow()

    @Volatile private var syncFired: Boolean = false

    private var authorizeJob: Job? = null
    private var syncPipelineJob: Job? = null

    init {
        viewModelScope.launch {
            syncSocketManager.state.collect { s ->
                val prev = _state.value
                val next = when (s) {
                    WebSyncSocketManager.State.Connected -> when (val cur = prev) {
                        is WebPairingUiState.Error -> cur
                        WebPairingUiState.Connected -> cur
                        else -> WebPairingUiState.Syncing
                    }
                    WebSyncSocketManager.State.Connecting -> when (val cur = prev) {
                        is WebPairingUiState.Error -> cur
                        else -> WebPairingUiState.Connecting
                    }
                    WebSyncSocketManager.State.Closed ->
                        when (val cur = prev) {
                            WebPairingUiState.Connected -> cur
                            WebPairingUiState.Syncing ->
                                WebPairingUiState.Error(
                                    appContext.getString(R.string.pairing_sync_interrupted)
                                )
                            else -> cur
                        }
                    is WebSyncSocketManager.State.Failed ->
                        when (prev) {
                            WebPairingUiState.Connecting,
                            WebPairingUiState.Syncing ->
                                WebPairingUiState.Error(s.reason)

                            else -> prev
                        }
                    WebSyncSocketManager.State.Idle -> prev
                }
                _state.value = next

                if (s == WebSyncSocketManager.State.Connected &&
                    !syncFired &&
                    next == WebPairingUiState.Syncing
                ) {
                    syncFired = true
                    syncPipelineJob?.cancel()
                    syncPipelineJob = viewModelScope.launch {
                        try {
                            val result = try {
                                syncWebSessionUseCase()
                            } catch (e: CancellationException) {
                                throw e
                            }
                            result.onFailure { e ->
                                Log.e(TAG, "Sync failed", e)
                                if (_state.value is WebPairingUiState.Syncing) {
                                    _state.value = WebPairingUiState.Error(
                                        e.message ?: "Sync hatası"
                                    )
                                }
                            }.onSuccess { count ->
                                Log.d(TAG, "Sync OK total=$count messages")
                                if (_state.value is WebPairingUiState.Syncing) {
                                    _state.value = WebPairingUiState.Connected
                                    syncSocketManager.disconnect()
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        }
                    }
                }
                if (s is WebSyncSocketManager.State.Failed ||
                    s == WebSyncSocketManager.State.Closed ||
                    s == WebSyncSocketManager.State.Idle
                ) {
                    syncFired = false
                }
            }
        }
    }

    fun cancelPairingAttempt() {
        if (_state.value !is WebPairingUiState.Authorizing &&
            _state.value !is WebPairingUiState.Connecting &&
            _state.value !is WebPairingUiState.Syncing
        ) return

        authorizeJob?.cancel()
        syncPipelineJob?.cancel()
        authorizeJob = null
        syncPipelineJob = null
        syncSocketManager.disconnect()
        crypto.clearSession()
        _state.value = WebPairingUiState.Idle
    }

    fun onQrScanned(rawQr: String) {
        if (_state.value is WebPairingUiState.Authorizing ||
            _state.value is WebPairingUiState.Connecting ||
            _state.value is WebPairingUiState.Syncing ||
            _state.value is WebPairingUiState.Connected
        ) return

        Log.d(TAG, "QR raw (len=${rawQr.length}): ${rawQr.take(256)}")

        val qr = parseQr(rawQr)
        if (qr == null) {
            Log.w(TAG, "QR parse başarısız")
            _state.value = WebPairingUiState.Error("Geçersiz QR")
            return
        }
        Log.d(TAG, "QR parsed: s=${qr.s.take(8)}…  k.len=${qr.k.length}")

        authorizeJob?.cancel()
        authorizeJob = viewModelScope.launch {
            try {
                _state.value = WebPairingUiState.Authorizing

                val bundle = runCatching { buildEncryptedBundle(qr.k) }.getOrElse { e ->
                    Log.e(TAG, "Encrypt error", e)
                    if (_state.value is WebPairingUiState.Authorizing) {
                        _state.value = WebPairingUiState.Error(e.message ?: "Şifreleme hatası")
                    }
                    return@launch
                }

                if (_state.value != WebPairingUiState.Authorizing) return@launch

                Log.d(TAG, "Authorize request:")
                Log.d(TAG, "  POST /auth/web/session/${qr.s}/authorize")
                Log.d(TAG, "  web_x25519_pub (QR):  ${qr.k}  (len=${qr.k.length})")
                Log.d(TAG, "  android_x25519_pub:   ${bundle.androidPublicKeyHex}  (len=${bundle.androidPublicKeyHex.length})")
                Log.d(TAG, "  nonce:                ${bundle.nonceHex}  (len=${bundle.nonceHex.length})")
                Log.d(TAG, "  ciphertext:           ${bundle.ciphertextHex}  (len=${bundle.ciphertextHex.length})")

                val jwt = keyVault.getAccessToken() ?: ""
                val authResult = repository.authorizeWebSession(
                    token = jwt,
                    sessionId = qr.s,
                    payload = WebSessionAuthorizationPayload(
                        ciphertext = bundle.ciphertextHex,
                        nonce = bundle.nonceHex,
                        androidX25519Pub = bundle.androidPublicKeyHex
                    )
                )

                authResult.onFailure { e ->
                    Log.e(TAG, "Authorize error", e)
                    if (_state.value is WebPairingUiState.Authorizing) {
                        _state.value = WebPairingUiState.Error(e.message ?: "Yetkilendirilemedi")
                    }
                }.onSuccess {
                    if (_state.value != WebPairingUiState.Authorizing) return@onSuccess
                    Log.i(TAG, "▶ QR_SESSION_ID parsed from QR: \"${qr.s}\"")
                    Log.d(TAG, "Authorize OK → opening sync WS")
                    _state.value = WebPairingUiState.Connecting
                    syncSocketManager.connect(qr.s)
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun disconnect() {
        authorizeJob?.cancel()
        syncPipelineJob?.cancel()
        authorizeJob = null
        syncPipelineJob = null
        syncSocketManager.disconnect()
        crypto.clearSession()
        _state.value = WebPairingUiState.Idle
    }

    fun reset() {
        if (_state.value is WebPairingUiState.Error) {
            _state.value = WebPairingUiState.Idle
        }
    }

    private suspend fun buildEncryptedBundle(webPubHex: String): WebPairingCryptoManager.HandshakeResult {
        val ed = keyVault.getEd25519PrivateKey() ?: error("ed25519_priv missing")
        val x = keyVault.getX25519PrivateKey() ?: error("x25519_priv missing")
        val shadeId = keyVault.getShadeId() ?: error("shade_id missing")
        val userId = keyVault.getUserId() ?: error("user_id missing")

        val plaintextJson = gson.toJson(
            WebSessionPlaintext(
                x25519Priv = x,
                ed25519Priv = ed,
                shadeId = shadeId,
                userId = userId
            )
        )
        Log.d(TAG, "Plaintext JSON (will be encrypted): $plaintextJson")
        return crypto.startSession(plaintextJson.toByteArray(Charsets.UTF_8), webPubHex)
    }

    /**
     * Aşağıdaki formatların hepsini kabul eder:
     *  1) JSON: {"s":"<uuid>","k":"<hex>"}        (boşluksuz veya boşluklu)
     *  2) URL query: ?s=<uuid>&k=<hex>            (full URL veya çıplak)
     *  3) URL path + query: https://x.y/web/<uuid>?k=<hex>
     *  4) URL fragment: https://x.y/web/<uuid>#<hex>
     *  5) Concat: "<uuid>:<hex>" veya "<uuid>|<hex>"
     */
    private fun parseQr(raw: String): QrPayload? {
        val trimmed = raw.trim()

        tryParseJson(trimmed)?.let { return it }
        tryParseUrl(trimmed)?.let { return it }
        tryParseConcat(trimmed)?.let { return it }

        return null
    }

    private fun tryParseJson(raw: String): QrPayload? {
        if (!raw.startsWith("{")) return null
        return try {
            gson.fromJson(raw, QrPayload::class.java)
                ?.takeIf { it.s.isNotBlank() && it.k.isNotBlank() }
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun tryParseUrl(raw: String): QrPayload? {
        val queryStart = raw.indexOf('?')
        val fragmentStart = raw.indexOf('#')

        val params = mutableMapOf<String, String>()
        var sFromPath: String? = null
        val uuidRegex = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )

        if (queryStart >= 0) {
            val pathPart = raw.substring(0, queryStart)
            val queryEnd = if (fragmentStart > queryStart) fragmentStart else raw.length
            val query = raw.substring(queryStart + 1, queryEnd)
            query.split('&').forEach { kv ->
                val eq = kv.indexOf('=')
                if (eq > 0) params[kv.substring(0, eq)] = kv.substring(eq + 1)
            }
            val lastSeg = pathPart.trimEnd('/').substringAfterLast('/', "")
            if (uuidRegex.matches(lastSeg)) sFromPath = lastSeg
        } else if (fragmentStart >= 0) {
            val pathPart = raw.substring(0, fragmentStart)
            val frag = raw.substring(fragmentStart + 1)
            val lastSeg = pathPart.trimEnd('/').substringAfterLast('/', "")
            if (uuidRegex.matches(lastSeg)) {
                sFromPath = lastSeg
                if (frag.isNotBlank()) params["k"] = frag
            }
        } else {
            return null
        }

        val s = params["s"] ?: params["sid"] ?: params["session_id"] ?: sFromPath
        val k = params["k"] ?: params["wpk"] ?: params["pub"]
        if (s.isNullOrBlank() || k.isNullOrBlank()) return null
        return QrPayload(s = s, k = k)
    }

    private fun tryParseConcat(raw: String): QrPayload? {
        val sep = raw.indexOfAny(charArrayOf(':', '|', ';', ','))
        if (sep <= 0) return null
        val s = raw.substring(0, sep).trim()
        val k = raw.substring(sep + 1).trim()
        val uuidRegex = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
        if (!uuidRegex.matches(s) || k.isBlank()) return null
        return QrPayload(s = s, k = k)
    }

    private data class QrPayload(val s: String = "", val k: String = "")

    private data class WebSessionPlaintext(
        @SerializedName("x25519_priv") val x25519Priv: String,
        @SerializedName("ed25519_priv") val ed25519Priv: String,
        @SerializedName("shade_id") val shadeId: String,
        @SerializedName("user_id") val userId: String
    )

    private companion object {
        const val TAG = "WebPairingVM"
    }
}
