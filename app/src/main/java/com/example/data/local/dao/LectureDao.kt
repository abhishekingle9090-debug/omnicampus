package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.LectureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LectureDao {
    @Query("SELECT * FROM lectures ORDER BY recordedAt DESC")
    fun getAllLectures(): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures WHERE subjectId = :subjectId ORDER BY recordedAt DESC")
    fun getLecturesBySubject(subjectId: String): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures WHERE id = :id LIMIT 1")
    fun getLectureByIdFlow(id: String): Flow<LectureEntity?>

    @Query("SELECT * FROM lectures WHERE id = :id LIMIT 1")
    suspend fun getLectureById(id: String): LectureEntity?

    @Query("SELECT * FROM lectures ORDER BY recordedAt DESC LIMIT :limit")
    fun getRecentLectures(limit: Int): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures WHERE title LIKE '%' || :query || '%' OR topic LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY recordedAt DESC")
    fun searchLectures(query: String): Flow<List<LectureEntity>>

    @Query("SELECT COUNT(*) FROM lectures")
    fun getLecturesCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLecture(lecture: LectureEntity)

    @Update
    suspend fun updateLecture(lecture: LectureEntity)

    @Delete
    suspend fun deleteLecture(lecture: LectureEntity)

    @Query("DELETE FROM lectures WHERE id = :id")
    suspend fun deleteLectureById(id: String)
}
