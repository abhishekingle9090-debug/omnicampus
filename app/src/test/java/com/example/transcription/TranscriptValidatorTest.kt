package com.example.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptValidatorTest {

    @Test
    fun testEmptyAndBlankTranscriptsAreRejected() {
        assertFalse(TranscriptValidator.isValid(""))
        assertFalse(TranscriptValidator.isValid("   \n\t  "))
        assertEquals("No speech detected. Please record the lecture again.", TranscriptValidator.validate("").reason)
    }

    @Test
    fun testShortPhrasesAreRejected() {
        assertFalse(TranscriptValidator.isValid("hello"))
        assertFalse(TranscriptValidator.isValid("yes sir"))
        assertFalse(TranscriptValidator.isValid("ok then"))
        assertEquals("No speech detected. Please record the lecture again.", TranscriptValidator.validate("ok then").reason)
    }

    @Test
    fun testStandardNoiseAndSilenceTokensAreRejected() {
        val nonSpeechSamples = listOf(
            "[silence]",
            "silence",
            "no_speech_detected",
            "no speech detected",
            "[music]",
            "[applause]",
            "[blank_audio]",
            "thank you.",
            "thank you",
            "thanks for watching"
        )

        for (sample in nonSpeechSamples) {
            val result = TranscriptValidator.validate(sample)
            assertFalse("Sample '$sample' should be rejected", result.isValid)
        }
    }

    @Test
    fun testValidAcademicLecturesAreAccepted() {
        val lectureText = """
            Today we begin our discussion of binary search trees and self balancing tree structures such as AVL trees.
            We will analyze the worst case and average case time complexity of insertion, search, and deletion operations.
            Notice that when a tree becomes degenerate search complexity degrades from log n to linear time.
        """.trimIndent()

        val result = TranscriptValidator.validate(lectureText)
        assertTrue("Valid lecture transcript must be accepted", result.isValid)
    }
}
