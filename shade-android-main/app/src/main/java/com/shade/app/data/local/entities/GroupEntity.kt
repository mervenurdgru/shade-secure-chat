package com.shade.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local mirror of a group. Synced from `/api/v1/groups/:id` and the
 * `GroupMembershipEvent` broadcasts. The crypto-sensitive state (sender keys)
 * lives in its own tables — this entity only holds presentation metadata.
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey
    val groupId: String,
    val name: String,
    val ownerId: String,
    val avatarUrl: String?,
    /** ISO-8601 string as returned by the backend. */
    val createdAt: String,
)

/**
 * Member of a [GroupEntity]. Composite PK = (groupId, userId).
 */
@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "userId"],
    indices = [Index("groupId"), Index("userId")],
)
data class GroupMemberEntity(
    val groupId: String,
    val userId: String,
    val shadeId: String,
    /** "owner" or "member". */
    val role: String,
)
