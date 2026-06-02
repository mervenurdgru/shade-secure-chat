package com.shade.app.ui.qr

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.shade.app.crypto.WebPairingCryptoManager
import com.shade.app.data.remote.api.WebSessionService
import com.shade.app.data.remote.dto.AuthorizeWebSessionRequest
import com.shade.app.security.KeyVaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class QrScannerUiState {
    object Idle    : QrScannerUiState()
    object Loading : QrScannerUiState()
    object Success : QrScannerUiState()
    data class Error(val message: String) : QrScannerUiState()
}

@HiltViewModel
class QrScannerViewModel @Inject constructor(
    private val keyVaultManager: KeyVaultManager,
    private val webSessionService: WebSessionService,
    private val webPairingCryptoManager: WebPairingCryptoManager
) : ViewModel() {

    private val gson = Gson()

    private val _uiState = MutableStateFlow<QrScannerUiState>(QrScannerUiState.Idle)
    val uiState = _uiState.asStateFlow()

    /**
     * QR içeriğini işle.
     * Format: "shade://web-auth?s=<sessionId>&k=<webPublicKeyHex>"
     * Örnek:  "shade://web-auth?s=abc123&k=04af92..."
     */
    fun processScannedQr(content: String) {
        Log.d("QrScanner", "Taranan QR: $content")

        if (!content.startsWith("shade://web-auth")) {
            _uiState.value = QrScannerUiState.Error(
                "Geçersiz QR kodu.\nBu bir Shade Web QR'ı değil."
            )
            return
        }

        // Parse query params: ?s=<sessionId>&k=<pubKeyHex>
        val queryStart = content.indexOf('?')
        if (queryStart < 0) {
            _uiState.value = QrScannerUiState.Error("QR kodu eksik bilgi içeriyor.")
            return
        }
        val queryParts = content.substring(queryStart + 1)
            .split('&')
            .associate { part ->
                val eq = part.indexOf('=')
                if (eq < 0) part to "" else part.substring(0, eq) to part.substring(eq + 1)
            }

        val sessionId       = queryParts["s"].orEmpty()
        val webPublicKeyHex = queryParts["k"].orEmpty()

        if (sessionId.isBlank() || webPublicKeyHex.isBlank()) {
            _uiState.value = QrScannerUiState.Error("QR kodu boş bir oturum içeriyor.")
            return
        }

        _uiState.value = QrScannerUiState.Loading
        viewModelScope.launch {
            try {
                val token = keyVaultManager.getAccessToken() ?: run {
                    _uiState.value = QrScannerUiState.Error("Oturum bulunamadı. Lütfen tekrar giriş yap.")
                    return@launch
                }
                val shadeId = keyVaultManager.getShadeId() ?: run {
                    _uiState.value = QrScannerUiState.Error("ShadeId bulunamadı.")
                    return@launch
                }
                val userId = keyVaultManager.getUserId() ?: run {
                    _uiState.value = QrScannerUiState.Error("UserId bulunamadı.")
                    return@launch
                }
                val x25519PrivKey = keyVaultManager.getX25519PrivateKey() ?: run {
                    _uiState.value = QrScannerUiState.Error("Şifreleme anahtarı bulunamadı.")
                    return@launch
                }
                val ed25519PrivKey = keyVaultManager.getEd25519PrivateKey() ?: run {
                    _uiState.value = QrScannerUiState.Error("İmzalama anahtarı bulunamadı.")
                    return@launch
                }

                // Kimlik bilgilerini JSON bundle olarak oluştur
                val bundleMap = mapOf(
                    "jwt"          to token,
                    "shade_id"     to shadeId,
                    "user_id"      to userId,
                    "x25519_priv"  to x25519PrivKey,
                    "ed25519_priv" to ed25519PrivKey
                )
                val bundleBytes = gson.toJson(bundleMap).toByteArray(Charsets.UTF_8)

                // X25519 ECDH + HKDF → bundle'ı transfer key ile şifrele
                val handshake = webPairingCryptoManager.startSession(bundleBytes, webPublicKeyHex)
                Log.d("QrScanner", "Handshake tamamlandı, sessionId=$sessionId")

                // Backend'e authorize isteği gönder
                val request = AuthorizeWebSessionRequest(
                    ciphertext      = handshake.ciphertextHex,
                    nonce           = handshake.nonceHex,
                    androidX25519Pub = handshake.androidPublicKeyHex
                )
                val response = webSessionService.authorizeSession("Bearer $token", sessionId, request)
                if (response.isSuccessful) {
                    _uiState.value = QrScannerUiState.Success
                    Log.d("QrScanner", "Web oturumu bağlandı ✓")
                } else {
                    _uiState.value = QrScannerUiState.Error(
                        "Sunucu hatası: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                Log.e("QrScanner", "Web link hatası: ${e.message}")
                _uiState.value = QrScannerUiState.Error(
                    "Bağlantı hatası: ${e.message?.take(60)}"
                )
            }
        }
    }

    fun reset() {
        _uiState.value = QrScannerUiState.Idle
    }
}
