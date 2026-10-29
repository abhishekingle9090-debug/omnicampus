package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiAiService
import com.example.auth.SecurityUtils
import com.example.data.local.OmniCampusDatabase
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.LectureEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.TranscriptEntity
import com.example.data.local.entity.UserAccountEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.OmniCampusRepository
import com.example.recording.AudioDiagnostics
import com.example.recording.LectureAudioPlayer
import com.example.recording.LectureRecordingService
import com.example.recording.MicTestUtility
import com.example.recording.WavAudioRecorder
import com.example.transcription.GeminiAudioTranscriber
import com.example.transcription.SpeechToTextEngine
import com.example.transcription.TranscriptCorrectionHelper
import com.example.transcription.TranscriptSegment
import com.example.transcription.TranscriptionFailure
import com.example.transcription.VoskOfflineEngine
import com.example.transcription.WordDiff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val TAG = "OmniCampusViewModel"

data class TranscriptionUiState(
    val isTranscribing: Boolean = false,
    val progress: Float = 0f,
    val stageMessage: String? = null,
    val error: String? = null,
    val recoveryAction: String? = null,
    val successText: String? = null,
    val diagnostics: AudioDiagnostics? = null
)

data class AiNoteGenState(
    val isGenerating: Boolean = false,
    val error: String? = null,
    val generatedNote: NoteEntity? = null
)

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val isGuest: Boolean = true,
    val userId: String? = null,
    val email: String? = null,
    val name: String = "Student",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val passwordResetSent: Boolean = false
)

class OmniCampusViewModel(application: Application) : AndroidViewModel(application) {

    val database = OmniCampusDatabase.getDatabase(application)
    val repository = OmniCampusRepository(database)
    val aiService = GeminiAiService(application)

    // Real audio capture and playback utilities
    val wavRecorder = WavAudioRecorder(application)
    val audioPlayer = LectureAudioPlayer()
    val micTestUtility = MicTestUtility(application)

    // Speech-to-text engines
    val geminiTranscriber = GeminiAudioTranscriber(application)
    val voskOfflineEngine = VoskOfflineEngine(application)

    // Auth State
    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    // User Profile
    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // FocusSentry Manager
    val focusSentryManager = com.example.focussentry.FocusSentryManager(application, repository, aiService)

    // Focus & Distraction Statistics Flows
    val allConfiguredApps: StateFlow<List<com.example.data.local.entity.DistractionAppEntity>> = repository.allConfiguredApps.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalFocusMinutes: StateFlow<Int> = repository.totalFocusMinutes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val completedFocusSessionsCount: StateFlow<Int> = repository.completedSessionsCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val totalDistractionsCount: StateFlow<Int> = repository.totalDistractionsCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val todayFocusMinutes: StateFlow<Int> = repository.getTodayFocusMinutes().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Subjects
    val subjects: StateFlow<List<SubjectEntity>> = repository.allSubjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Lectures
    val lectures: StateFlow<List<LectureEntity>> = repository.allLectures.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentLectures: StateFlow<List<LectureEntity>> = repository.getRecentLectures(5).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalLecturesCount: StateFlow<Int> = repository.totalLecturesCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Notes
    val notes: StateFlow<List<NoteEntity>> = repository.allNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentNotes: StateFlow<List<NoteEntity>> = repository.getRecentNotes(5).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalNotesCount: StateFlow<Int> = repository.totalNotesCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Flashcards
    val flashcards: StateFlow<List<FlashcardEntity>> = repository.allFlashcards.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val dueFlashcards: StateFlow<List<FlashcardEntity>> = repository.getDueFlashcards().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val dueFlashcardsCount: StateFlow<Int> = repository.getDueFlashcardsCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Active Recording Form State
    val recordingFormTitle = MutableStateFlow("")
    val recordingFormSubjectId = MutableStateFlow("")
    val recordingFormTopic = MutableStateFlow("")
    val recordingFormDesc = MutableStateFlow("")
    val recordingLanguage = MutableStateFlow("en-US")
    val selectedEngineType = MutableStateFlow("GEMINI") // "GEMINI" or "VOSK"

    // Transcription state
    private val _transcriptionState = MutableStateFlow(TranscriptionUiState())
    val transcriptionState: StateFlow<TranscriptionUiState> = _transcriptionState.asStateFlow()

    // Transcript correction diffs
    private val _reviewDiffs = MutableStateFlow<List<WordDiff>>(emptyList())
    val reviewDiffs: StateFlow<List<WordDiff>> = _reviewDiffs.asStateFlow()

    // AI Note Generation state
    private val _noteGenState = MutableStateFlow(AiNoteGenState())
    val noteGenState: StateFlow<AiNoteGenState> = _noteGenState.asStateFlow()

    init {
        // Check initial profile
        viewModelScope.launch {
            val profile = repository.getUserProfileDirect()
            if (profile != null) {
                _authUiState.value = _authUiState.value.copy(
                    isLoggedIn = !profile.isGuestMode && !profile.userEmail.isNullOrBlank(),
                    isGuest = profile.isGuestMode,
                    email = profile.userEmail,
                    name = profile.name
                )
            }
        }
    }

    // ================= AUTHENTICATION ACTIONS =================

    fun signIn(email: String, password: String, onComplete: (Boolean) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            _authUiState.value = _authUiState.value.copy(errorMessage = "Email and password cannot be empty.")
            onComplete(false)
            return
        }

        viewModelScope.launch {
            _authUiState.value = _authUiState.value.copy(isLoading = true, errorMessage = null)
            val account = repository.getAccountByEmail(email.trim())

            if (account == null) {
                _authUiState.value = _authUiState.value.copy(
                    isLoading = false,
                    errorMessage = "No account found with this email. Please sign up."
                )
                onComplete(false)
                return@launch
            }

            val isPasswordValid = SecurityUtils.verifyPassword(password, account.salt, account.passwordHash)
            if (!isPasswordValid) {
                _authUiState.value = _authUiState.value.copy(
                    isLoading = false,
                    errorMessage = "Incorrect password. Please try again."
                )
                onComplete(false)
                return@launch
            }

            // Update user profile session
            repository.updateUserProfile(
                UserProfileEntity(
                    id = "primary_user",
                    name = account.name,
                    semester = account.semester,
                    isGuestMode = false,
                    userEmail = account.email,
                    hasCompletedOnboarding = true
                )
            )

            _authUiState.value = AuthUiState(
                isLoggedIn = true,
                isGuest = false,
                userId = account.id,
                email = account.email,
                name = account.name
            )
            onComplete(true)
        }
    }

    fun signUp(name: String, email: String, password: String, confirmPass: String, onComplete: (Boolean) -> Unit) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _authUiState.value = _authUiState.value.copy(errorMessage = "All fields are required.")
            onComplete(false)
            return
        }
        if (!email.contains("@") || !email.contains(".")) {
            _authUiState.value = _authUiState.value.copy(errorMessage = "Please enter a valid academic or personal email address.")
            onComplete(false)
            return
        }
        if (password.length < 6) {
            _authUiState.value = _authUiState.value.copy(errorMessage = "Password must be at least 6 characters.")
            onComplete(false)
            return
        }
        if (password != confirmPass) {
            _authUiState.value = _authUiState.value.copy(errorMessage = "Passwords do not match.")
            onComplete(false)
            return
        }

        viewModelScope.launch {
            _authUiState.value = _authUiState.value.copy(isLoading = true, errorMessage = null)
            val salt = SecurityUtils.generateSalt()
            val hash = SecurityUtils.hashPassword(password, salt)
            val account = UserAccountEntity(
                id = UUID.randomUUID().toString(),
                email = email.trim(),
                name = name.trim(),
                passwordHash = hash,
                salt = salt,
                isVerified = true
            )

            val result = repository.registerAccount(account)
            result.fold(
                onSuccess = {
                    _authUiState.value = AuthUiState(
                        isLoggedIn = true,
                        isGuest = false,
                        userId = account.id,
                        email = account.email,
                        name = account.name
                    )
                    onComplete(true)
                },
                onFailure = { error ->
                    _authUiState.value = _authUiState.value.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Registration failed."
                    )
                    onComplete(false)
                }
            )
        }
    }

    fun continueAsGuest(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.updateUserProfile(
                UserProfileEntity(
                    id = "primary_user",
                    name = "Guest Student",
                    semester = "Semester 1",
                    isGuestMode = true,
                    userEmail = null,
                    hasCompletedOnboarding = true
                )
            )
            _authUiState.value = AuthUiState(isLoggedIn = false, isGuest = true, name = "Guest Student")
            onComplete()
        }
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.updateUserProfile(
                UserProfileEntity(
                    id = "primary_user",
                    name = "Student",
                    isGuestMode = true,
                    userEmail = null,
                    hasCompletedOnboarding = false
                )
            )
            _authUiState.value = AuthUiState(isLoggedIn = false, isGuest = true)
            onComplete()
        }
    }

    fun requestPasswordReset(email: String) {
        _authUiState.value = _authUiState.value.copy(
            passwordResetSent = true,
            errorMessage = "A password recovery guide has been sent to $email."
        )
    }

    // ================= RECORDING OPERATIONS =================

    fun startLectureRecording(lectureId: String, title: String): Boolean {
        val started = wavRecorder.startRecording(lectureId)
        if (started) {
            LectureRecordingService.start(getApplication(), title.ifBlank { "OmniCampus Lecture" })
        }
        return started
    }

    fun pauseLectureRecording() = wavRecorder.pauseRecording()

    fun resumeLectureRecording() = wavRecorder.resumeRecording()

    fun cancelLectureRecording() {
        wavRecorder.cancelRecording()
        LectureRecordingService.stop(getApplication())
    }

    fun stopAndSaveLecture(onSaved: (String) -> Unit) {
        val file = wavRecorder.stopRecording()
        LectureRecordingService.stop(getApplication())

        val duration = wavRecorder.status.value.durationSeconds
        val lectureId = UUID.randomUUID().toString()

        val lecture = LectureEntity(
            id = lectureId,
            subjectId = recordingFormSubjectId.value.ifBlank {
                subjects.value.firstOrNull()?.id ?: "general_subject"
            },
            title = recordingFormTitle.value.ifBlank { "Recorded Lecture ${System.currentTimeMillis() % 1000}" },
            topic = recordingFormTopic.value,
            description = recordingFormDesc.value,
            audioFilePath = file?.absolutePath,
            durationSeconds = duration,
            language = recordingLanguage.value,
            status = "RECORDED"
        )

        viewModelScope.launch {
            repository.insertLecture(lecture)
            recordingFormTitle.value = ""
            recordingFormTopic.value = ""
            recordingFormDesc.value = ""
            onSaved(lectureId)
        }
    }

    // ================= TRANSCRIPTION =================

    fun transcribeLecture(lecture: LectureEntity) {
        viewModelScope.launch {
            _transcriptionState.value = TranscriptionUiState(
                isTranscribing = true,
                progress = 0.05f,
                stageMessage = "Inspecting recorded audio file..."
            )
            repository.updateLecture(lecture.copy(status = "TRANSCRIBING"))

            val audioFilePath = lecture.audioFilePath
            if (audioFilePath.isNullOrBlank()) {
                val failure = TranscriptionFailure.AudioFileMissing()
                setTranscriptionFailure(lecture, failure.message, failure.recoveryAction)
                return@launch
            }

            val audioFile = File(audioFilePath)
            val diagnostics = wavRecorder.getDiagnostics(audioFile)
            _transcriptionState.value = _transcriptionState.value.copy(diagnostics = diagnostics)

            if (!diagnostics.isValid) {
                if (diagnostics.durationSeconds < 2) {
                    val failure = TranscriptionFailure.AudioTooShort()
                    setTranscriptionFailure(lecture, failure.message, failure.recoveryAction)
                } else {
                    val failure = TranscriptionFailure.AudioFileMissing("Audio file appears empty or corrupt (${diagnostics.fileSizeBytes} bytes).")
                    setTranscriptionFailure(lecture, failure.message, failure.recoveryAction)
                }
                return@launch
            }

            if (diagnostics.isSilent) {
                Log.w(TAG, "Audio file has very low amplitude (RMS=${diagnostics.averageRms})")
            }

            _transcriptionState.value = _transcriptionState.value.copy(
                progress = 0.25f,
                stageMessage = "Processing speech with ${geminiTranscriber.engineName}..."
            )

            val engine: SpeechToTextEngine = if (selectedEngineType.value == "VOSK") {
                voskOfflineEngine
            } else {
                geminiTranscriber
            }

            val result = engine.transcribeAudio(
                audioFile = audioFile,
                language = lecture.language,
                onProgress = { progress ->
                    _transcriptionState.value = _transcriptionState.value.copy(
                        progress = progress,
                        stageMessage = if (progress < 0.7f) "Uploading audio bytes..." else "Decoding classroom speech..."
                    )
                }
            )

            result.fold(
                onSuccess = { transcriptionResult ->
                    if (transcriptionResult.fullText.isBlank()) {
                        val failure = TranscriptionFailure.EmptyTranscription()
                        setTranscriptionFailure(lecture, failure.message, failure.recoveryAction)
                        return@fold
                    }

                    val transcript = TranscriptEntity(
                        id = UUID.randomUUID().toString(),
                        lectureId = lecture.id,
                        originalText = transcriptionResult.fullText,
                        correctedText = transcriptionResult.fullText,
                        isEdited = false,
                        segmentsJson = TranscriptSegment.serializeList(transcriptionResult.segments),
                        confidence = transcriptionResult.confidence
                    )
                    repository.insertOrUpdateTranscript(transcript)
                    repository.updateLecture(lecture.copy(status = "TRANSCRIBED"))

                    _transcriptionState.value = TranscriptionUiState(
                        isTranscribing = false,
                        progress = 1.0f,
                        stageMessage = "Transcription completed successfully.",
                        successText = transcriptionResult.fullText,
                        diagnostics = diagnostics
                    )
                    prepareWordReview(transcriptionResult.fullText)
                },
                onFailure = { error ->
                    Log.e(TAG, "Transcription failed", error)
                    val (msg, action) = if (error is TranscriptionFailure) {
                        error.message to error.recoveryAction
                    } else {
                        (error.localizedMessage ?: "Transcription failed") to "Check internet connectivity or ensure API key is active."
                    }
                    setTranscriptionFailure(lecture, msg, action)
                }
            )
        }
    }

    private suspend fun setTranscriptionFailure(lecture: LectureEntity, message: String, action: String) {
        repository.updateLecture(lecture.copy(status = "FAILED"))
        _transcriptionState.value = _transcriptionState.value.copy(
            isTranscribing = false,
            error = message,
            recoveryAction = action
        )
    }

    fun prepareWordReview(text: String) {
        val diffs = TranscriptCorrectionHelper.analyzeTranscriptForReview(text)
        _reviewDiffs.value = diffs
    }

    fun toggleDiffAccepted(index: Int) {
        val current = _reviewDiffs.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            current[index] = item.copy(isAccepted = !item.isAccepted, isRejected = false)
            _reviewDiffs.value = current
        }
    }

    fun toggleDiffRejected(index: Int) {
        val current = _reviewDiffs.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            current[index] = item.copy(isRejected = !item.isRejected, isAccepted = false)
            _reviewDiffs.value = current
        }
    }

    fun saveConfirmedTranscript(
        transcript: TranscriptEntity,
        customEditedText: String? = null
    ) {
        viewModelScope.launch {
            val finalText = customEditedText ?: TranscriptCorrectionHelper.applyAcceptedCorrections(_reviewDiffs.value)
            val updated = transcript.copy(
                correctedText = finalText,
                isEdited = true,
                updatedAt = System.currentTimeMillis()
            )
            repository.insertOrUpdateTranscript(updated)
        }
    }

    // ================= AI NOTE GENERATION =================

    fun generateAiNotes(
        lecture: LectureEntity,
        transcript: TranscriptEntity,
        noteStyle: String = "ACADEMIC",
        detailLevel: String = "STANDARD"
    ) {
        viewModelScope.launch {
            _noteGenState.value = AiNoteGenState(isGenerating = true)

            val subject = repository.getSubjectById(lecture.subjectId)
            val subjectName = subject?.name ?: "General Subject"
            val textToUse = transcript.correctedText.ifBlank { transcript.originalText }

            val result = aiService.generateNotes(
                transcript = textToUse,
                lectureTitle = lecture.title,
                subjectName = subjectName,
                noteStyle = noteStyle,
                detailLevel = detailLevel
            )

            result.fold(
                onSuccess = { noteData ->
                    val noteEntity = NoteEntity(
                        id = UUID.randomUUID().toString(),
                        lectureId = lecture.id,
                        subjectId = lecture.subjectId,
                        style = noteStyle,
                        detailLevel = detailLevel,
                        title = noteData.title,
                        overview = noteData.overview,
                        keyConceptsJson = JSONArray(noteData.keyConcepts).toString(),
                        definitionsJson = JSONArray(
                            noteData.definitions.map { (term, def) ->
                                JSONObject().apply {
                                    put("term", term)
                                    put("definition", def)
                                }
                            }
                        ).toString(),
                        examplesJson = JSONArray(noteData.examples).toString(),
                        formulasJson = JSONArray(
                            noteData.formulas.map { (name, formula, desc) ->
                                JSONObject().apply {
                                    put("name", name)
                                    put("formula", formula)
                                    put("description", desc)
                                }
                            }
                        ).toString(),
                        importantPointsJson = JSONArray(noteData.importantPoints).toString(),
                        summary = noteData.summary,
                        revisionQuestionsJson = JSONArray(noteData.revisionQuestions).toString()
                    )

                    repository.insertNote(noteEntity)
                    _noteGenState.value = AiNoteGenState(isGenerating = false, generatedNote = noteEntity)

                    generateFlashcardsForNote(noteEntity, textToUse, subjectName)
                },
                onFailure = { error ->
                    Log.e(TAG, "Note generation failed", error)
                    _noteGenState.value = AiNoteGenState(
                        isGenerating = false,
                        error = error.localizedMessage ?: "Note generation failed"
                    )
                }
            )
        }
    }

    private fun generateFlashcardsForNote(note: NoteEntity, transcript: String, subjectName: String) {
        viewModelScope.launch {
            val result = aiService.generateFlashcards(transcript, note.overview, subjectName)
            result.onSuccess { cards ->
                val entities = cards.map { card ->
                    FlashcardEntity(
                        id = UUID.randomUUID().toString(),
                        subjectId = note.subjectId,
                        lectureId = note.lectureId,
                        noteId = note.id,
                        question = card.question,
                        answer = card.answer,
                        type = card.type,
                        difficulty = card.difficulty,
                        nextReviewAt = System.currentTimeMillis()
                    )
                }
                repository.insertFlashcards(entities)
            }
        }
    }

    fun updateCustomNoteContent(note: NoteEntity, newCustomNotes: String) {
        viewModelScope.launch {
            repository.updateNote(
                note.copy(
                    customNotes = newCustomNotes,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun reviewFlashcard(flashcard: FlashcardEntity, rating: String) {
        viewModelScope.launch {
            repository.recordFlashcardReview(flashcard, rating)
        }
    }

    fun deleteFlashcard(flashcard: FlashcardEntity) {
        viewModelScope.launch {
            repository.deleteFlashcard(flashcard)
        }
    }

    fun deleteLecture(lecture: LectureEntity) {
        viewModelScope.launch {
            repository.deleteLecture(lecture)
        }
    }

    fun renameLecture(lectureId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            val lecture = repository.getLectureById(lectureId) ?: return@launch
            repository.updateLecture(lecture.copy(title = newTitle.trim()))
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun addSubject(name: String, code: String, semester: String, colorHex: String) {
        viewModelScope.launch {
            repository.insertSubject(
                SubjectEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    code = code,
                    semester = semester,
                    colorHex = colorHex
                )
            )
        }
    }

    fun deleteSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            repository.deleteSubject(subject)
        }
    }

    fun toggleMonitoredApp(app: com.example.data.local.entity.DistractionAppEntity) {
        viewModelScope.launch {
            repository.updateDistractionApp(app.copy(isMonitored = !app.isMonitored))
        }
    }

    fun addCustomDistractionApp(pkg: String, name: String) {
        viewModelScope.launch {
            repository.insertDistractionApp(
                com.example.data.local.entity.DistractionAppEntity(
                    packageName = pkg,
                    appName = name,
                    isMonitored = true
                )
            )
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            val currentProfile = repository.getUserProfileDirect() ?: UserProfileEntity()
            repository.updateUserProfile(currentProfile.copy(hasCompletedOnboarding = true))
        }
    }

    fun updateProfile(
        name: String,
        semester: String,
        goalMinutes: Int,
        preferredFocus: Int,
        darkTheme: Boolean
    ) {
        viewModelScope.launch {
            val currentProfile = repository.getUserProfileDirect() ?: UserProfileEntity()
            repository.updateUserProfile(
                currentProfile.copy(
                    name = name,
                    semester = semester,
                    studyGoalMinutes = goalMinutes,
                    preferredFocusMinutes = preferredFocus,
                    darkThemeEnabled = darkTheme
                )
            )
            _authUiState.value = _authUiState.value.copy(name = name)
        }
    }

    fun createManualTranscript(lectureId: String, text: String) {
        viewModelScope.launch {
            val transcript = TranscriptEntity(
                id = UUID.randomUUID().toString(),
                lectureId = lectureId,
                originalText = text,
                correctedText = text,
                isEdited = true,
                segmentsJson = "[]",
                confidence = 1.0f
            )
            repository.insertOrUpdateTranscript(transcript)
            val lecture = repository.getLectureById(lectureId)
            if (lecture != null) {
                repository.updateLecture(lecture.copy(status = "TRANSCRIBED"))
            }
            prepareWordReview(text)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stopAudio()
    }
}
