package com.shade.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shade.app.data.local.converters.RoomConverters
import com.shade.app.data.local.dao.ChatDao
import com.shade.app.data.local.dao.ContactDao
import com.shade.app.data.local.dao.GroupDao
import com.shade.app.data.local.dao.GroupReadReceiptDao
import com.shade.app.data.local.dao.MessageDao
import com.shade.app.data.local.dao.SenderKeyDao
import com.shade.app.data.local.entities.ChatEntity
import com.shade.app.data.local.entities.ContactEntity
import com.shade.app.data.local.entities.GroupEntity
import com.shade.app.data.local.entities.GroupMemberEntity
import com.shade.app.data.local.entities.GroupReadReceiptEntity
import com.shade.app.data.local.entities.MessageEntity
import com.shade.app.data.local.entities.OwnSenderKeyEntity
import com.shade.app.data.local.entities.PeerSenderKeyEntity
import com.shade.app.data.local.entities.SkdmDispatchedEntity

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE messages ADD COLUMN audioPath TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN audioDurationMs INTEGER")
        database.execSQL("ALTER TABLE messages ADD COLUMN filePath TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN fileName TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN fileSizeBytes INTEGER")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chats ADD COLUMN isGroup INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE chats ADD COLUMN groupName TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE contacts ADD COLUMN profileName TEXT")
    }
}

/**
 * v7 → v8 — Sender Keys & local group mirror.
 *
 * Adds four tables:
 *  - `groups` + `group_members` mirror the backend's group state so the UI can
 *    render member lists and react to `GroupMembershipEvent` without a round-trip.
 *  - `own_sender_keys` + `peer_sender_keys` back the Sender Keys ratchet
 *    described in API_CONTRACT.md → "Group Messaging Protocol".
 *  - `skdm_dispatched` is the local bookkeeping that prevents re-shipping the
 *    same SKDM to a peer device that already has it.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `groups` (
                `groupId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `ownerId` TEXT NOT NULL,
                `avatarUrl` TEXT,
                `createdAt` TEXT NOT NULL,
                PRIMARY KEY(`groupId`)
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `group_members` (
                `groupId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `shadeId` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                PRIMARY KEY(`groupId`, `userId`)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_group_members_groupId` ON `group_members` (`groupId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_group_members_userId` ON `group_members` (`userId`)")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `own_sender_keys` (
                `groupId` TEXT NOT NULL,
                `keyId` INTEGER NOT NULL,
                `chainKeyHex` TEXT NOT NULL,
                `chainIndex` INTEGER NOT NULL,
                `signingPublicKeyHex` TEXT NOT NULL,
                `signingPrivateKeyHex` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`groupId`)
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `peer_sender_keys` (
                `groupId` TEXT NOT NULL,
                `peerUserId` TEXT NOT NULL,
                `peerDeviceId` TEXT NOT NULL,
                `keyId` INTEGER NOT NULL,
                `chainKeyHex` TEXT NOT NULL,
                `chainIndex` INTEGER NOT NULL,
                `signingPublicKeyHex` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`groupId`, `peerUserId`, `peerDeviceId`, `keyId`)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_peer_sender_keys_groupId` ON `peer_sender_keys` (`groupId`)")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_peer_sender_keys_groupId_peerUserId_peerDeviceId` " +
                    "ON `peer_sender_keys` (`groupId`, `peerUserId`, `peerDeviceId`)"
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `skdm_dispatched` (
                `groupId` TEXT NOT NULL,
                `peerUserId` TEXT NOT NULL,
                `peerDeviceId` TEXT NOT NULL,
                `ownKeyId` INTEGER NOT NULL,
                `dispatchedAt` INTEGER NOT NULL,
                PRIMARY KEY(`groupId`, `peerUserId`, `peerDeviceId`, `ownKeyId`)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_skdm_dispatched_groupId` ON `skdm_dispatched` (`groupId`)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `group_read_receipts` (
                `messageId` TEXT NOT NULL,
                `readerShadeId` TEXT NOT NULL,
                PRIMARY KEY(`messageId`, `readerShadeId`)
            )
            """.trimIndent()
        )
    }
}

/** Grup mesajlarını DM listesi sorgusundan ayırmak için `messages.isGroupThread`. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE messages ADD COLUMN isGroupThread INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "UPDATE messages SET isGroupThread = 1 WHERE receiverId IN (SELECT groupId FROM groups)"
        )
    }
}

@Database(
    entities = [
        MessageEntity::class,
        ContactEntity::class,
        ChatEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        OwnSenderKeyEntity::class,
        PeerSenderKeyEntity::class,
        SkdmDispatchedEntity::class,
        GroupReadReceiptEntity::class,
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class ShadeDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun contactDao(): ContactDao
    abstract fun groupDao(): GroupDao
    abstract fun senderKeyDao(): SenderKeyDao
    abstract fun groupReadReceiptDao(): GroupReadReceiptDao
}
