package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["lectureId"]), Index(value = ["subjectId"])]
)
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val lectureId: String,
    val subjectId: String,
    val style: String = "ACADEMIC", // CONCISE, ACADEMIC, EXAM, CHAPTER, DEFINITIONS, QA
    val detailLevel: String = "STANDARD", // BRIEF, STANDARD, COMPREHENSIVE
    val title: String,
    val overview: String,
    val keyConceptsJson: String = "[]",
    val definitionsJson: String = "[]",
    val examplesJson: String = "[]",
    val formulasJson: String = "[]",
    val importantPointsJson: String = "[]",
    val summary: String,
    val revisionQuestionsJson: String = "[]",
    val customNotes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
