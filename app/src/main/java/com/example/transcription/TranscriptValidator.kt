package com.example.transcription

object TranscriptValidator {

    private val NON_SPEECH_PATTERNS = listOf(
        "no_speech_detected",
        "no speech detected",
        "no speech",
        "[silence]",
        "silence",
        "[applause]",
        "applause",
        "[music]",
        "music",
        "[sound]",
        "[noise]",
        "[inaudible]",
        "inaudible",
        "[blank_audio]",
        "thank you.",
        "thank you",
        "thanks for watching",
        "subtitles by",
        "you"
    )

    data class ValidationResult(
        val isValid: Boolean,
        val reason: String? = null
    )

    fun validate(transcript: String?): ValidationResult {
        if (transcript.isNullOrBlank()) {
            return ValidationResult(false, "No speech detected. Please record the lecture again.")
        }

        val trimmed = transcript.trim()
        val lower = trimmed.lowercase()

        // Check against known silence or non-speech hallucination strings
        for (pattern in NON_SPEECH_PATTERNS) {
            if (lower == pattern || lower == "[$pattern]") {
                return ValidationResult(false, "No speech detected. Please record the lecture again.")
            }
        }

        // Count meaningful words (at least 2 letters, excluding punctuation)
        val words = trimmed.split("\\s+".toRegex())
            .map { it.replace("""[^\p{L}\p{N}]""".toRegex(), "") }
            .filter { it.length >= 2 }

        // An actual lecture or spoken sentence must have at least 3-4 meaningful words
        if (words.size < 3) {
            return ValidationResult(false, "No speech detected. Please record the lecture again.")
        }

        return ValidationResult(true)
    }

    fun isValid(transcript: String?): Boolean = validate(transcript).isValid
}
