package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.FlashcardReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards ORDER BY createdAt DESC")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE subjectId = :subjectId ORDER BY createdAt DESC")
    fun getFlashcardsBySubject(subjectId: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE lectureId = :lectureId ORDER BY createdAt DESC")
    fun getFlashcardsByLecture(lectureId: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE nextReviewAt <= :currentTime ORDER BY nextReviewAt ASC")
    fun getDueFlashcards(currentTime: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT COUNT(*) FROM flashcards WHERE nextReviewAt <= :currentTime")
    fun getDueFlashcardsCount(currentTime: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM flashcards")
    fun getTotalFlashcardsCount(): Flow<Int>

    @Query("SELECT * FROM flashcards WHERE id = :id LIMIT 1")
    suspend fun getFlashcardById(id: String): FlashcardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: FlashcardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(flashcards: List<FlashcardEntity>)

    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)

    @Delete
    suspend fun deleteFlashcard(flashcard: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteFlashcardById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: FlashcardReviewEntity)

    @Query("SELECT * FROM flashcard_reviews WHERE flashcardId = :flashcardId ORDER BY reviewedAt DESC")
    fun getReviewsForFlashcard(flashcardId: String): Flow<List<FlashcardReviewEntity>>
}
