package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

import android.content.Context
import com.example.ai.ApiKeyManager

private const val TAG = "GeminiAiService"

data class GeneratedNoteData(
    val title: String,
    val overview: String,
    val keyConcepts: List<String>,
    val definitions: List<Pair<String, String>>, // term to definition
    val examples: List<String>,
    val formulas: List<Triple<String, String, String>>, // name, formula, description
    val importantPoints: List<String>,
    val summary: String,
    val revisionQuestions: List<String>
)

data class GeneratedFlashcardData(
    val question: String,
    val answer: String,
    val type: String, // DEFINITION, CONCEPT, SHORT_ANSWER, TECHNICAL, FORMULA
    val difficulty: String // EASY, MEDIUM, HARD
)

class GeminiAiService(private val context: Context? = null) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Lazy initialization for Firebase AI Logic
    private val firebaseAiModel by lazy {
        try {
            Firebase.ai.generativeModel(
                modelName = AiModelConfig.PRIMARY_MODEL,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Firebase.ai initialization skipped: ${e.message}")
            null
        }
    }

    /**
     * Generates structured lecture notes from the confirmed transcript.
     */
    suspend fun generateNotes(
        transcript: String,
        lectureTitle: String,
        subjectName: String,
        noteStyle: String = "ACADEMIC",
        detailLevel: String = "STANDARD",
        targetLanguage: String = "en-US"
    ): Result<GeneratedNoteData> = withContext(Dispatchers.IO) {
        val validation = com.example.transcription.TranscriptValidator.validate(transcript)
        if (!validation.isValid) {
            return@withContext Result.failure(IllegalArgumentException(validation.reason ?: "No speech detected. Please record the lecture again."))
        }

        val prompt = buildNotePrompt(transcript, lectureTitle, subjectName, noteStyle, detailLevel, targetLanguage)

        // 1. Attempt Firebase AI Logic
        try {
            val model = firebaseAiModel
            if (model != null) {
                val response = withTimeout(60_000L) {
                    model.generateContent(prompt)
                }
                val jsonText = response.text
                if (!jsonText.isNullOrBlank()) {
                    val parsed = parseNoteJson(jsonText, lectureTitle)
                    if (parsed != null) return@withContext Result.success(parsed)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic note generation failed, trying direct API: ${e.message}")
        }

        // 2. Attempt direct Gemini API if key is available
        val apiKey = context?.let { ApiKeyManager.getApiKey(it) } ?: try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            val v = field.get(null) as? String
            if (v.isNullOrBlank() || v == "MY_GEMINI_API_KEY") null else v
        } catch (_: Exception) {
            null
        }

        if (!apiKey.isNullOrBlank()) {
            try {
                val responseJson = callDirectGeminiApi(prompt, apiKey)
                val parsed = parseNoteJson(responseJson, lectureTitle)
                if (parsed != null) return@withContext Result.success(parsed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Direct Gemini API note generation failed: ${e.message}")
            }
        }

        // 3. Resilient Offline Heuristic Extractor fallback
        Log.i(TAG, "Using Offline Heuristic Note Engine for offline/local extraction")
        val localNotes = generateOfflineNotes(transcript, lectureTitle, subjectName, noteStyle)
        Result.success(localNotes)
    }

    /**
     * Generates revision flashcards from the transcript and notes.
     */
    suspend fun generateFlashcards(
        transcript: String,
        notesOverview: String,
        subjectName: String,
        targetLanguage: String = "en-US"
    ): Result<List<GeneratedFlashcardData>> = withContext(Dispatchers.IO) {
        val validation = com.example.transcription.TranscriptValidator.validate(transcript)
        if (!validation.isValid) {
            return@withContext Result.failure(IllegalArgumentException(validation.reason ?: "No speech detected. Please record the lecture again."))
        }

        val prompt = buildFlashcardsPrompt(transcript, notesOverview, subjectName, targetLanguage)

        // 1. Attempt Firebase AI Logic
        try {
            val model = firebaseAiModel
            if (model != null) {
                val response = withTimeout(60_000L) {
                    model.generateContent(prompt)
                }
                val jsonText = response.text
                if (!jsonText.isNullOrBlank()) {
                    val cards = parseFlashcardsJson(jsonText)
                    if (cards.isNotEmpty()) return@withContext Result.success(cards)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic flashcard generation failed: ${e.message}")
        }

        // 2. Attempt direct Gemini API
        val apiKey = context?.let { ApiKeyManager.getApiKey(it) } ?: try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            val v = field.get(null) as? String
            if (v.isNullOrBlank() || v == "MY_GEMINI_API_KEY") null else v
        } catch (_: Exception) {
            null
        }

        if (!apiKey.isNullOrBlank()) {
            try {
                val responseJson = callDirectGeminiApi(prompt, apiKey)
                val cards = parseFlashcardsJson(responseJson)
                if (cards.isNotEmpty()) return@withContext Result.success(cards)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Direct Gemini API flashcards failed: ${e.message}")
            }
        }

        // 3. Fallback heuristic flashcard generator
        val fallbackCards = generateOfflineFlashcards(transcript)
        Result.success(fallbackCards)
    }

    /**
     * Evaluates a student's spoken/text response against the expected answer in FocusSentry.
     * Returns a score (0 to 100), boolean passed, and constructive feedback.
     */
    suspend fun evaluateStudyResponse(
        question: String,
        expectedAnswer: String,
        studentAnswer: String
    ): Triple<Int, Boolean, String> = withContext(Dispatchers.IO) {
        if (studentAnswer.isBlank()) {
            return@withContext Triple(0, false, "No answer provided. Take a moment to review.")
        }

        // Check semantic similarity or key term coverage
        val studentWords = studentAnswer.lowercase().split("\\W+".toRegex()).filter { it.length > 2 }
        val expectedWords = expectedAnswer.lowercase().split("\\W+".toRegex()).filter { it.length > 2 }

        if (expectedWords.isEmpty()) {
            return@withContext Triple(85, true, "Great attempt!")
        }

        val overlap = studentWords.intersect(expectedWords.toSet()).size
        val matchRatio = (overlap.toFloat() / expectedWords.size.coerceAtLeast(1)).coerceIn(0f, 1f)

        val score = when {
            matchRatio >= 0.5f -> 95
            matchRatio >= 0.3f -> 80
            matchRatio >= 0.15f -> 65
            studentWords.size >= 4 -> 55
            else -> 40
        }

        val passed = score >= 60
        val feedback = if (passed) {
            "Verified! You captured the essential concepts. Ready to dive back into your focus session."
        } else {
            "Close attempt! Review the key points: $expectedAnswer"
        }

        Triple(score, passed, feedback)
    }

    private fun resolveLanguageName(codeOrName: String): String {
        return when {
            codeOrName.startsWith("es", ignoreCase = true) -> "Spanish"
            codeOrName.startsWith("fr", ignoreCase = true) -> "French"
            codeOrName.startsWith("de", ignoreCase = true) -> "German"
            codeOrName.startsWith("hi", ignoreCase = true) -> "Hindi"
            codeOrName.startsWith("zh", ignoreCase = true) -> "Mandarin Chinese"
            codeOrName.startsWith("ja", ignoreCase = true) -> "Japanese"
            codeOrName.startsWith("pt", ignoreCase = true) -> "Portuguese"
            codeOrName.startsWith("ar", ignoreCase = true) -> "Arabic"
            codeOrName.startsWith("ru", ignoreCase = true) -> "Russian"
            codeOrName.startsWith("it", ignoreCase = true) -> "Italian"
            codeOrName.startsWith("ko", ignoreCase = true) -> "Korean"
            codeOrName.startsWith("bn", ignoreCase = true) -> "Bengali"
            codeOrName.startsWith("te", ignoreCase = true) -> "Telugu"
            codeOrName.startsWith("mr", ignoreCase = true) -> "Marathi"
            codeOrName.startsWith("ta", ignoreCase = true) -> "Tamil"
            codeOrName.startsWith("ur", ignoreCase = true) -> "Urdu"
            codeOrName.startsWith("en", ignoreCase = true) -> "English"
            else -> codeOrName.ifBlank { "English" }
        }
    }

    private fun buildNotePrompt(
        transcript: String,
        lectureTitle: String,
        subjectName: String,
        noteStyle: String,
        detailLevel: String,
        targetLanguage: String = "en-US"
    ): String {
        val languageName = resolveLanguageName(targetLanguage)
        return """
            You are a distinguished university professor and academic tutor specializing in '$subjectName'.
            Transform this lecture transcript for the subject '$subjectName' into deep, structured, high-yield academic study notes.
            Lecture Title: $lectureTitle
            Requested Style: $noteStyle
            Detail Level: $detailLevel
            Target Output Language: $languageName ($targetLanguage)

            LANGUAGE REQUIREMENT:
            - Write all generated notes (title, overview, key concepts, definitions, examples, formulas description, important points, summary, revision questions) ENTIRELY in $languageName.
            - Standard international mathematical notation and code symbols can remain intact.

            ACADEMIC DEPTH & RIGOR:
            - Provide thorough, deep explanations: unpack core theoretical mechanisms and explain WHY each concept matters.
            - In "examples": Provide 3 to 5 concrete real-world case studies or practical application scenarios based on the lecture topics.
            - In "importantPoints": Highlight critical edge cases, boundary conditions, and common exam pitfalls/misconceptions students encounter.
            - In "formulas": Break down every equation into its name, mathematical expression, and detailed breakdown of each variable/parameter and usage context.
            - In "revisionQuestions": Formulate high-level conceptual questions suitable for university exam preparation.

            ACCURACY RULES:
            - The provided transcript is the EXCLUSIVE source of truth.
            - Do NOT invent facts or concepts not grounded in the transcript.
            - If the transcript has no intelligible educational content or speech, return an empty JSON object with "overview": "NO_CONTENT".

            Return a valid JSON object matching this schema:
            {
              "title": "$lectureTitle",
              "overview": "Comprehensive deep overview of the lecture topic, theoretical context, and significance",
              "keyConcepts": ["List of core takeaways, mechanisms, and principles with clear explanations"],
              "definitions": [{"term": "Key Term", "definition": "Precise, rigorous academic definition"}],
              "examples": ["3 to 5 detailed real-world case studies or practical application scenarios"],
              "formulas": [{"name": "Formula Name", "formula": "Mathematical or algorithmic expression", "description": "Breakdown of variables and practical context"}],
              "importantPoints": ["Edge cases, boundary conditions, and common exam pitfalls"],
              "summary": "Concise synthesis tying the theoretical insights of the lecture together",
              "revisionQuestions": ["Challenging comprehension and exam-preparation questions"]
            }

            Transcript:
            ${transcript.take(12000)}
        """.trimIndent()
    }

    private fun buildFlashcardsPrompt(
        transcript: String,
        notesOverview: String,
        subjectName: String,
        targetLanguage: String = "en-US"
    ): String {
        val languageName = resolveLanguageName(targetLanguage)
        return """
            Create 5 to 8 high-quality active recall revision flashcards based EXCLUSIVELY on this lecture transcript.
            Overview: $notesOverview
            Target Language: $languageName ($targetLanguage)
            Transcript: ${transcript.take(8000)}

            STRICT RULES:
            - Output all questions and answers in $languageName.
            - Generate questions and answers ONLY from concepts and facts explicitly stated in the transcript.
            - Do NOT invent general subject questions outside the transcript text.
            - If the transcript does not contain enough clear concepts, return an empty array: []
            - Vary card types: DEFINITION, CONCEPT, SHORT_ANSWER, TECHNICAL, FORMULA
            - Grade difficulty: EASY, MEDIUM, or HARD
            - Format must be strict JSON array:
            [
              {
                "question": "Clear, direct active recall question",
                "answer": "Concise, precise answer",
                "type": "CONCEPT",
                "difficulty": "MEDIUM"
              }
            ]
        """.trimIndent()
    }

    private fun callDirectGeminiApi(prompt: String, apiKey: String): String {
        val models = AiModelConfig.SUPPORTED_MODELS
        var lastException: Exception? = null

        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val jsonPayload = JSONObject().apply {
                    val contents = JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", prompt) })
                            })
                        })
                    }
                    put("contents", contents)
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val bodyString = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val root = JSONObject(bodyString)
                    val candidates = root.getJSONArray("candidates")
                    val candidate = candidates.getJSONObject(0)
                    val parts = candidate.getJSONObject("content").getJSONArray("parts")
                    val textBuilder = StringBuilder()
                    for (i in 0 until parts.length()) {
                        textBuilder.append(parts.getJSONObject(i).optString("text", ""))
                    }
                    return textBuilder.toString()
                } else {
                    val errJson = try { JSONObject(bodyString).optJSONObject("error") } catch (_: Exception) { null }
                    val serverMsg = errJson?.optString("message", "") ?: response.message
                    Log.w(TAG, "Model $model returned HTTP ${response.code}: $serverMsg")
                    lastException = IllegalStateException("Gemini API HTTP ${response.code}: $serverMsg")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException("Failed to call Gemini API")
    }

    private fun parseNoteJson(jsonString: String, defaultTitle: String): GeneratedNoteData? {
        return try {
            var cleaned = jsonString.trim()
            val firstBrace = cleaned.indexOf('{')
            val lastBrace = cleaned.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace > firstBrace) {
                cleaned = cleaned.substring(firstBrace, lastBrace + 1)
            }
            val obj = JSONObject(cleaned)

            val title = obj.optString("title", defaultTitle)
            val overview = obj.optString("overview", "Lecture overview and synthesized concepts.")

            val keyConcepts = mutableListOf<String>()
            val kcArray = obj.optJSONArray("keyConcepts")
            if (kcArray != null) {
                for (i in 0 until kcArray.length()) keyConcepts.add(kcArray.getString(i))
            }

            val definitions = mutableListOf<Pair<String, String>>()
            val defArray = obj.optJSONArray("definitions")
            if (defArray != null) {
                for (i in 0 until defArray.length()) {
                    val defObj = defArray.getJSONObject(i)
                    definitions.add(defObj.optString("term", "") to defObj.optString("definition", ""))
                }
            }

            val examples = mutableListOf<String>()
            val exArray = obj.optJSONArray("examples")
            if (exArray != null) {
                for (i in 0 until exArray.length()) examples.add(exArray.getString(i))
            }

            val formulas = mutableListOf<Triple<String, String, String>>()
            val formArray = obj.optJSONArray("formulas")
            if (formArray != null) {
                for (i in 0 until formArray.length()) {
                    val fObj = formArray.getJSONObject(i)
                    formulas.add(
                        Triple(
                            fObj.optString("name", ""),
                            fObj.optString("formula", ""),
                            fObj.optString("description", "")
                        )
                    )
                }
            }

            val importantPoints = mutableListOf<String>()
            val ipArray = obj.optJSONArray("importantPoints")
            if (ipArray != null) {
                for (i in 0 until ipArray.length()) importantPoints.add(ipArray.getString(i))
            }

            val summary = obj.optString("summary", "Summary of key lecture principles.")

            val revisionQuestions = mutableListOf<String>()
            val rqArray = obj.optJSONArray("revisionQuestions")
            if (rqArray != null) {
                for (i in 0 until rqArray.length()) revisionQuestions.add(rqArray.getString(i))
            }

            GeneratedNoteData(
                title = title,
                overview = overview,
                keyConcepts = keyConcepts,
                definitions = definitions,
                examples = examples,
                formulas = formulas,
                importantPoints = importantPoints,
                summary = summary,
                revisionQuestions = revisionQuestions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse note JSON: ${e.message}")
            null
        }
    }

    private fun parseFlashcardsJson(jsonString: String): List<GeneratedFlashcardData> {
        val list = mutableListOf<GeneratedFlashcardData>()
        try {
            var cleaned = jsonString.trim()
            val firstBracket = cleaned.indexOf('[')
            val lastBracket = cleaned.lastIndexOf(']')
            if (firstBracket != -1 && lastBracket > firstBracket) {
                cleaned = cleaned.substring(firstBracket, lastBracket + 1)
            }
            val array = JSONArray(cleaned)
            for (i in 0 until array.length()) {
                val cardObj = array.getJSONObject(i)
                list.add(
                    GeneratedFlashcardData(
                        question = cardObj.optString("question", ""),
                        answer = cardObj.optString("answer", ""),
                        type = cardObj.optString("type", "CONCEPT"),
                        difficulty = cardObj.optString("difficulty", "MEDIUM")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse flashcards JSON", e)
        }
        return list
    }

    /**
     * Offline Heuristic Note Engine:
     * Parses sentence structures, identifies definitions (e.g., "is defined as", "refers to"),
     * extracts key concepts and builds comprehensive notes offline without external network dependency.
     */
    private fun generateOfflineNotes(
        transcript: String,
        title: String,
        subject: String,
        style: String
    ): GeneratedNoteData {
        val sentences = transcript.split("(?<=[.!?])\\s+".toRegex()).filter { it.isNotBlank() }
        val overview = if (sentences.isNotEmpty()) {
            "Synthesized local revision notes for $title ($subject). " + sentences.take(3).joinToString(" ")
        } else {
            "Extracted notes based on lecture transcript."
        }

        val keyConcepts = mutableListOf<String>()
        val definitions = mutableListOf<Pair<String, String>>()
        val importantPoints = mutableListOf<String>()

        sentences.forEach { sentence ->
            val lower = sentence.lowercase()
            when {
                lower.contains("is defined as") || lower.contains("refers to") || lower.contains("means that") -> {
                    val parts = sentence.split("is defined as", "refers to", "means that")
                    if (parts.size >= 2) {
                        definitions.add(parts[0].trim() to parts[1].trim())
                    } else {
                        keyConcepts.add(sentence)
                    }
                }
                lower.contains("important") || lower.contains("crucial") || lower.contains("remember") || lower.contains("note that") -> {
                    importantPoints.add(sentence)
                }
                lower.contains("because") || lower.contains("therefore") || lower.contains("in order to") -> {
                    keyConcepts.add(sentence)
                }
            }
        }

        if (keyConcepts.isEmpty()) {
            keyConcepts.addAll(sentences.take(5))
        }
        if (importantPoints.isEmpty() && sentences.size > 2) {
            importantPoints.add("Review the primary mechanisms outlined in the beginning of this lecture.")
        }

        val summary = if (sentences.size > 2) {
            sentences.takeLast(2).joinToString(" ")
        } else {
            "Core principles and definitions documented from the recorded session."
        }

        val revisionQuestions = listOf(
            "What is the central problem or topic addressed in '$title'?",
            "Explain the key definitions and how they relate to $subject.",
            "How do the principles covered in this lecture apply to practical engineering scenarios?"
        )

        return GeneratedNoteData(
            title = title,
            overview = overview,
            keyConcepts = keyConcepts,
            definitions = definitions,
            examples = listOf("Academic discussion and analytical examples referenced in the recorded discourse."),
            formulas = emptyList(),
            importantPoints = importantPoints,
            summary = summary,
            revisionQuestions = revisionQuestions
        )
    }

    private fun generateOfflineFlashcards(transcript: String): List<GeneratedFlashcardData> {
        val sentences = transcript.split("(?<=[.!?])\\s+".toRegex()).filter { it.isNotBlank() }
        val cards = mutableListOf<GeneratedFlashcardData>()

        sentences.forEach { sentence ->
            val lower = sentence.lowercase()
            if (lower.contains("is defined as") || lower.contains("refers to")) {
                val parts = sentence.split("is defined as", "refers to")
                if (parts.size >= 2) {
                    cards.add(
                        GeneratedFlashcardData(
                            question = "What is ${parts[0].trim()}?",
                            answer = parts[1].trim(),
                            type = "DEFINITION",
                            difficulty = "EASY"
                        )
                    )
                }
            }
        }

        if (cards.isEmpty() && sentences.size >= 2) {
            // Only extract if there is actual substantial content
            val firstSentence = sentences.first()
            if (firstSentence.length > 20) {
                cards.add(
                    GeneratedFlashcardData(
                        question = "Key concept from lecture opening:",
                        answer = firstSentence,
                        type = "CONCEPT",
                        difficulty = "MEDIUM"
                    )
                )
            }
        }

        return cards
    }
}
