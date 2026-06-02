package com.shade.app.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shade.app.security.KeyVaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QrViewModel @Inject constructor(
    private val keyVaultManager: KeyVaultManager
) : ViewModel() {

    private val _shadeId = MutableStateFlow<String?>(null)
    val shadeId = _shadeId.asStateFlow()

    init {
        viewModelScope.launch {
            _shadeId.value = keyVaultManager.getShadeId()
        }
    }
}
