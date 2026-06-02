package com.shade.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val userId: String,

    val shadeId: String,

    val encryptionPublicKey: String,

    /** Kullanıcının bu kişi için kaydettiği özel isim (Kişiler ekranından). */
    val savedName: String?,

    /** Kişinin kendi profilinde belirlediği görünen ad (backend'den otomatik güncellenir). */
    val profileName: String? = null,

    val profileImagePath: String?,
    val isBlocked: Boolean = false
)
