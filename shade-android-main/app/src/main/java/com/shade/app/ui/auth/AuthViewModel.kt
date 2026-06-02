package com.shade.app.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.shade.app.R
import com.shade.app.crypto.MnemonicManager
import com.shade.app.data.remote.api.UserService
import com.shade.app.data.remote.dto.UpdateDisplayNameRequest
import com.shade.app.domain.usecase.auth.LoginUseCase
import com.shade.app.domain.usecase.auth.RegisterUseCase
import com.shade.app.security.KeyVaultManager
import com.shade.app.ui.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(
        val message: UiText,
        val mnemonic: List<String> = emptyList(),
        val shadeId: String? = null
    ) : AuthUiState()
    data class Error(val message: UiText) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
    private val loginUseCase: LoginUseCase,
    private val mnemonicManager: MnemonicManager,
    private val userService: UserService,
    private val keyVaultManager: KeyVaultManager,
): ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState
    private var fcmToken = ""

    init {
        fetchFcmToken()
    }

    fun resetUiState() {
        _uiState.value = AuthUiState.Idle
    }

    private fun fetchFcmToken() {
        viewModelScope.launch {
            try {
                fcmToken = FirebaseMessaging.getInstance().token.await()
                Log.d(TAG, "FCM token obtained: ${fcmToken.take(20)}...")
            } catch (e: Exception) {
                Log.w(TAG, "FCM token fetch failed, proceeding without token", e)
                fcmToken = ""
            }
        }
    }

    fun register(deviceModel: String, displayName: String = "") {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            Log.d(TAG, "register() called — deviceModel=$deviceModel fcmToken=${fcmToken.take(10)}...")

            val currentMnemonic = mnemonicManager.generateMnemonic()
            val result = registerUseCase(currentMnemonic, deviceModel, fcmToken)

            result.onSuccess { authResult ->
                Log.d(TAG, "register success — shadeId=${authResult.shadeId}")

                // Display name ayarla (boş değilse)
                if (displayName.isNotBlank()) {
                    try {
                        val token = "Bearer ${keyVaultManager.getAccessToken()}"
                        userService.updateDisplayName(token, UpdateDisplayNameRequest(displayName.trim()))
                        Log.d(TAG, "display name set: $displayName")
                    } catch (e: Exception) {
                        Log.w(TAG, "display name set failed (non-critical): ${e.message}")
                    }
                }

                _uiState.value = AuthUiState.Success(
                    message = UiText.StringResource(R.string.account_created),
                    mnemonic = currentMnemonic,
                    shadeId = authResult.shadeId
                )
            }.onFailure { e ->
                Log.e(TAG, "register failed — ${e::class.simpleName}: ${e.message} | cause: ${e.cause?.message}")
                _uiState.value = AuthUiState.Error(UiText.StringResource(R.string.something_went_wrong))
            }
        }
    }

    fun login(shadeId: String, mnemonic: List<String>, deviceModel: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            Log.d(TAG, "login() called — shadeId=$shadeId")
            val result = loginUseCase(shadeId, mnemonic, deviceModel, fcmToken)

            result.onSuccess {
                Log.d(TAG, "login success")
                _uiState.value = AuthUiState.Success(UiText.StringResource(R.string.login_successful))
            }.onFailure { e ->
                Log.e(TAG, "login failed", e)
                _uiState.value = AuthUiState.Error(UiText.StringResource(R.string.something_went_wrong))
            }
        }
    }

    companion object {
        private const val TAG = "AuthViewModel"
    }
}
