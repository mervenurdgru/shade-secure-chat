package com.shade.app.data.repository

import com.shade.app.data.local.dao.GroupDao
import com.shade.app.data.local.entities.GroupEntity
import com.shade.app.data.local.entities.GroupMemberEntity
import com.shade.app.data.remote.api.GroupService
import com.shade.app.data.remote.dto.AddMemberRequest
import com.shade.app.data.remote.dto.CreateGroupRequest
import com.shade.app.data.remote.dto.CreateInviteRequest
import com.shade.app.data.remote.dto.GroupResponse
import com.shade.app.data.remote.dto.InviteResponse
import com.shade.app.data.remote.dto.RedeemInviteResponse
import com.shade.app.domain.repository.GroupRepository
import com.shade.app.security.KeyVaultManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GroupRepositoryImpl @Inject constructor(
    private val groupService: GroupService,
    private val groupDao: GroupDao,
    private val keyVaultManager: KeyVaultManager,
) : GroupRepository {

    private suspend fun token() = "Bearer ${keyVaultManager.getAccessToken()}"

    // ── Remote ───────────────────────────────────────────────────────────────

    override suspend fun createGroup(name: String, memberIds: List<String>): Result<GroupResponse> =
        runCatching {
            val resp = groupService.createGroup(token(), CreateGroupRequest(name, memberIds))
            val body = resp.body() ?: error("Empty response (${resp.code()})")
            cacheGroupResponse(body)
            body
        }

    override suspend fun listGroups(): Result<List<GroupResponse>> =
        runCatching {
            val resp = groupService.listGroups(token())
            val body = resp.body() ?: error("Empty response (${resp.code()})")
            body.forEach { cacheGroupResponse(it) }
            body
        }

    override suspend fun getGroup(groupId: String): Result<GroupResponse> =
        runCatching {
            val resp = groupService.getGroup(token(), groupId)
            val body = resp.body() ?: error("Empty response (${resp.code()})")
            cacheGroupResponse(body)
            body
        }

    override suspend fun deleteGroup(groupId: String): Result<Unit> =
        runCatching {
            val resp = groupService.deleteGroup(token(), groupId)
            if (!resp.isSuccessful) error("Delete failed (${resp.code()})")
            groupDao.deleteGroup(groupId)
            groupDao.clearMembers(groupId)
        }

    override suspend fun addMember(groupId: String, userId: String): Result<Unit> =
        runCatching {
            val resp = groupService.addMember(token(), groupId, AddMemberRequest(userId))
            if (!resp.isSuccessful) error("Add member failed (${resp.code()})")
            // The actual member row is filled in by the JOINED GME handler — we
            // don't have shade_id here without an extra round-trip.
        }

    override suspend fun removeMember(groupId: String, userId: String): Result<Unit> =
        runCatching {
            val resp = groupService.removeMember(token(), groupId, userId)
            if (!resp.isSuccessful) error("Remove member failed (${resp.code()})")
            groupDao.removeMember(groupId, userId)
        }

    override suspend fun createInvite(groupId: String?, maxUses: Int): Result<InviteResponse> =
        runCatching {
            val resp = groupService.createInvite(token(), CreateInviteRequest(groupId, maxUses))
            resp.body() ?: error("Empty response (${resp.code()})")
        }

    override suspend fun redeemInvite(code: String): Result<RedeemInviteResponse> =
        runCatching {
            val resp = groupService.redeemInvite(token(), code)
            val body = resp.body() ?: error("Empty response (${resp.code()})")
            body.group?.let { cacheGroupResponse(it) }
            body
        }

    // ── Local cache ──────────────────────────────────────────────────────────

    override suspend fun getCachedGroup(groupId: String): GroupEntity? = groupDao.getGroup(groupId)

    override fun observeCachedGroup(groupId: String): Flow<GroupEntity?> =
        groupDao.observeGroup(groupId)

    override suspend fun getCachedMembers(groupId: String): List<GroupMemberEntity> =
        groupDao.getMembers(groupId)

    override fun observeCachedMembers(groupId: String): Flow<List<GroupMemberEntity>> =
        groupDao.observeMembers(groupId)

    override suspend fun upsertLocalMember(member: GroupMemberEntity) {
        groupDao.upsertMembers(listOf(member))
    }

    override suspend fun removeLocalMember(groupId: String, userId: String) {
        groupDao.removeMember(groupId, userId)
    }

    override suspend fun clearLocal(groupId: String) {
        groupDao.deleteGroup(groupId)
        groupDao.clearMembers(groupId)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun cacheGroupResponse(resp: GroupResponse) {
        groupDao.upsertGroup(
            GroupEntity(
                groupId = resp.groupId,
                name = resp.name,
                ownerId = resp.ownerId,
                avatarUrl = resp.avatarUrl,
                createdAt = resp.createdAt,
            )
        )
        groupDao.replaceMembers(
            resp.groupId,
            resp.members.map { member ->
                GroupMemberEntity(
                    groupId = resp.groupId,
                    userId = member.userId,
                    shadeId = member.shadeId,
                    role = member.role,
                )
            }
        )
    }
}
