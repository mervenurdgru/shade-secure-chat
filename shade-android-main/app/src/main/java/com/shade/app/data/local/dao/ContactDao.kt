package com.shade.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shade.app.data.local.entities.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Query("SELECT * FROM contacts")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE shadeId = :shadeId LIMIT 1")
    suspend fun getContactByShadeId(shadeId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE shadeId = :shadeId LIMIT 1")
    fun observeContactByShadeId(shadeId: String): Flow<ContactEntity?>

    @Query("SELECT * FROM contacts WHERE userId = :userId LIMIT 1")
    suspend fun getContactByUserId(userId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE savedName LIKE '%' || :query || '%'")
    fun searchContacts(query: String): Flow<List<ContactEntity>>

    @Query("UPDATE contacts SET savedName = :newName WHERE shadeId = :shadeId")
    suspend fun updateNameByShadeId(shadeId: String, newName: String)

    @Query("UPDATE contacts SET profileName = :name WHERE shadeId = :shadeId")
    suspend fun updateProfileNameByShadeId(shadeId: String, name: String?)

    @Query("UPDATE contacts SET profileImagePath = :path WHERE shadeId = :shadeId")
    suspend fun updateProfileImageByShadeId(shadeId: String, path: String?)

    @Query("UPDATE contacts SET isBlocked = :isBlocked WHERE userId = :userId")
    suspend fun setBlocked(userId: String, isBlocked: Boolean)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)
}
