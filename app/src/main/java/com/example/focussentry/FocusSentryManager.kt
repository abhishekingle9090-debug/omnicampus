package com.example.focussentry

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.ai.GeminiAiService
import com.example.data.local.entity.FocusSessionEntity
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
    val verificationFeedback: String? = null
)

class FocusSentryManager(
    private val context: Context,
    private val repository: OmniCampusRepository,
    private val aiService: GeminiAiService
) : TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var timerJob: Job? = null
    private var monitorJob: Job? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

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

    /**
     * Checks whether UsageStats permission has been granted in Android System Settings
     */
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

    /**
     * Opens system Settings screen for Usage Access
     */
    fun requestUsageAccessIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

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
        }
    }

    fun pauseSession() {
        if (!_sessionState.value.isActive) return
        timerJob?.cancel()
        _sessionState.value = _sessionState.value.copy(isPaused = true)
    }

    fun resumeSession() {
        val id = _sessionState.value.sessionId ?: return
        if (!_sessionState.value.isActive) return
        _sessionState.value = _sessionState.value.copy(isPaused = false)
        startSessionTimer(id)
    }

    fun endSession(completed: Boolean = false) {
        val currentState = _sessionState.value
        val sessionId = currentState.sessionId ?: return

        timerJob?.cancel()
        monitorJob?.cancel()

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
            // Find a suitable question from flashcards or lecture notes
            val cards = repository.allFlashcards
            var targetQuestion = "What is the primary concept you are studying in this session?"
            var expectedAnswer = "Core principles and subject definitions."

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

            if (isTtsReady) {
                val speechFeedback = if (passed) "Great job! Verification passed." else "Keep going. Take a look at the key concepts."
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

    private fun startDistractionMonitoring() {
        monitorJob?.cancel()
        if (!hasUsageStatsPermission()) {
            Log.i(TAG, "UsageStats permission not granted. Distraction monitoring operating in on-demand mode.")
            return
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return

        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive && _sessionState.value.isActive) {
                delay(4000) // Battery-friendly check every 4 seconds
                try {
                    val monitoredApps = repository.getMonitoredAppsList().map { it.packageName }.toSet()
                    if (monitoredApps.isEmpty()) continue

                    val endTime = System.currentTimeMillis()
                    val startTime = endTime - 5000

                    val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
                    val event = UsageEvents.Event()

                    while (usageEvents.hasNextEvent()) {
                        usageEvents.getNextEvent(event)
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                            val pkg = event.packageName
                            if (pkg != context.packageName && monitoredApps.contains(pkg)) {
                                scope.launch(Dispatchers.Main) {
                                    triggerSimulatedDistraction(pkg)
                                }
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking usage events: ${e.message}")
                }
            }
        }
    }

    fun destroy() {
        timerJob?.cancel()
        monitorJob?.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
