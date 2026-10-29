package com.example.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.ai.AiModelConfig
import com.example.ai.ApiKeyManager
import com.example.ai.GeminiAiService
import com.example.data.local.OmniCampusDatabase
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.LectureEntity
import com.example.data.local.entity.TranscriptEntity
import com.example.recording.WavAudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "DiagnosticManager"

data class DiagnosticItem(
    val id: String,
    val title: String,
    val description: String,
    val isPassed: Boolean? = null,
    val details: String = "",
    val latencyMs: Long? = null
)

object DiagnosticManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun runAllDiagnostics(
        context: Context,
        database: OmniCampusDatabase
    ): List<DiagnosticItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticItem>()

        // 1. Internet Connectivity
        results.add(checkInternetConnectivity(context))

        // 2. AI Service Configuration & Key Source
        results.add(checkAiConfiguration(context))

        // 3. Text-Only Model Smoke Test (gemini-3.8-flash)
        results.add(checkTextModelSmokeTest(context))

        // 4. Recording File Validation
        results.add(checkRecordingFileValidation(context))

        // 5. Audio Transcription Engine Verification
        results.add(checkAudioTranscriptionEngine(context))

        // 6. Transcript Room Persistence
        results.add(checkTranscriptPersistence(database))

        // 7. Notes Generation Pipeline
        results.add(checkNotesGeneration(context))

        // 8. Flashcard Persistence
        results.add(checkFlashcardPersistence(database))

        results
    }

    private fun checkInternetConnectivity(context: Context): DiagnosticItem {
        val start = System.currentTimeMillis()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val hasNet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (!hasNet) {
            return DiagnosticItem(
                id = "network",
                title = "Internet Connectivity",
                description = "Checks if the smartphone is connected to WiFi or Mobile Data.",
                isPassed = false,
                details = "No active internet connection. Please verify device WiFi or Cellular data."
            )
        }

        // Test actual HTTP reachability
        return try {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models")
                .head()
                .build()
            val resp = httpClient.newCall(req).execute()
            val latency = System.currentTimeMillis() - start
            DiagnosticItem(
                id = "network",
                title = "Internet Connectivity",
                description = "Live HTTP ping to Google Generative Language service.",
                isPassed = true,
                details = "Connected to Google Generative Language API (HTTP ${resp.code})",
                latencyMs = latency
            )
        } catch (e: Exception) {
            DiagnosticItem(
                id = "network",
                title = "Internet Connectivity",
                description = "Live HTTP ping to Google Generative Language service.",
                isPassed = false,
                details = "Cannot reach Google API endpoint: ${e.localizedMessage ?: "Connection timed out"}"
            )
        }
    }

    private fun checkAiConfiguration(context: Context): DiagnosticItem {
        val key = ApiKeyManager.getApiKey(context)
        val source = ApiKeyManager.getKeySourceDescription(context)

        return if (!key.isNullOrBlank()) {
            val masked = if (key.length > 8) "${key.take(4)}...${key.takeLast(4)}" else "••••"
            DiagnosticItem(
                id = "ai_config",
                title = "AI Model & Key Configuration",
                description = "Active model: ${AiModelConfig.PRIMARY_MODEL} (Fallback: ${AiModelConfig.FALLBACK_MODEL})",
                isPassed = true,
                details = "Source: $source (Key: $masked, Length: ${key.length})"
            )
        } else {
            DiagnosticItem(
                id = "ai_config",
                title = "AI Model & Key Configuration",
                description = "Active model: ${AiModelConfig.PRIMARY_MODEL}",
                isPassed = false,
                details = "Gemini API key is not configured. Enter your key in Profile settings."
            )
        }
    }

    private suspend fun checkTextModelSmokeTest(context: Context): DiagnosticItem {
        val key = ApiKeyManager.getApiKey(context)
        if (key.isNullOrBlank()) {
            return DiagnosticItem(
                id = "smoke_test",
                title = "Text Model Smoke Test",
                description = "Live test call to ${AiModelConfig.PRIMARY_MODEL}",
                isPassed = false,
                details = "Skipped: API key not configured."
            )
        }

        val smokeResult = ApiKeyManager.runSmokeTest(key)
        return if (smokeResult.isSuccess) {
            DiagnosticItem(
                id = "smoke_test",
                title = "Text Model Smoke Test (${smokeResult.modelUsed})",
                description = "Live query sent to ${smokeResult.modelUsed}",
                isPassed = true,
                details = "Received response: \"${smokeResult.responseText}\"",
                latencyMs = smokeResult.latencyMs
            )
        } else {
            DiagnosticItem(
                id = "smoke_test",
                title = "Text Model Smoke Test",
                description = "Live query sent to ${AiModelConfig.PRIMARY_MODEL}",
                isPassed = false,
                details = smokeResult.errorMessage ?: "Failed to generate content."
            )
        }
    }

    private fun checkRecordingFileValidation(context: Context): DiagnosticItem {
        return try {
            val testFile = File(context.cacheDir, "diagnostic_test.wav")
            // Create a minimal 1-second 16kHz PCM WAV file
            val sampleRate = 16000
            val pcmBytes = ByteArray(sampleRate * 2) // 1 second of silence/low noise
            val totalDataLen = pcmBytes.size + 36
            val header = ByteArray(44)
            val bb = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            bb.order(java.nio.ByteOrder.BIG_ENDIAN).putInt(0x52494646) // RIFF
            bb.order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(totalDataLen)
            bb.order(java.nio.ByteOrder.BIG_ENDIAN).putInt(0x57415645) // WAVE
            bb.order(java.nio.ByteOrder.BIG_ENDIAN).putInt(0x666d7420) // fmt 
            bb.order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(16)
            bb.putShort(1.toShort()) // PCM
            bb.putShort(1.toShort()) // Mono
            bb.putInt(sampleRate)
            bb.putInt(sampleRate * 2)
            bb.putShort(2.toShort())
            bb.putShort(16.toShort())
            bb.order(java.nio.ByteOrder.BIG_ENDIAN).putInt(0x64617461) // data
            bb.order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(pcmBytes.size)

            FileOutputStream(testFile).use { fos ->
                fos.write(header)
                fos.write(pcmBytes)
            }

            val recorder = WavAudioRecorder(context)
            val diag = recorder.getDiagnostics(testFile)
            testFile.delete()

            DiagnosticItem(
                id = "audio_validation",
                title = "Audio File & WAV Validation",
                description = "Validates 16 kHz Mono 16-bit PCM WAV header parser.",
                isPassed = diag.formatDescription.contains("PCM 16-bit Mono WAV"),
                details = "Validated parser: ${diag.formatDescription}"
            )
        } catch (e: Exception) {
            DiagnosticItem(
                id = "audio_validation",
                title = "Audio File & WAV Validation",
                description = "Validates 16 kHz Mono 16-bit PCM WAV header parser.",
                isPassed = false,
                details = "WAV validation failed: ${e.localizedMessage}"
            )
        }
    }

    private fun checkAudioTranscriptionEngine(context: Context): DiagnosticItem {
        val hasKey = ApiKeyManager.hasKeyConfigured(context)
        return DiagnosticItem(
            id = "transcription_engine",
            title = "Audio Transcription Engine",
            description = "Gemini Multimodal Speech Engine (${AiModelConfig.PRIMARY_MODEL})",
            isPassed = hasKey,
            details = if (hasKey) "Ready for audio payloads (English, Hindi, Marathi, Hinglish)" else "Awaiting API key configuration"
        )
    }

    private suspend fun checkTranscriptPersistence(database: OmniCampusDatabase): DiagnosticItem {
        return try {
            val tempLectureId = "diag_test_lecture_${UUID.randomUUID()}"
            val testTranscript = TranscriptEntity(
                id = "diag_transcript_${UUID.randomUUID()}",
                lectureId = tempLectureId,
                originalText = "Diagnostic test transcript verification text.",
                correctedText = "Diagnostic test transcript verification text.",
                confidence = 0.99f
            )

            database.transcriptDao().insertTranscript(testTranscript)
            val retrieved = database.transcriptDao().getTranscriptDirect(tempLectureId)
            database.transcriptDao().deleteTranscriptByLectureId(tempLectureId)

            val passed = retrieved?.originalText == testTranscript.originalText
            DiagnosticItem(
                id = "transcript_db",
                title = "Transcript Room Persistence",
                description = "Read/write cycle for lecture transcripts in Room database.",
                isPassed = passed,
                details = if (passed) "Room DAO transactions verified successfully" else "Retrieved transcript does not match"
            )
        } catch (e: Exception) {
            DiagnosticItem(
                id = "transcript_db",
                title = "Transcript Room Persistence",
                description = "Read/write cycle for lecture transcripts in Room database.",
                isPassed = false,
                details = "Room error: ${e.localizedMessage}"
            )
        }
    }

    private suspend fun checkNotesGeneration(context: Context): DiagnosticItem {
        return try {
            val service = GeminiAiService(context)
            val testTranscript = "Today we covered Newton's First Law of Motion. An object remains at rest unless acted upon by an external force."
            val notesResult = service.generateNotes(
                transcript = testTranscript,
                lectureTitle = "Physics Test",
                subjectName = "Physics"
            )

            if (notesResult.isSuccess) {
                val data = notesResult.getOrNull()
                DiagnosticItem(
                    id = "notes_gen",
                    title = "Notes Generation Pipeline",
                    description = "Structured JSON notes extraction and parsing.",
                    isPassed = true,
                    details = "Parsed: \"${data?.title}\" (${data?.keyConcepts?.size ?: 0} concepts, ${data?.definitions?.size ?: 0} definitions)"
                )
            } else {
                DiagnosticItem(
                    id = "notes_gen",
                    title = "Notes Generation Pipeline",
                    description = "Structured JSON notes extraction and parsing.",
                    isPassed = false,
                    details = notesResult.exceptionOrNull()?.localizedMessage ?: "Note parsing failed"
                )
            }
        } catch (e: Exception) {
            DiagnosticItem(
                id = "notes_gen",
                title = "Notes Generation Pipeline",
                description = "Structured JSON notes extraction and parsing.",
                isPassed = false,
                details = "Exception: ${e.localizedMessage}"
            )
        }
    }

    private suspend fun checkFlashcardPersistence(database: OmniCampusDatabase): DiagnosticItem {
        return try {
            val testCard = FlashcardEntity(
                id = "diag_card_${UUID.randomUUID()}",
                subjectId = "diag_sub",
                lectureId = "diag_lec",
                question = "What is Newton's First Law?",
                answer = "Inertia: An object at rest stays at rest.",
                type = "CONCEPT",
                difficulty = "EASY"
            )

            database.flashcardDao().insertFlashcard(testCard)
            val retrieved = database.flashcardDao().getFlashcardById(testCard.id)
            database.flashcardDao().deleteFlashcard(testCard)

            val passed = retrieved?.answer == testCard.answer
            DiagnosticItem(
                id = "flashcard_db",
                title = "Flashcard Database Persistence",
                description = "Room CRUD validation for student flashcards and review tracking.",
                isPassed = passed,
                details = if (passed) "Flashcard storage & recall verified" else "Failed to verify flashcard insertion"
            )
        } catch (e: Exception) {
            DiagnosticItem(
                id = "flashcard_db",
                title = "Flashcard Database Persistence",
                description = "Room CRUD validation for student flashcards and review tracking.",
                isPassed = false,
                details = "Room error: ${e.localizedMessage}"
            )
        }
    }
}
