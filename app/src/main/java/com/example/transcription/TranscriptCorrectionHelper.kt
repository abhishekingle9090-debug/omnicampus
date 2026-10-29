package com.example.transcription

data class WordDiff(
    val originalWord: String,
    val suggestedWord: String,
    val position: Int,
    val isChanged: Boolean = false,
    val isAccepted: Boolean = false,
    val isRejected: Boolean = false,
    val reason: String? = null
)

object TranscriptCorrectionHelper {

    /**
     * Common phonetic and acoustic confusion pairs in academic and engineering lectures
     */
    private val commonAcousticConfusions = mapOf(
        "hi" to "bye",
        "bye" to "hi",
        "there" to "their",
        "paxos" to "Paxos",
        "raft" to "Raft",
        "cpu" to "CPU",
        "io" to "I/O",
        "sql" to "SQL",
        "nosql" to "NoSQL",
        "http" to "HTTP",
        "rest" to "REST",
        "json" to "JSON",
        "api" to "API",
        "ram" to "RAM",
        "quorum" to "Quorum",
        "mutex" to "Mutex",
        "semaphore" to "Semaphore",
        "thread" to "Thread",
        "socket" to "Socket"
    )

    /**
     * Generates a separate list of suggested word replacements without altering the original text.
     * Keeps original intact and lets the student inspect and approve each word.
     */
    fun analyzeTranscriptForReview(text: String): List<WordDiff> {
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val diffList = mutableListOf<WordDiff>()

        words.forEachIndexed { index, rawWord ->
            val cleanWord = rawWord.trim('.', ',', ':', ';', '!', '?', '"', '\'')
            val cleanLower = cleanWord.lowercase()

            val suggestion = commonAcousticConfusions[cleanLower]

            if (suggestion != null && suggestion != cleanWord) {
                // Re-apply punctuation if any
                val prefix = rawWord.takeWhile { !it.isLetterOrDigit() }
                val suffix = rawWord.takeLastWhile { !it.isLetterOrDigit() }
                val formattedSuggestion = "$prefix$suggestion$suffix"

                diffList.add(
                    WordDiff(
                        originalWord = rawWord,
                        suggestedWord = formattedSuggestion,
                        position = index,
                        isChanged = true,
                        reason = "Acoustic or technical term formatting clarification"
                    )
                )
            } else {
                diffList.add(
                    WordDiff(
                        originalWord = rawWord,
                        suggestedWord = rawWord,
                        position = index,
                        isChanged = false
                    )
                )
            }
        }
        return diffList
    }

    /**
     * Applies only user-accepted replacements to construct the confirmed transcript.
     */
    fun applyAcceptedCorrections(diffs: List<WordDiff>): String {
        return diffs.joinToString(" ") { diff ->
            if (diff.isAccepted) diff.suggestedWord else diff.originalWord
        }
    }
}
