package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.OmniCampusDatabase
import com.example.data.local.entity.LectureEntity
import com.example.data.local.entity.SubjectEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var database: OmniCampusDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OmniCampusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun testAppNameResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("OmniCampus AI", appName)
    }

    @Test
    fun testSubjectAndLecturePersistence() = runBlocking {
        val subId = UUID.randomUUID().toString()
        val subject = SubjectEntity(
            id = subId,
            name = "Computer Architecture",
            code = "CS-301",
            semester = "Fall 2026",
            colorHex = "#6C5CE7"
        )
        database.subjectDao().insertSubject(subject)

        val retrievedSub = database.subjectDao().getSubjectById(subId)
        assertNotNull(retrievedSub)
        assertEquals("Computer Architecture", retrievedSub?.name)

        val lectureId = UUID.randomUUID().toString()
        val lecture = LectureEntity(
            id = lectureId,
            subjectId = subId,
            title = "Pipelining and Branch Prediction",
            topic = "Instruction Level Parallelism",
            durationSeconds = 3600L,
            status = "RECORDED"
        )
        database.lectureDao().insertLecture(lecture)

        val retrievedLecture = database.lectureDao().getLectureById(lectureId)
        assertNotNull(retrievedLecture)
        assertEquals("Pipelining and Branch Prediction", retrievedLecture?.title)
    }
}
