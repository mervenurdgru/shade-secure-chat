package com.shade.app.domain.repository

import com.shade.app.data.local.entities.GroupEntity
import com.shade.app.data.local.entities.GroupMemberEntity
import com.shade.app.data.remote.dto.GroupResponse
import com.shade.app.data.remote.dto.InviteResponse
import com.shade.app.data.remote.dto.RedeemInviteResponse
import kotlinx.coroutines.flow.Flow

/**
 * Group state on the wire (REST + WS) and its mirrored local cache.
 *
 * The `getGroup` / `listGroups` calls also persist the result into the local
 * `groups` / `group_members` tables, so consumers can observe membership
 * changes from `GroupMembershipEvent` broadcasts without re-fetching.
 */
interface GroupRepository {

    // ── Remote ────────────────────────────────────────────────────────────────
    suspend fun createGroup(name: String, memberIds: List<String>): Result<GroupResponse>
    suspend fun listGroups(): Result<List<GroupResponse>>
    suspend fun getGroup(groupId: String): Result<GroupResponse>
    suspend fun deleteGroup(groupId: String): Result<Unit>
    suspend fun addMember(groupId: String, userId: String): Result<Unit>
    suspend fun removeMember(groupId: String, userId: String): Result<Unit>
    suspend fun createInvite(groupId: String? = null, maxUses: Int = 1): Result<InviteResponse>
    suspend fun redeemInvite(code: String): Result<RedeemInviteResponse>

    // ── Local cache ──────────────────────────────────────────────────────────
    suspend fun getCachedGroup(groupId: String): GroupEntity?
    fun observeCachedGroup(groupId: String): Flow<GroupEntity?>
    suspend fun getCachedMembers(groupId: String): List<GroupMemberEntity>
    fun observeCachedMembers(groupId: String): Flow<List<GroupMemberEntity>>

    /** Add or replace a single member row locally (used by JOINED handler). */
    suspend fun upsertLocalMember(member: GroupMemberEntity)

    /** Remove a member row locally (used by LEFT / REMOVED handlers). */
    suspend fun removeLocalMember(groupId: String, userId: String)

    /** Drop everything for the group locally (used on group deletion). */
    suspend fun clearLocal(groupId: String)
}

