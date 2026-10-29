package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["subjectId"]), Index(value = ["lectureId"])]
)
data class FlashcardEntity(
    @PrimaryKey
    val id: String,
    val subjectId: String,
    val lectureId: String,
    val noteId: String? = null,
    val question: String,
    val answer: String,
    val type: String = "CONCEPT", // DEFINITION, CONCEPT, SHORT_ANSWER, TECHNICAL, FORMULA
    val difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD
    val intervalDays: Int = 1,
    val repetitions: Int = 0,
    val easeFactor: Float = 2.5f,
    val nextReviewAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
