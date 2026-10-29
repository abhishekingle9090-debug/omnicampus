package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcripts",
    foreignKeys = [
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["lectureId"])]
)
data class TranscriptEntity(
    @PrimaryKey
    val id: String,
    val lectureId: String,
    val originalText: String,
    val correctedText: String,
    val isEdited: Boolean = false,
    val segmentsJson: String = "[]", // List of TranscriptSegment
    val confidence: Float = 0.9f,
    val updatedAt: Long = System.currentTimeMillis()
)
