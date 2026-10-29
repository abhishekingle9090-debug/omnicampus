package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flashcard_reviews",
    foreignKeys = [
        ForeignKey(
            entity = FlashcardEntity::class,
            parentColumns = ["id"],
            childColumns = ["flashcardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["flashcardId"])]
)
data class FlashcardReviewEntity(
    @PrimaryKey
    val id: String,
    val flashcardId: String,
    val rating: String, // EASY, MEDIUM, HARD
    val reviewedAt: Long = System.currentTimeMillis()
)
