package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.DistractionAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DistractionAppDao {
    @Query("SELECT * FROM distraction_apps ORDER BY appName ASC")
    fun getAllApps(): Flow<List<DistractionAppEntity>>

    @Query("SELECT * FROM distraction_apps WHERE isMonitored = 1")
    fun getMonitoredApps(): Flow<List<DistractionAppEntity>>

    @Query("SELECT * FROM distraction_apps WHERE isMonitored = 1")
    suspend fun getMonitoredAppsList(): List<DistractionAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: DistractionAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<DistractionAppEntity>)

    @Update
    suspend fun updateApp(app: DistractionAppEntity)

    @Delete
    suspend fun deleteApp(app: DistractionAppEntity)
}
