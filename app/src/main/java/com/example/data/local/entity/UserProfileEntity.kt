package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: String = "primary_user",
    val name: String = "Alex Rivera",
    val semester: String = "Semester 5 (Fall 2026)",
    val preferredLanguage: String = "en-US",
    val studyGoalMinutes: Int = 120,
    val preferredFocusMinutes: Int = 45,
    val isGuestMode: Boolean = true,
    val userEmail: String? = null,
    val notificationsEnabled: Boolean = true,
    val darkThemeEnabled: Boolean = true,
    val hasCompletedOnboarding: Boolean = false
)
