package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TranscriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE lectureId = :lectureId LIMIT 1")
    fun getTranscriptByLectureId(lectureId: String): Flow<TranscriptEntity?>

    @Query("SELECT * FROM transcripts WHERE lectureId = :lectureId LIMIT 1")
    suspend fun getTranscriptDirect(lectureId: String): TranscriptEntity?

    @Query("SELECT * FROM transcripts WHERE id = :id LIMIT 1")
    suspend fun getTranscriptById(id: String): TranscriptEntity?

    @Query("SELECT * FROM transcripts WHERE originalText LIKE '%' || :query || '%' OR correctedText LIKE '%' || :query || '%'")
    fun searchTranscripts(query: String): Flow<List<TranscriptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: TranscriptEntity)

    @Update
    suspend fun updateTranscript(transcript: TranscriptEntity)

    @Query("DELETE FROM transcripts WHERE lectureId = :lectureId")
    suspend fun deleteTranscriptByLectureId(lectureId: String)
}
