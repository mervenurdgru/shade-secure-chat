package com.shade.app.domain.repository

import com.shade.app.data.local.entities.ContactEntity
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    suspend fun insertContact(contact: ContactEntity)
    fun getAllContacts(): Flow<List<ContactEntity>>
    suspend fun getContactByShadeId(shadeId: String): ContactEntity?
    suspend fun getContactByUserId(userId: String): ContactEntity?
    fun observeContactByShadeId(shadeId: String): Flow<ContactEntity?>
    fun searchContacts(query: String): Flow<List<ContactEntity>>

    suspend fun getOrFetchContact(shadeId: String): ContactEntity?

    /**
     * Resolves a contact by UUID. Used by group flows where peers are
     * referenced by `user_id`. Uses `GET /api/v1/keys/:id` when there is no
     * cached row yet, or always when [bypassCache] is true (fresh pubkey).
     *
     * When a row already exists and the network is hit, only
     * `encryptionPublicKey` (and blank `shadeId`) are refreshed so saved
     * contact metadata is preserved.
     */
    suspend fun getOrFetchContactByUserId(userId: String, bypassCache: Boolean = false): ContactEntity?

    suspend fun deleteContact(contact: ContactEntity)
    suspend fun updateContactName(shadeId: String, newName: String)
    suspend fun setBlocked(userId: String, isBlocked: Boolean)
}