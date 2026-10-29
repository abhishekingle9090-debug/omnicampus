package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey
    val id: String,
    val subjectId: String?,
    val plannedMinutes: Int,
    val actualMinutes: Int = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val status: String = "ACTIVE", // ACTIVE, COMPLETED, CANCELLED, PAUSED
    val distractionCount: Int = 0,
    val verificationAttempted: Boolean = false,
    val verificationPassed: Boolean = false,
    val goal: String = "",
    val notes: String = ""
)
