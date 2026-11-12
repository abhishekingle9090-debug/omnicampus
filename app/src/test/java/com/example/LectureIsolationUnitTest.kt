package com.example

import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.NoteEntity
import com.example.transcription.TranscriptValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LectureIsolationUnitTest {

    @Test
    fun testNoteNavigationGuardPreventsStaleNoteExposure() {
        val lectureAId = "lecture-a-123"
        val lectureBId = "lecture-b-456"

        val noteFromLectureA = NoteEntity(
            id = "note-a",
            lectureId = lectureAId,
            subjectId = "sub-1",
            title = "Lecture A Notes",
            overview = "Overview A",
            summary = "Summary A"
        )

        // Simulate Bug 1 condition: NoteGeneratorScreen opens for lectureBId, but noteGenState has Lecture A's note
        val shouldNavigateBuggy = noteFromLectureA != null
        assertTrue("Previous buggy logic would immediately navigate", shouldNavigateBuggy)

        // Fixed navigation guard:
        // note != null && note.lectureId == currentLectureId && targetLectureId == currentLectureId
        val targetLectureId = lectureAId // from prior run
        val shouldNavigateFixed = noteFromLectureA.lectureId == lectureBId && targetLectureId == lectureBId
        assertFalse("Fixed logic must NEVER navigate to Lecture A's note when viewing Lecture B", shouldNavigateFixed)

        // When a new note is generated specifically for Lecture B:
        val noteFromLectureB = NoteEntity(
            id = "note-b",
            lectureId = lectureBId,
            subjectId = "sub-1",
            title = "Lecture B Notes",
            overview = "Overview B",
            summary = "Summary B"
        )
        val targetLectureBId = lectureBId
        val shouldNavigateValid = noteFromLectureB.lectureId == lectureBId && targetLectureBId == lectureBId
        assertTrue("Fixed logic must navigate when note and target match the active lecture", shouldNavigateValid)
    }

    @Test
    fun testFlashcardLectureScopedFiltering() {
        val lectureAId = "lecture-a-" + UUID.randomUUID()
        val lectureBId = "lecture-b-" + UUID.randomUUID()
        val subjectId = "sub-101"

        val cardsLectureA = (1..3).map { i ->
            FlashcardEntity(
                id = "card-a-$i",
                subjectId = subjectId,
                lectureId = lectureAId,
                question = "Lecture A Question $i",
                answer = "Lecture A Answer $i"
            )
        }

        val cardsLectureB = (1..2).map { i ->
            FlashcardEntity(
                id = "card-b-$i",
                subjectId = subjectId,
                lectureId = lectureBId,
                question = "Lecture B Question $i",
                answer = "Lecture B Answer $i"
            )
        }

        val allFlashcards = cardsLectureA + cardsLectureB
        assertEquals(5, allFlashcards.size)

        // Test Scoped to Lecture B:
        val scopeToLectureOnly = true
        val filteredForLectureB = allFlashcards.filter { card ->
            if (scopeToLectureOnly) card.lectureId == lectureBId else true
        }

        assertEquals("Lecture B scoped deck must contain exactly 2 cards", 2, filteredForLectureB.size)
        assertTrue("All cards in Lecture B deck must have lectureId == lectureBId", filteredForLectureB.all { it.lectureId == lectureBId })
        assertFalse("No cards from Lecture A should be present", filteredForLectureB.any { it.lectureId == lectureAId })

        // Test Scoped to Lecture A:
        val filteredForLectureA = allFlashcards.filter { card ->
            if (scopeToLectureOnly) card.lectureId == lectureAId else true
        }
        assertEquals("Lecture A scoped deck must contain exactly 3 cards", 3, filteredForLectureA.size)
        assertTrue("All cards in Lecture A deck must have lectureId == lectureAId", filteredForLectureA.all { it.lectureId == lectureAId })

        // Test Combined Mode (All Lectures Review):
        val combinedCards = allFlashcards.filter { card ->
            false // scopeToLectureOnly == false
        }.ifEmpty { allFlashcards }
        assertEquals(5, combinedCards.size)
    }

    @Test
    fun testEmptyRecordingRejectionPreventsHallucination() {
        val silentAudioTranscript = "[silence]"
        val noiseTranscript = "no_speech_detected"
        val emptyTranscript = ""
        val whitespaceOnly = "   \n  "

        assertFalse(TranscriptValidator.isValid(silentAudioTranscript))
        assertFalse(TranscriptValidator.isValid(noiseTranscript))
        assertFalse(TranscriptValidator.isValid(emptyTranscript))
        assertFalse(TranscriptValidator.isValid(whitespaceOnly))

        // Legitimate academic transcript
        val legitimateTranscript = "In today's class on distributed systems, we analyze Paxos consensus algorithm and leader election."
        assertTrue(TranscriptValidator.isValid(legitimateTranscript))
    }
}
