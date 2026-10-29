package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val code: String,
    val semester: String,
    val colorHex: String,
    val createdAt: Long = System.currentTimeMillis()
)
