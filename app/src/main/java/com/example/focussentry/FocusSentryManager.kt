package com.example.focussentry

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.ai.GeminiAiService
import com.example.data.local.entity.FocusSessionEntity
import com.example.data.local.entity.LectureEntity
import com.example.data.repository.OmniCampusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

private const val TAG = "FocusSentryManager"

data class FocusSessionState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val sessionId: String? = null,
    val subjectId: String? = null,
    val subjectName: String = "",
    val goal: String = "",
    val plannedMinutes: Int = 25,
    val elapsedSeconds: Long = 0L,
    val remainingSeconds: Long = 25 * 60L,
    val distractionCount: Int = 0,
    val recentDistractionApp: String? = null,
    val showDistractionAlert: Boolean = false,
    val isVerificationActive: Boolean = false,
    val verificationQuestion: String = "",
    val verificationExpectedAnswer: String = "",
    val verificationScore: Int? = null,
    val verificationPassed: Boolean? = null,
    val verificationFeedback: String? = null,
    val isOverlayLocked: Boolean = false
)

class FocusSentryManager(
    private val context: Context,
    private val repository: OmniCampusRepository,
    private val aiService: GeminiAiService
) : TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timerJob: Job? = null
    private var monitorJob: Job? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager }

    // WindowManager Overlay View & Lifecycle
    private var overlayView: View? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    // Anti-Hang and Temporary Unlock State
    @Volatile
    private var isCurrentlyUnlocked: Boolean = false
    @Volatile
    private var unlockedUntilTimestamp: Long = 0L
    @Volatile
    private var lastUnlockedApp: String? = null

    // Current Overlay Context
    private var currentBlockedApp: String = ""
    private var currentQuestionText: String = ""
    private var currentExpectedAnswer: String = ""
    private var currentKeywords: List<String> = emptyList()
    private var currentLectureTitle: String? = null
    private var isFallbackQuestion: Boolean = false

    // Overlay Interactive State
    private var isListening: Boolean = false
    private var recognizedSpeechText: String = ""
    private var speechStatusMessage: String = "Tap mic to speak your answer"
    private var overlayErrorMessage: String? = null

    // SpeechRecognizer for in-overlay speech input
    private var speechRecognizer: SpeechRecognizer? = null

    private val _sessionState = MutableStateFlow(FocusSessionState())
    val sessionState: StateFlow<FocusSessionState> = _sessionState.asStateFlow()

    init {
        try {
            textToSpeech = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.w(TAG, "TextToSpeech init exception: ${e.message}")
        }
        restoreActiveSession()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            isTtsReady = true
        }
    }

    // ================= PERMISSION CHECKS & INTENTS =================

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageAccessIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    // ================= REAL-TIME WINDOWMANAGER LOCK OVERLAY =================

    fun showLockOverlay(blockedApp: String) {
        scope.launch(Dispatchers.Main) {
            if (!_sessionState.value.isActive || _sessionState.value.isPaused) {
                return@launch
            }

            // Anti-Hang check: Do not re-trigger if temporarily unlocked for this app
            val now = System.currentTimeMillis()
            if (isCurrentlyUnlocked && now < unlockedUntilTimestamp) {
                Log.d(TAG, "App $blockedApp is temporarily unlocked until $unlockedUntilTimestamp. Skipping overlay.")
                return@launch
            }

            if (!hasOverlayPermission()) {
                Log.w(TAG, "Cannot show WindowManager overlay: SYSTEM_ALERT_WINDOW permission not granted.")
                Toast.makeText(
                    context,
                    "FocusSentry: Please grant 'Display over other apps' to block distracting apps.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            // If overlay is already active for this app, do not recreate
            if (overlayView != null) {
                return@launch
            }

            currentBlockedApp = blockedApp

            // Fetch dynamic context from Room DB
            val (lecture, transcript) = withContext(Dispatchers.IO) {
                val latestLec = repository.getLatestLecture()
                val latestTrans = if (latestLec != null) {
                    repository.getTranscriptDirect(latestLec.id) ?: repository.getLatestTranscript()
                } else {
                    repository.getLatestTranscript()
                }
                Pair(latestLec, latestTrans)
            }

            val transcriptText = transcript?.correctedText?.ifBlank { transcript.originalText } ?: ""

            if (lecture != null && transcriptText.isNotBlank()) {
                currentLectureTitle = lecture.title
                currentKeywords = extractKeywordsFromTranscript(transcriptText, lecture.title, lecture.topic)
                currentQuestionText = "Recall Challenge: What were the key concepts discussed in '${lecture.title}'?"
                currentExpectedAnswer = transcriptText
                isFallbackQuestion = false
            } else {
                // Fallback question when no lecture exists in DB
                currentLectureTitle = null
                currentKeywords = emptyList()
                currentQuestionText = "Please solve this to unlock: What is 2+2?"
                currentExpectedAnswer = "4"
                isFallbackQuestion = true
            }

            // Reset interactive state
            isListening = false
            recognizedSpeechText = ""
            speechStatusMessage = "Tap mic to speak your answer"
            overlayErrorMessage = null

            renderOverlayView()

            if (isTtsReady) {
                val speechPrompt = if (!isFallbackQuestion && currentLectureTitle != null) {
                    "FocusSentry Active. Distraction blocked. Answer the recall question to unlock."
                } else {
                    "FocusSentry Active. Distraction blocked. Solve the challenge to unlock."
                }
                textToSpeech?.speak(speechPrompt, TextToSpeech.QUEUE_FLUSH, null, "lock_alert")
            }
        }
    }

    private fun renderOverlayView() {
        try {
            val wm = windowManager ?: return

            // Safely remove any existing view
            overlayView?.let { oldView ->
                try {
                    wm.removeView(oldView)
                } catch (_: Exception) {}
            }
            overlayLifecycleOwner?.destroy()

            val lifecycleOwner = OverlayLifecycleOwner()
            overlayLifecycleOwner = lifecycleOwner

            val view = createFocusOverlayComposeView(
                context = context,
                lifecycleOwner = lifecycleOwner,
                blockedAppName = currentBlockedApp,
                questionText = currentQuestionText,
                lectureTitle = currentLectureTitle,
                keywords = currentKeywords,
                isListeningState = isListening,
                recognizedSpeechText = recognizedSpeechText,
                speechStatusMessage = speechStatusMessage,
                errorMessage = overlayErrorMessage,
                onTextSubmit = { answer -> submitAnswer(answer) },
                onMicStart = { startVoiceUnlockListening() },
                onMicStop = { stopListening() },
                onMicSubmit = { answer -> submitAnswer(answer) },
                onReturnToStudy = { returnToStudySession() },
                onEmergencyBypass = { emergencyBypassUnlock() }
            )

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

            wm.addView(view, params)
            overlayView = view
            _sessionState.value = _sessionState.value.copy(isOverlayLocked = true)
            Log.i(TAG, "FocusSentry lock overlay attached successfully over $currentBlockedApp")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render WindowManager lock overlay: ${e.message}", e)
            overlayView = null
        }
    }

    fun hideLockOverlay() {
        scope.launch(Dispatchers.Main) {
            stopListening()
            try {
                overlayView?.let { view ->
                    windowManager?.removeView(view)
                    Log.i(TAG, "FocusSentry lock overlay view removed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing lock overlay view: ${e.message}")
            } finally {
                overlayLifecycleOwner?.destroy()
                overlayLifecycleOwner = null
                overlayView = null
                _sessionState.value = _sessionState.value.copy(isOverlayLocked = false)
            }
        }
    }

    // ================= UNLOCK & SUBMISSION LOGIC =================

    fun submitAnswer(userAnswer: String) {
        scope.launch(Dispatchers.Main) {
            val answer = userAnswer.trim()
            if (answer.isBlank()) {
                overlayErrorMessage = "Please type or speak an answer to unlock."
                renderOverlayView()
                return@launch
            }

            val isCorrect = evaluateAnswer(answer)

            if (isCorrect) {
                Log.i(TAG, "Correct answer provided: '$answer'. Unlocking app.")

                // Show green Toast
                Toast.makeText(context, "Great job! App Unlocked.", Toast.LENGTH_LONG).show()

                if (isTtsReady) {
                    textToSpeech?.speak("Great job! App Unlocked.", TextToSpeech.QUEUE_FLUSH, null, "unlocked_tts")
                }

                // CRITICAL FIX: Set isCurrentlyUnlocked flag for the next 5 minutes
                // so the background monitoring service doesn't immediately re-trigger and freeze the phone
                isCurrentlyUnlocked = true
                unlockedUntilTimestamp = System.currentTimeMillis() + (5 * 60 * 1000L) // 5 minutes unlock
                lastUnlockedApp = currentBlockedApp

                // CRITICAL FIX: Safely remove overlay view and reset state
                hideLockOverlay()
            } else {
                Log.i(TAG, "Incorrect answer provided: '$answer'")

                overlayErrorMessage = "Incorrect answer. Focus mode remains active."
                recognizedSpeechText = ""
                speechStatusMessage = "Try again or type your answer"

                if (isTtsReady) {
                    textToSpeech?.speak("Incorrect answer. Focus mode remains active.", TextToSpeech.QUEUE_FLUSH, null, "incorrect_tts")
                }

                // Keep overlay visible and refresh view with error message
                renderOverlayView()
            }
        }
    }

    private fun evaluateAnswer(answer: String): Boolean {
        if (isFallbackQuestion) {
            // Check for math problem 2+2
            val clean = answer.lowercase().replace("[^a-z0-9]".toRegex(), "")
            return clean == "4" || clean == "four" || clean.contains("4") || clean.contains("four")
        }

        // Compare answer with lecture keywords and expected content
        val cleanAnswerWords = answer.lowercase()
            .replace("[^a-zA-Z0-9 ]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.length >= 3 }
            .toSet()

        if (cleanAnswerWords.isEmpty()) return false

        // Check match against keywords
        var keywordMatches = 0
        for (word in cleanAnswerWords) {
            if (currentKeywords.any { kw -> kw.equals(word, ignoreCase = true) || (word.length > 4 && kw.contains(word)) || (kw.length > 4 && word.contains(kw)) }) {
                keywordMatches++
            } else if (currentLectureTitle != null && currentLectureTitle!!.contains(word, ignoreCase = true)) {
                keywordMatches++
            }
        }

        // Check match against expected answer words (Match score > 50%)
        val expectedWords = currentExpectedAnswer.lowercase()
            .replace("[^a-zA-Z0-9 ]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.length >= 4 && !STOP_WORDS.contains(it) }
            .toSet()

        val commonWords = cleanAnswerWords.intersect(expectedWords)
        val matchScore = if (cleanAnswerWords.isNotEmpty()) {
            commonWords.size.toFloat() / cleanAnswerWords.size.toFloat()
        } else {
            0f
        }

        return keywordMatches >= 2 || matchScore >= 0.5f || (currentKeywords.size <= 2 && keywordMatches >= 1)
    }

    private fun returnToStudySession() {
        scope.launch(Dispatchers.Main) {
            hideLockOverlay()
            bringAppToForeground()
        }
    }

    private fun emergencyBypassUnlock() {
        scope.launch(Dispatchers.Main) {
            isCurrentlyUnlocked = true
            unlockedUntilTimestamp = System.currentTimeMillis() + (3 * 60 * 1000L)
            hideLockOverlay()
            bringAppToForeground()
            Toast.makeText(context, "Unlocked. Returned to Study Session.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bringAppToForeground() {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch MainActivity: ${e.message}")
        }
    }

    // ================= SPEECH RECOGNIZER OPTIMIZATION =================

    private fun startVoiceUnlockListening() {
        if (isListening) {
            stopListening()
            return
        }

        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            overlayErrorMessage = "Microphone permission required for voice unlock."
            renderOverlayView()
            Toast.makeText(context, "Microphone permission required.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer unavailable.")
            overlayErrorMessage = "Speech recognition unavailable. Please type your answer."
            renderOverlayView()
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        speechStatusMessage = "Listening... Speak your answer now!"
                        overlayErrorMessage = null
                        mainHandler.post { renderOverlayView() }
                    }

                    override fun onBeginningOfSpeech() {
                        speechStatusMessage = "Hearing your voice..."
                        mainHandler.post { renderOverlayView() }
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        speechStatusMessage = "Finished listening. Tap 'Send Answer' or review below."
                        mainHandler.post { renderOverlayView() }
                    }

                    override fun onError(errorCode: Int) {
                        isListening = false
                        val errorText = when (errorCode) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try again or type."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out. Try speaking again."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio error. Please type your answer."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission denied."
                            else -> "Could not hear clearly. Try again or type."
                        }
                        speechStatusMessage = errorText
                        mainHandler.post { renderOverlayView() }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            recognizedSpeechText = text
                            speechStatusMessage = "Speech captured! Review or tap 'Send Answer'."
                        } else {
                            speechStatusMessage = "No words recognized. Try again or type."
                        }
                        mainHandler.post { renderOverlayView() }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull() ?: ""
                        if (partial.isNotBlank()) {
                            recognizedSpeechText = partial
                            mainHandler.post { renderOverlayView() }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            // CRITICAL OPTIMIZATION: Extended silence limits so mic does not cut off mid-speech
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer: ${e.message}", e)
            isListening = false
            speechStatusMessage = "Speech error. Please type your answer."
            renderOverlayView()
        }
    }

    private fun stopListening() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
        renderOverlayView()
    }

    // ================= KEYWORD EXTRACTION =================

    private fun extractKeywordsFromTranscript(
        transcript: String,
        title: String,
        topic: String
    ): List<String> {
        val combined = "$title $topic $transcript".lowercase()
            .replace("[^a-zA-Z0-9 ]".toRegex(), " ")

        val words = combined.split("\\s+".toRegex())
            .filter { it.length >= 4 && !STOP_WORDS.contains(it) }

        val freq = mutableMapOf<String, Int>()
        for (w in words) {
            freq[w] = (freq[w] ?: 0) + 1
        }

        return freq.entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }
    }

    // ================= RELIABLE BACKGROUND DISTRACTION MONITOR =================

    private fun startDistractionMonitoring() {
        monitorJob?.cancel()
        if (!hasUsageStatsPermission()) {
            Log.i(TAG, "UsageStats permission not granted. Distraction monitoring operating in on-demand mode.")
            return
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return

        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive && _sessionState.value.isActive) {
                // Polling interval between 500ms and 800ms for immediate responsiveness
                delay(750)

                try {
                    val now = System.currentTimeMillis()

                    // Check if temporarily unlocked
                    if (isCurrentlyUnlocked) {
                        if (now < unlockedUntilTimestamp) {
                            // Currently in 5-minute unlock window
                            continue
                        } else {
                            // 5 minutes expired, reset unlock
                            isCurrentlyUnlocked = false
                            lastUnlockedApp = null
                        }
                    }

                    val monitoredApps = repository.getMonitoredAppsList().map { it.packageName }.toSet()
                    if (monitoredApps.isEmpty()) continue

                    val startTime = now - 2000L
                    val usageEvents = usageStatsManager.queryEvents(startTime, now)
                    val event = UsageEvents.Event()
                    var foregroundPackage: String? = null

                    while (usageEvents.hasNextEvent()) {
                        usageEvents.getNextEvent(event)
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                            foregroundPackage = event.packageName
                        }
                    }

                    // Fallback to queryUsageStats if no new resume events fired
                    if (foregroundPackage == null) {
                        val stats = usageStatsManager.queryUsageStats(
                            UsageStatsManager.INTERVAL_DAILY,
                            now - 5000L,
                            now
                        )
                        foregroundPackage = stats.maxByOrNull { it.lastTimeUsed }?.packageName
                    }

                    if (foregroundPackage != null) {
                        // Filter out our own app, system UI, and launchers
                        if (isLauncherOrSystemApp(foregroundPackage)) {
                            continue
                        }

                        if (monitoredApps.contains(foregroundPackage)) {
                            // Trigger distraction overlay immediately
                            scope.launch(Dispatchers.Main) {
                                triggerSimulatedDistraction(foregroundPackage)
                                showLockOverlay(foregroundPackage)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error in distraction monitoring loop: ${e.message}")
                }
            }
        }
    }

    private fun isLauncherOrSystemApp(pkg: String): Boolean {
        if (pkg == context.packageName) return true
        if (pkg == "android" || pkg == "com.android.systemui") return true

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultLauncher = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return pkg == defaultLauncher
    }

    // ================= FOCUS SESSION LIFECYCLE =================

    private fun restoreActiveSession() {
        scope.launch {
            val activeSession = repository.getActiveFocusSession()
            if (activeSession != null) {
                val now = System.currentTimeMillis()
                val elapsed = ((now - activeSession.startTime) / 1000).coerceAtLeast(0L)
                val totalSeconds = activeSession.plannedMinutes * 60L
                val remaining = (totalSeconds - elapsed).coerceAtLeast(0L)

                val subject = activeSession.subjectId?.let { repository.getSubjectById(it) }

                _sessionState.value = FocusSessionState(
                    isActive = true,
                    isPaused = false,
                    sessionId = activeSession.id,
                    subjectId = activeSession.subjectId,
                    subjectName = subject?.name ?: "General Focus",
                    goal = activeSession.goal,
                    plannedMinutes = activeSession.plannedMinutes,
                    elapsedSeconds = elapsed,
                    remainingSeconds = remaining,
                    distractionCount = activeSession.distractionCount
                )

                startSessionTimer(activeSession.id)
                startDistractionMonitoring()
            }
        }
    }

    fun startSession(
        subjectId: String?,
        subjectName: String,
        durationMinutes: Int,
        goal: String
    ) {
        val sessionId = UUID.randomUUID().toString()
        val totalSeconds = durationMinutes * 60L

        val entity = FocusSessionEntity(
            id = sessionId,
            subjectId = subjectId,
            plannedMinutes = durationMinutes,
            startTime = System.currentTimeMillis(),
            status = "ACTIVE",
            goal = goal
        )

        scope.launch {
            repository.insertFocusSession(entity)
            isCurrentlyUnlocked = false
            unlockedUntilTimestamp = 0L

            _sessionState.value = FocusSessionState(
                isActive = true,
                isPaused = false,
                sessionId = sessionId,
                subjectId = subjectId,
                subjectName = subjectName,
                goal = goal,
                plannedMinutes = durationMinutes,
                elapsedSeconds = 0L,
                remainingSeconds = totalSeconds,
                distractionCount = 0
            )

            startSessionTimer(sessionId)
            startDistractionMonitoring()
            try {
                context.startService(Intent(context, FocusOverlayService::class.java))
            } catch (e: Exception) {
                Log.d(TAG, "Optional FocusOverlayService start: ${e.message}")
            }
        }
    }

    fun pauseSession() {
        if (!_sessionState.value.isActive) return
        timerJob?.cancel()
        hideLockOverlay()
        _sessionState.value = _sessionState.value.copy(isPaused = true)
    }

    fun resumeSession() {
        val id = _sessionState.value.sessionId ?: return
        if (!_sessionState.value.isActive) return
        _sessionState.value = _sessionState.value.copy(isPaused = false)
        startSessionTimer(id)
        startDistractionMonitoring()
    }

    fun endSession(completed: Boolean = false) {
        val currentState = _sessionState.value
        val sessionId = currentState.sessionId ?: return

        timerJob?.cancel()
        monitorJob?.cancel()
        hideLockOverlay()
        try {
            context.stopService(Intent(context, FocusOverlayService::class.java))
        } catch (_: Exception) {}

        val actualMinutes = (currentState.elapsedSeconds / 60).toInt().coerceAtLeast(1)

        scope.launch {
            val session = repository.getActiveFocusSession()
            if (session != null && session.id == sessionId) {
                val updated = session.copy(
                    actualMinutes = actualMinutes,
                    endTime = System.currentTimeMillis(),
                    status = if (completed || currentState.remainingSeconds <= 0) "COMPLETED" else "CANCELLED",
                    distractionCount = currentState.distractionCount,
                    verificationAttempted = currentState.verificationPassed != null,
                    verificationPassed = currentState.verificationPassed ?: false
                )
                repository.updateFocusSession(updated)
            }

            _sessionState.value = FocusSessionState(isActive = false)
        }
    }

    fun dismissDistractionAlert() {
        _sessionState.value = _sessionState.value.copy(showDistractionAlert = false)
    }

    fun triggerSimulatedDistraction(appName: String) {
        val newCount = _sessionState.value.distractionCount + 1
        _sessionState.value = _sessionState.value.copy(
            distractionCount = newCount,
            recentDistractionApp = appName,
            showDistractionAlert = true
        )
        scope.launch {
            val session = repository.getActiveFocusSession()
            if (session != null) {
                repository.updateFocusSession(session.copy(distractionCount = newCount))
            }
        }
    }

    fun launchVoiceVerification() {
        scope.launch {
            val targetQuestion = "What is the primary concept you are studying in this session?"
            val expectedAnswer = "Core principles and subject definitions."

            _sessionState.value = _sessionState.value.copy(
                isVerificationActive = true,
                verificationQuestion = targetQuestion,
                verificationExpectedAnswer = expectedAnswer,
                verificationScore = null,
                verificationPassed = null,
                verificationFeedback = null
            )

            if (isTtsReady) {
                textToSpeech?.speak(
                    "Study Check: $targetQuestion",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "study_check_utterance"
                )
            }
        }
    }

    fun submitVerificationAnswer(answer: String) {
        scope.launch {
            val current = _sessionState.value
            val (score, passed, feedback) = aiService.evaluateStudyResponse(
                question = current.verificationQuestion,
                expectedAnswer = current.verificationExpectedAnswer,
                studentAnswer = answer
            )

            _sessionState.value = _sessionState.value.copy(
                verificationScore = score,
                verificationPassed = passed,
                verificationFeedback = feedback
            )

            if (passed) {
                hideLockOverlay()
            }

            if (isTtsReady) {
                val speechFeedback = if (passed) "Great job! Verification passed. Focus lock released." else "Keep going. Take a look at the key concepts."
                textToSpeech?.speak(speechFeedback, TextToSpeech.QUEUE_FLUSH, null, "feedback_utterance")
            }
        }
    }

    fun closeVerification() {
        _sessionState.value = _sessionState.value.copy(
            isVerificationActive = false,
            showDistractionAlert = false
        )
    }

    private fun startSessionTimer(sessionId: String) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _sessionState.value.isActive && !_sessionState.value.isPaused) {
                delay(1000)
                val current = _sessionState.value
                val newElapsed = current.elapsedSeconds + 1
                val newRemaining = (current.plannedMinutes * 60L - newElapsed).coerceAtLeast(0L)

                _sessionState.value = current.copy(
                    elapsedSeconds = newElapsed,
                    remainingSeconds = newRemaining
                )

                if (newRemaining <= 0) {
                    endSession(completed = true)
                    break
                }
            }
        }
    }

    fun destroy() {
        timerJob?.cancel()
        monitorJob?.cancel()
        hideLockOverlay()
        try {
            context.stopService(Intent(context, FocusOverlayService::class.java))
        } catch (_: Exception) {}
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {}
    }

    companion object {
        private val STOP_WORDS = setOf(
            "that", "this", "with", "from", "have", "were", "what", "when", "where",
            "which", "will", "would", "there", "their", "about", "could", "should",
            "these", "those", "being", "been", "also", "then", "into", "more", "other",
            "some", "such", "than", "them", "very", "your", "only", "lecture", "student",
            "class", "today", "chapter", "topic", "study", "hello", "everyone"
        )
    }
}
