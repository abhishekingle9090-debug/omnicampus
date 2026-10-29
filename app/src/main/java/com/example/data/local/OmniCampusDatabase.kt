package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.DistractionAppDao
import com.example.data.local.dao.FlashcardDao
import com.example.data.local.dao.FocusSessionDao
import com.example.data.local.dao.LectureDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.dao.SubjectDao
import com.example.data.local.dao.TranscriptDao
import com.example.data.local.dao.UserAccountDao
import com.example.data.local.dao.UserProfileDao
import com.example.data.local.entity.DistractionAppEntity
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.FlashcardReviewEntity
import com.example.data.local.entity.FocusSessionEntity
import com.example.data.local.entity.LectureEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.TranscriptEntity
import com.example.data.local.entity.UserAccountEntity
import com.example.data.local.entity.UserProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        SubjectEntity::class,
        LectureEntity::class,
        TranscriptEntity::class,
        NoteEntity::class,
        FlashcardEntity::class,
        FlashcardReviewEntity::class,
        UserProfileEntity::class,
        UserAccountEntity::class,
        FocusSessionEntity::class,
        DistractionAppEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class OmniCampusDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun lectureDao(): LectureDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun noteDao(): NoteDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun userAccountDao(): UserAccountDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun distractionAppDao(): DistractionAppDao

    companion object {
        @Volatile
        private var INSTANCE: OmniCampusDatabase? = null

        fun getDatabase(context: Context): OmniCampusDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniCampusDatabase::class.java,
                    "omnicampus_database"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialStarterData(database)
                    }
                }
            }
        }

        private suspend fun populateInitialStarterData(database: OmniCampusDatabase) {
            // Seed clean initial academic subjects for the student
            val sub1 = SubjectEntity(
                id = UUID.randomUUID().toString(),
                name = "Computer Science Core",
                code = "CS-101",
                semester = "Semester 1",
                colorHex = "#6C5CE7"
            )
            val sub2 = SubjectEntity(
                id = UUID.randomUUID().toString(),
                name = "Mathematics & Statistics",
                code = "MATH-201",
                semester = "Semester 1",
                colorHex = "#00B4D8"
            )
            val sub3 = SubjectEntity(
                id = UUID.randomUUID().toString(),
                name = "Engineering Physics",
                code = "PHY-102",
                semester = "Semester 1",
                colorHex = "#10B981"
            )

            database.subjectDao().insertSubject(sub1)
            database.subjectDao().insertSubject(sub2)
            database.subjectDao().insertSubject(sub3)

            // Seed common distraction apps for FocusSentry
            val defaultApps = listOf(
                DistractionAppEntity("com.google.android.youtube", "YouTube", isMonitored = true),
                DistractionAppEntity("com.instagram.android", "Instagram", isMonitored = true),
                DistractionAppEntity("com.zhiliaoapp.musically", "TikTok", isMonitored = true),
                DistractionAppEntity("com.twitter.android", "X / Twitter", isMonitored = false),
                DistractionAppEntity("com.netflix.mediaclient", "Netflix", isMonitored = true),
                DistractionAppEntity("com.reddit.frontpage", "Reddit", isMonitored = false)
            )
            defaultApps.forEach { database.distractionAppDao().insertApp(it) }

            // Initial student profile
            database.userProfileDao().insertOrUpdateProfile(
                UserProfileEntity(
                    id = "primary_user",
                    name = "Student",
                    semester = "Semester 1",
                    preferredLanguage = "en-US",
                    studyGoalMinutes = 60,
                    preferredFocusMinutes = 45,
                    isGuestMode = true,
                    notificationsEnabled = true,
                    darkThemeEnabled = true,
                    hasCompletedOnboarding = false
                )
            )
        }
    }
}
