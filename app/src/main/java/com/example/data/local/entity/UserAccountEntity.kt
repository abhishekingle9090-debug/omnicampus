package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_accounts",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserAccountEntity(
    @PrimaryKey
    val id: String,
    val email: String,
    val name: String,
    val passwordHash: String,
    val salt: String,
    val semester: String = "Semester 1",
    val isVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
