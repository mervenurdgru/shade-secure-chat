package com.shade.app.ui.audit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shade.app.data.remote.api.AuditService
import com.shade.app.data.remote.dto.AuditLogItem
import com.shade.app.security.KeyVaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuditUiState(
    val logs: List<AuditLogItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SecurityAuditViewModel @Inject constructor(
    private val auditService: AuditService,
    private val keyVaultManager: KeyVaultManager
) : ViewModel() {

    companion object {
        private const val TAG = "SHADE_AUDIT"
    }

    private val _uiState = MutableStateFlow(AuditUiState())
    val uiState: StateFlow<AuditUiState> = _uiState.asStateFlow()

    init {
        fetchLogs()
    }

    fun fetchLogs() {
        viewModelScope.launch {
            _uiState.value = AuditUiState(isLoading = true)
            try {
                val token = "Bearer ${keyVaultManager.getAccessToken()}"
                val response = auditService.getMyLogs(token)
                if (response.isSuccessful) {
                    val logs = response.body()?.logs ?: emptyList()
                    Log.d(TAG, "Güvenlik günlükleri alındı: ${logs.size} kayıt")
                    _uiState.value = AuditUiState(logs = logs)
                } else {
                    Log.w(TAG, "Günlük alınamadı: ${response.code()}")
                    _uiState.value = AuditUiState(error = "Günlükler alınamadı (${response.code()})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ağ hatası: ${e.message}")
                _uiState.value = AuditUiState(error = "Bağlantı hatası: ${e.message}")
            }
        }
    }
}
