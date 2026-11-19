package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyCopierDao {

    @Query("SELECT * FROM key_records ORDER BY updatedAt DESC")
    fun observeAllKeys(): Flow<List<KeyRecordEntity>>

    @Query("SELECT * FROM key_records WHERE id = :id LIMIT 1")
    suspend fun getKeyById(id: Long): KeyRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(record: KeyRecordEntity): Long

    @Update
    suspend fun updateKey(record: KeyRecordEntity)

    @Query("UPDATE key_records SET isFavorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, updatedAt: Long)

    @Query("DELETE FROM key_records WHERE id = :id")
    suspend fun deleteKeyById(id: Long)

    @Query("SELECT * FROM key_attachments WHERE keyRecordId = :keyRecordId ORDER BY isPrimary DESC, createdAt DESC")
    fun observeAttachmentsForKey(keyRecordId: Long): Flow<List<KeyAttachmentEntity>>

    @Query("SELECT * FROM key_attachments WHERE keyRecordId = :keyRecordId")
    suspend fun getAttachmentsForKey(keyRecordId: Long): List<KeyAttachmentEntity>

    @Query("SELECT COUNT(*) FROM key_attachments")
    fun observeAttachmentCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: KeyAttachmentEntity): Long

    @Query("DELETE FROM key_attachments WHERE id = :id")
    suspend fun deleteAttachmentById(id: Long)
}
