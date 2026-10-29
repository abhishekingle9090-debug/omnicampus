package com.example

import com.example.transcription.TranscriptCorrectionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testAcousticConfusionDetection() {
        val sampleTranscript = "hi class welcome to raft consensus and cpu architecture"
        val diffs = TranscriptCorrectionHelper.analyzeTranscriptForReview(sampleTranscript)

        // Verify diffs are captured without modifying original
        val changedWords = diffs.filter { it.isChanged }
        assertTrue("Expected acoustic suggestions for known technical/confusion words", changedWords.isNotEmpty())

        val paxosDiff = diffs.firstOrNull { it.originalWord.equals("raft", ignoreCase = true) }
        assertEquals("Raft", paxosDiff?.suggestedWord)
    }

    @Test
    fun testSelectiveWordAcceptance() {
        val original = "hi student"
        val diffs = TranscriptCorrectionHelper.analyzeTranscriptForReview(original)
        val markedDiffs = diffs.mapIndexed { index, item ->
            if (index == 0) item.copy(isAccepted = true) else item
        }
        val corrected = TranscriptCorrectionHelper.applyAcceptedCorrections(markedDiffs)
        assertEquals("bye student", corrected)
    }
}
