package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "distraction_apps")
data class DistractionAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isMonitored: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)
