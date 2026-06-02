package com.shade.app.ui.myprofile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shade.app.data.remote.api.MediaService
import com.shade.app.data.remote.api.UserService
import com.shade.app.data.remote.dto.UpdateAvatarRequest
import com.shade.app.data.remote.dto.UpdateDisplayNameRequest
import com.shade.app.data.repository.AppPrefsRepository
import com.shade.app.security.KeyVaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class MyProfileUiState(
    val shadeId: String = "",
    val displayName: String = "",
    val profilePhotoPath: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class MyProfileViewModel @Inject constructor(
    private val keyVaultManager: KeyVaultManager,
    private val appPrefsRepository: AppPrefsRepository,
    private val userService: UserService,
    private val mediaService: MediaService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyProfileUiState())
    val uiState = _uiState.asStateFlow()

    private val _saveSuccess = MutableSharedFlow<Unit>()
    val saveSuccess = _saveSuccess.asSharedFlow()

    init {
        viewModelScope.launch {
            val shadeId = keyVaultManager.getShadeId() ?: ""
            _uiState.update { it.copy(shadeId = shadeId, isLoading = false) }
        }
        viewModelScope.launch {
            appPrefsRepository.displayName.collect { name ->
                _uiState.update { it.copy(displayName = name) }
            }
        }
        viewModelScope.launch {
            appPrefsRepository.profilePhotoPath.collect { path ->
                _uiState.update { it.copy(profilePhotoPath = path) }
            }
        }
    }

    fun saveName(name: String) {
        if (name.isBlank()) return
        val trimmed = name.trim()
        viewModelScope.launch {
            // 1. Önce yerel olarak kaydet
            appPrefsRepository.setDisplayName(trimmed)

            // 2. Backend'e de gönder (başarısız olsa bile yerel kayıt tutulur)
            try {
                val token = keyVaultManager.getAccessToken() ?: return@launch
                userService.updateDisplayName(
                    "Bearer $token",
                    UpdateDisplayNameRequest(trimmed)
                )
            } catch (e: Exception) {
                android.util.Log.w("MyProfile", "İsim sunucuya gönderilemedi: ${e.message}")
            }

            _saveSuccess.emit(Unit)
        }
    }

    /** Galeri'den gelen URI'yi yükler: yerel kaydeder → backend'e upload eder → avatar endpoint'ini günceller. */
    fun saveProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            try {
                // 1. URI'dan bytes'ı ana thread dışında oku
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    Log.e("MyProfile", "URI'dan veri okunamadı: $uri")
                    return@launch
                }

                // 2. Yerel dosyaya yaz
                val dir = File(context.filesDir, "profile").also { it.mkdirs() }
                val dest = File(dir, "avatar.jpg")
                dest.writeBytes(bytes)

                // 3. Yerel path'i kaydet (hemen UI'da görünsün)
                appPrefsRepository.setProfilePhotoPath(dest.absolutePath)

                // 4. Backend'e upload et
                val token = keyVaultManager.getAccessToken() ?: run {
                    Log.w("MyProfile", "Token alınamadı")
                    _saveSuccess.emit(Unit)
                    return@launch
                }
                val requestBody = dest.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("image", dest.name, requestBody)
                val uploadResponse = mediaService.uploadImage("Bearer $token", part)

                if (uploadResponse.isSuccessful) {
                    val imageId = uploadResponse.body()?.imageId
                    if (imageId != null) {
                        // 5. Avatar endpoint'ini güncelle
                        val avatarResponse = userService.updateAvatar(
                            "Bearer $token",
                            UpdateAvatarRequest(imageId)
                        )
                        if (!avatarResponse.isSuccessful) {
                            Log.w("MyProfile", "Avatar güncelleme başarısız: ${avatarResponse.code()}")
                        } else {
                            Log.i("MyProfile", "Avatar başarıyla güncellendi: $imageId")
                        }
                    }
                } else {
                    Log.w("MyProfile", "Fotoğraf upload başarısız: ${uploadResponse.code()} ${uploadResponse.errorBody()?.string()}")
                }

                _saveSuccess.emit(Unit)
            } catch (e: Exception) {
                Log.e("MyProfile", "Fotoğraf kaydedilemedi: ${e::class.simpleName}: ${e.message}", e)
                _saveSuccess.emit(Unit)
            }
        }
    }

    /** Profil fotoğrafını kaldırır: yerel dosyayı siler → backend'e bildirir. */
    fun removeProfilePhoto() {
        viewModelScope.launch {
            // 1. Yerel dosyayı sil
            val path = _uiState.value.profilePhotoPath
            if (path != null) {
                File(path).delete()
                appPrefsRepository.setProfilePhotoPath(null)
            }

            // 2. Backend'e bildir
            try {
                val token = keyVaultManager.getAccessToken() ?: return@launch
                val response = userService.deleteAvatar("Bearer $token")
                if (!response.isSuccessful) {
                    Log.w("MyProfile", "Avatar silme başarısız: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("MyProfile", "Avatar silinemedi: ${e.message}")
            }
        }
    }
}
