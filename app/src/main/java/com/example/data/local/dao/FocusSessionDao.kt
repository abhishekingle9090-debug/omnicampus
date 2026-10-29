package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSession(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveSessionFlow(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE startTime >= :startOfDayTimestamp ORDER BY startTime DESC")
    fun getTodaySessions(startOfDayTimestamp: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE startTime >= :startOfWeekTimestamp ORDER BY startTime DESC")
    fun getWeeklySessions(startOfWeekTimestamp: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT COALESCE(SUM(actualMinutes), 0) FROM focus_sessions WHERE startTime >= :startOfDayTimestamp AND status = 'COMPLETED'")
    fun getTodayFocusMinutes(startOfDayTimestamp: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(actualMinutes), 0) FROM focus_sessions WHERE status = 'COMPLETED'")
    fun getTotalFocusMinutes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE status = 'COMPLETED'")
    fun getCompletedSessionsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM focus_sessions")
    fun getTotalSessionsCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(distractionCount), 0) FROM focus_sessions")
    fun getTotalDistractionsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity)

    @Update
    suspend fun updateSession(session: FocusSessionEntity)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)
}
