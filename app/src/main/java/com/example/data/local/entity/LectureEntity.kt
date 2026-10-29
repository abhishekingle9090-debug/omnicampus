package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lectures",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["subjectId"])]
)
data class LectureEntity(
    @PrimaryKey
    val id: String,
    val subjectId: String,
    val title: String,
    val topic: String = "",
    val description: String = "",
    val audioFilePath: String? = null,
    val durationSeconds: Long = 0L,
    val language: String = "en-US",
    val recordedAt: Long = System.currentTimeMillis(),
    val status: String = "RECORDED" // RECORDED, TRANSCRIBING, TRANSCRIBED, FAILED
)
