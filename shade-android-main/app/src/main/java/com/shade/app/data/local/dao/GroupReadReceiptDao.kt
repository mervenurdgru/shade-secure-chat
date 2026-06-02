package com.shade.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shade.app.data.local.entities.GroupReadReceiptEntity

@Dao
interface GroupReadReceiptDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: GroupReadReceiptEntity)

    @Query("SELECT COUNT(*) FROM group_read_receipts WHERE messageId = :messageId")
    suspend fun countReaders(messageId: String): Int

    @Query("DELETE FROM group_read_receipts WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: String)
}
