package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "key_records")
data class KeyRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val category: String,
    val keyStandard: String,
    val bittingCode: String,
    val bladeLengthMm: Float,
    val bladeWidthMm: Float,
    val bladeThicknessMm: Float,
    val pinCount: Int,
    val depthsJson: String,
    val spacingsJson: String,
    val isDoubleSided: Boolean = false,
    val flatWidthsJson: String = "[]",
    val leftSlopesJson: String = "[]",
    val rightSlopesJson: String = "[]",
    val tipStyle: String = "STANDARD_TAPER",
    val tipLengthMm: Float = 2.6f,
    val tipOffsetMm: Float = 0f,
    val bowScale: Float = 1.0f,
    val customContourJson: String = "[]",
    val useExactPhotoContour: Boolean = false,
    val notes: String,
    val tags: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "key_attachments",
    foreignKeys = [
        ForeignKey(
            entity = KeyRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["keyRecordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("keyRecordId")]
)
data class KeyAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val keyRecordId: Long,
    val localPath: String,
    val fileType: String,
    val isPrimary: Boolean,
    val createdAt: Long
)
