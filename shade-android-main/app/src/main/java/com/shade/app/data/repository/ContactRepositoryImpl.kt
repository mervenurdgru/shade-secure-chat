package com.shade.app.data.repository

import android.content.Context
import android.util.Log
import com.shade.app.data.local.dao.ContactDao
import com.shade.app.data.local.entities.ContactEntity
import com.shade.app.data.remote.api.KeysService
import com.shade.app.data.remote.api.MediaService
import com.shade.app.data.remote.api.UserService
import com.shade.app.domain.repository.ContactRepository
import com.shade.app.security.KeyVaultManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao,
    private val userService: UserService,
    private val keysService: KeysService,
    private val keyVaultManager: KeyVaultManager,
    private val mediaService: MediaService,
    @ApplicationContext private val context: Context
) : ContactRepository {

    // ── Download flood koruması ───────────────────────────────────────────────
    // Aynı shadeId için eş zamanlı veya kısa aralıklı indirme denemelerini önler.
    // 429 (rate-limit) alındığında 60 saniye cooldown uygulanır; bu süre içinde
    // gelen tüm çağrılar mevcut (null olabilir) path'i döndürür, sunucuya istek atmaz.

    /** shadeId → cooldown bitiş zamanı (System.currentTimeMillis) */
    private val downloadCooldown = ConcurrentHashMap<String, Long>()

    /** Şu an indirme aktif olan shadeId'ler — çift istek gönderimini engeller */
    private val activeDownloads = ConcurrentHashMap.newKeySet<String>()

    companion object {
        /** Başarısız indirme sonrası bekleme süresi (ms) */
        private const val COOLDOWN_MS = 60_000L
        private const val TAG = "ContactRepo"
        /** Geçerli bir profil fotoğrafı için minimum boyut (bayt) */
        private const val MIN_IMAGE_BYTES = 1024L
    }

    override suspend fun insertContact(contact: ContactEntity) {
        contactDao.insertContact(contact)
    }

    override fun getAllContacts(): Flow<List<ContactEntity>> {
        return contactDao.getAllContacts()
    }

    override suspend fun getContactByShadeId(shadeId: String): ContactEntity? {
        return contactDao.getContactByShadeId(shadeId)
    }

    override suspend fun getContactByUserId(userId: String): ContactEntity? {
        return contactDao.getContactByUserId(userId)
    }

    override fun observeContactByShadeId(shadeId: String): Flow<ContactEntity?> {
        return contactDao.observeContactByShadeId(shadeId)
    }

    override fun searchContacts(query: String): Flow<List<ContactEntity>> {
        return contactDao.searchContacts(query)
    }

    override suspend fun getOrFetchContact(shadeId: String): ContactEntity? {
        val local = contactDao.getContactByShadeId(shadeId)

        // NonCancellable: navigation can cancel the caller's scope mid-flight (causing
        // "context canceled" on the Cloudflare tunnel). Run the network + disk work to
        // completion so the photo is cached for subsequent views even if the user
        // navigates away before the response arrives.
        return withContext(NonCancellable) {
            try {
                val token = "Bearer ${keyVaultManager.getAccessToken()}"
                val response = userService.lookup(token, shadeId)
                Log.d(TAG, "lookup response [$shadeId]: code=${response.code()} body=${response.body()} errorBody=${response.errorBody()?.string()}")
                if (response.isSuccessful) {
                    response.body()?.let { dto ->
                        val freshProfileName = dto.displayName?.takeIf { it.isNotBlank() }

                        // Profil fotoğrafını indir (yalnızca imageId değiştiyse)
                        val imagePath = downloadProfileImageIfNeeded(
                            token = token,
                            shadeId = shadeId,
                            remoteImageId = dto.profileImageId,
                            currentPath = local?.profileImagePath
                        )

                        if (local != null) {
                            // Kişi zaten DB'de var — profileName ve profileImagePath'i güncelle
                            // (savedName'e dokunma: kullanıcının kaydettiği özel isim korunur)
                            contactDao.updateProfileNameByShadeId(shadeId, freshProfileName)
                            if (imagePath != local.profileImagePath) {
                                contactDao.updateProfileImageByShadeId(shadeId, imagePath)
                            }
                            local.copy(profileName = freshProfileName, profileImagePath = imagePath)
                        } else {
                            // Yeni kişi: profileName, encryptionPublicKey ve profileImagePath'i kaydet
                            val newContact = ContactEntity(
                                userId = dto.userId,
                                shadeId = dto.shadeId,
                                encryptionPublicKey = dto.encryptionPublicKey,
                                savedName = null,
                                profileName = freshProfileName,
                                profileImagePath = imagePath
                            )
                            contactDao.insertContact(newContact)
                            newContact
                        }
                    }
                } else local
            } catch (e: Exception) {
                Log.e(TAG, "getOrFetchContact hata [$shadeId]: ${e.message}", e)
                local
            }
        }
    }

    /**
     * Profil fotoğrafını backend'den indirir ve yerel dosyaya kaydeder.
     * Aynı imageId zaten mevcutsa mevcut path'i döndürür (gereksiz indirme yapmaz).
     * imageId yoksa null döndürür.
     *
     * Flood koruması:
     *  - Aynı shadeId için aynı anda sadece bir indirme aktif olabilir.
     *  - 429 veya hata alınırsa 60 saniye cooldown uygulanır.
     */
    private suspend fun downloadProfileImageIfNeeded(
        token: String,
        shadeId: String,
        remoteImageId: String?,
        currentPath: String?
    ): String? {
        if (remoteImageId == null) return null

        // Mevcut dosya adından imageId çıkar (dosya adı = "<imageId>.jpg")
        val currentImageId = currentPath?.let { File(it).nameWithoutExtension }
        if (currentImageId == remoteImageId && currentPath != null) {
            val existingFile = File(currentPath)
            if (existingFile.exists() && existingFile.length() >= MIN_IMAGE_BYTES) {
                return currentPath // Zaten güncel ve tam, tekrar indirme
            }
            // Dosya eksik veya bozuk (çok küçük) — yeniden indir
            if (existingFile.exists()) {
                Log.w(TAG, "Bozuk önbellek dosyası silindi (${existingFile.length()} bayt): $shadeId")
                existingFile.delete()
            }
        }

        // Cooldown kontrolü: son başarısız indirmeden 60 saniye geçmedi mi?
        val now = System.currentTimeMillis()
        val cooldownUntil = downloadCooldown[shadeId] ?: 0L
        if (now < cooldownUntil) {
            Log.d(TAG, "İndirme cooldown'da, atlanıyor: $shadeId (${(cooldownUntil - now) / 1000}s kaldı)")
            return currentPath
        }

        // Eş zamanlı indirme kontrolü: bu shadeId için indirme zaten devam ediyor mu?
        if (!activeDownloads.add(shadeId)) {
            Log.d(TAG, "İndirme zaten aktif, atlanıyor: $shadeId")
            return currentPath
        }

        return try {
            val response = mediaService.downloadImage(token, remoteImageId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body == null) {
                    Log.w(TAG, "Boş yanıt gövdesi: $shadeId")
                    downloadCooldown[shadeId] = System.currentTimeMillis() + COOLDOWN_MS
                    return currentPath
                }
                val dir = File(context.filesDir, "avatars").also { it.mkdirs() }
                val dest = File(dir, "$remoteImageId.jpg")
                // Dosya yazma işlemini IO dispatcher'da yap — ana iş parçacığında
                // bloke eden G/Ç yapmamak ve tam akış okumayı garanti etmek için.
                withContext(Dispatchers.IO) {
                    body.byteStream().use { input ->
                        FileOutputStream(dest).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                val writtenBytes = if (dest.exists()) dest.length() else 0L
                Log.d(TAG, "İndirilen bayt: $writtenBytes for $shadeId")
                if (writtenBytes >= MIN_IMAGE_BYTES) {
                    downloadCooldown.remove(shadeId) // Başarılı → cooldown'ı temizle
                    dest.absolutePath
                } else {
                    Log.w(TAG, "Dosya çok küçük ($writtenBytes bayt), tekrar indirilecek: $shadeId")
                    dest.delete()
                    downloadCooldown[shadeId] = System.currentTimeMillis() + COOLDOWN_MS
                    currentPath
                }
            } else {
                val code = response.code()
                Log.w(TAG, "Profil fotoğrafı indirilemedi: $code for $shadeId")
                // 429 veya sunucu hatası → cooldown başlat
                downloadCooldown[shadeId] = System.currentTimeMillis() + COOLDOWN_MS
                currentPath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Profil fotoğrafı indirme hatası: ${e.message}")
            downloadCooldown[shadeId] = System.currentTimeMillis() + COOLDOWN_MS
            currentPath
        } finally {
            activeDownloads.remove(shadeId)
        }
    }

    override suspend fun getOrFetchContactByUserId(
        userId: String,
        bypassCache: Boolean,
    ): ContactEntity? {
        val existing = contactDao.getContactByUserId(userId)
        if (!bypassCache && existing != null) return existing

        return withContext(NonCancellable) {
            try {
                val token = "Bearer ${keyVaultManager.getAccessToken()}"
                val response = keysService.getKeys(token, userId)
                if (!response.isSuccessful) return@withContext null
                val body = response.body() ?: return@withContext null
                val pubkey = body.publicKey.trim()
                val shadeId = existing?.shadeId?.ifBlank { body.coreGuardId } ?: body.coreGuardId

                // Display name ve profil fotoğrafı ID'sini lookup ile çek
                val lookupToken = "Bearer ${keyVaultManager.getAccessToken()}"
                var displayName: String? = null
                var remoteImageId: String? = null
                try {
                    val lookupResp = userService.lookup(lookupToken, shadeId)
                    if (lookupResp.isSuccessful) {
                        val dto = lookupResp.body()
                        displayName = dto?.displayName?.takeIf { it.isNotBlank() }
                        remoteImageId = dto?.profileImageId
                    }
                } catch (_: Exception) {}

                // Profil fotoğrafı eksikse indir
                val needsPhoto = existing?.profileImagePath
                    ?.let { File(it).exists() } != true
                val imagePath = if (needsPhoto) {
                    downloadProfileImageIfNeeded(
                        token = lookupToken,
                        shadeId = shadeId,
                        remoteImageId = remoteImageId,
                        currentPath = existing?.profileImagePath,
                    )
                } else {
                    existing?.profileImagePath
                }

                val contact = existing?.copy(
                        encryptionPublicKey = pubkey,
                        shadeId = shadeId,
                        profileName = existing.profileName ?: displayName,
                        profileImagePath = imagePath,
                    ) ?: ContactEntity(
                        userId = userId,
                        shadeId = shadeId,
                        encryptionPublicKey = pubkey,
                        savedName = null,
                        profileName = displayName,
                        profileImagePath = imagePath,
                    )
                contactDao.insertContact(contact)
                contact
            } catch (e: Exception) {
                Log.w(TAG, "getOrFetchContactByUserId error userId=$userId - ${e.message}")
                existing?.takeIf { it.encryptionPublicKey.isNotBlank() }
            }
        }
    }

    override suspend fun updateContactName(shadeId: String, newName: String) {
        contactDao.updateNameByShadeId(shadeId, newName)
    }

    override suspend fun deleteContact(contact: ContactEntity) {
        contactDao.deleteContact(contact)
    }

    override suspend fun setBlocked(userId: String, isBlocked: Boolean) {
        contactDao.setBlocked(userId, isBlocked)
    }
}
