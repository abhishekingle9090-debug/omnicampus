package com.example.data.repository

import com.example.data.local.OmniCampusDatabase
import com.example.data.local.entity.DistractionAppEntity
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.FlashcardReviewEntity
import com.example.data.local.entity.FocusSessionEntity
import com.example.data.local.entity.LectureEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.TranscriptEntity
import com.example.data.local.entity.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

class OmniCampusRepository(private val database: OmniCampusDatabase) {

    // Subjects
    val allSubjects: Flow<List<SubjectEntity>> = database.subjectDao().getAllSubjects()

    suspend fun getSubjectById(id: String): SubjectEntity? = withContext(Dispatchers.IO) {
        database.subjectDao().getSubjectById(id)
    }

    suspend fun insertSubject(subject: SubjectEntity) = withContext(Dispatchers.IO) {
        database.subjectDao().insertSubject(subject)
    }

    suspend fun updateSubject(subject: SubjectEntity) = withContext(Dispatchers.IO) {
        database.subjectDao().updateSubject(subject)
    }

    suspend fun deleteSubject(subject: SubjectEntity) = withContext(Dispatchers.IO) {
        database.subjectDao().deleteSubject(subject)
    }

    // Lectures
    val allLectures: Flow<List<LectureEntity>> = database.lectureDao().getAllLectures()
    val totalLecturesCount: Flow<Int> = database.lectureDao().getLecturesCount()

    fun getLecturesBySubject(subjectId: String): Flow<List<LectureEntity>> {
        return database.lectureDao().getLecturesBySubject(subjectId)
    }

    fun getRecentLectures(limit: Int = 5): Flow<List<LectureEntity>> {
        return database.lectureDao().getRecentLectures(limit)
    }

    fun getLectureByIdFlow(id: String): Flow<LectureEntity?> {
        return database.lectureDao().getLectureByIdFlow(id)
    }

    suspend fun getLectureById(id: String): LectureEntity? = withContext(Dispatchers.IO) {
        database.lectureDao().getLectureById(id)
    }

    fun searchLectures(query: String): Flow<List<LectureEntity>> {
        return database.lectureDao().searchLectures(query)
    }

    suspend fun insertLecture(lecture: LectureEntity) = withContext(Dispatchers.IO) {
        database.lectureDao().insertLecture(lecture)
    }

    suspend fun updateLecture(lecture: LectureEntity) = withContext(Dispatchers.IO) {
        database.lectureDao().updateLecture(lecture)
    }

    suspend fun deleteLecture(lecture: LectureEntity) = withContext(Dispatchers.IO) {
        // Safe deletion: delete audio file if exists
        lecture.audioFilePath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
        database.lectureDao().deleteLecture(lecture)
    }

    // Transcripts
    fun getTranscriptByLecture(lectureId: String): Flow<TranscriptEntity?> {
        return database.transcriptDao().getTranscriptByLectureId(lectureId)
    }

    suspend fun getTranscriptDirect(lectureId: String): TranscriptEntity? = withContext(Dispatchers.IO) {
        database.transcriptDao().getTranscriptDirect(lectureId)
    }

    suspend fun insertOrUpdateTranscript(transcript: TranscriptEntity) = withContext(Dispatchers.IO) {
        database.transcriptDao().insertTranscript(transcript)
    }

    // Notes
    val allNotes: Flow<List<NoteEntity>> = database.noteDao().getAllNotes()
    val totalNotesCount: Flow<Int> = database.noteDao().getNotesCount()

    fun getNotesByLecture(lectureId: String): Flow<List<NoteEntity>> {
        return database.noteDao().getNotesByLecture(lectureId)
    }

    fun getNotesBySubject(subjectId: String): Flow<List<NoteEntity>> {
        return database.noteDao().getNotesBySubject(subjectId)
    }

    fun getRecentNotes(limit: Int = 5): Flow<List<NoteEntity>> {
        return database.noteDao().getRecentNotes(limit)
    }

    fun getNoteByIdFlow(id: String): Flow<NoteEntity?> {
        return database.noteDao().getNoteByIdFlow(id)
    }

    suspend fun getNoteById(id: String): NoteEntity? = withContext(Dispatchers.IO) {
        database.noteDao().getNoteById(id)
    }

    fun searchNotes(query: String): Flow<List<NoteEntity>> {
        return database.noteDao().searchNotes(query)
    }

    suspend fun insertNote(note: NoteEntity) = withContext(Dispatchers.IO) {
        database.noteDao().insertNote(note)
    }

    suspend fun updateNote(note: NoteEntity) = withContext(Dispatchers.IO) {
        database.noteDao().updateNote(note)
    }

    suspend fun deleteNote(note: NoteEntity) = withContext(Dispatchers.IO) {
        database.noteDao().deleteNote(note)
    }

    // Flashcards
    val allFlashcards: Flow<List<FlashcardEntity>> = database.flashcardDao().getAllFlashcards()
    val totalFlashcardsCount: Flow<Int> = database.flashcardDao().getTotalFlashcardsCount()

    fun getFlashcardsBySubject(subjectId: String): Flow<List<FlashcardEntity>> {
        return database.flashcardDao().getFlashcardsBySubject(subjectId)
    }

    fun getFlashcardsByLecture(lectureId: String): Flow<List<FlashcardEntity>> {
        return database.flashcardDao().getFlashcardsByLecture(lectureId)
    }

    fun getDueFlashcards(currentTime: Long = System.currentTimeMillis()): Flow<List<FlashcardEntity>> {
        return database.flashcardDao().getDueFlashcards(currentTime)
    }

    fun getDueFlashcardsCount(currentTime: Long = System.currentTimeMillis()): Flow<Int> {
        return database.flashcardDao().getDueFlashcardsCount(currentTime)
    }

    suspend fun insertFlashcards(flashcards: List<FlashcardEntity>) = withContext(Dispatchers.IO) {
        database.flashcardDao().insertFlashcards(flashcards)
    }

    suspend fun updateFlashcard(flashcard: FlashcardEntity) = withContext(Dispatchers.IO) {
        database.flashcardDao().updateFlashcard(flashcard)
    }

    suspend fun recordFlashcardReview(flashcard: FlashcardEntity, rating: String) = withContext(Dispatchers.IO) {
        // SM-2 Spaced Repetition calculation
        val currentRepetitions = flashcard.repetitions
        val currentEase = flashcard.easeFactor
        val grade = when (rating) {
            "HARD" -> 1
            "MEDIUM" -> 3
            else -> 5 // EASY
        }

        val newRepetitions = if (grade >= 3) currentRepetitions + 1 else 0
        val newInterval = when {
            newRepetitions == 1 -> 1
            newRepetitions == 2 -> 6
            newRepetitions > 2 -> (flashcard.intervalDays * currentEase).toInt().coerceAtLeast(1)
            else -> 1
        }
        val newEase = (currentEase + (0.1f - (5 - grade) * (0.08f + (5 - grade) * 0.02f)))
            .coerceAtLeast(1.3f)

        val nextReview = System.currentTimeMillis() + (newInterval * 86400000L)

        val updatedCard = flashcard.copy(
            repetitions = newRepetitions,
            intervalDays = newInterval,
            easeFactor = newEase,
            difficulty = rating,
            lastReviewedAt = System.currentTimeMillis(),
            nextReviewAt = nextReview
        )
        database.flashcardDao().updateFlashcard(updatedCard)
        database.flashcardDao().insertReview(
            FlashcardReviewEntity(
                id = java.util.UUID.randomUUID().toString(),
                flashcardId = flashcard.id,
                rating = rating
            )
        )
    }

    suspend fun deleteFlashcard(flashcard: FlashcardEntity) = withContext(Dispatchers.IO) {
        database.flashcardDao().deleteFlashcard(flashcard)
    }

    // Focus Sessions
    val allFocusSessions: Flow<List<FocusSessionEntity>> = database.focusSessionDao().getAllSessions()
    val activeFocusSessionFlow: Flow<FocusSessionEntity?> = database.focusSessionDao().getActiveSessionFlow()
    val totalFocusMinutes: Flow<Int> = database.focusSessionDao().getTotalFocusMinutes()
    val completedSessionsCount: Flow<Int> = database.focusSessionDao().getCompletedSessionsCount()
    val totalSessionsCount: Flow<Int> = database.focusSessionDao().getTotalSessionsCount()
    val totalDistractionsCount: Flow<Int> = database.focusSessionDao().getTotalDistractionsCount()

    fun getTodayFocusMinutes(): Flow<Int> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return database.focusSessionDao().getTodayFocusMinutes(calendar.timeInMillis)
    }

    fun getWeeklySessions(): Flow<List<FocusSessionEntity>> {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
        }
        return database.focusSessionDao().getWeeklySessions(calendar.timeInMillis)
    }

    suspend fun getActiveFocusSession(): FocusSessionEntity? = withContext(Dispatchers.IO) {
        database.focusSessionDao().getActiveSession()
    }

    suspend fun insertFocusSession(session: FocusSessionEntity) = withContext(Dispatchers.IO) {
        database.focusSessionDao().insertSession(session)
    }

    suspend fun updateFocusSession(session: FocusSessionEntity) = withContext(Dispatchers.IO) {
        database.focusSessionDao().updateSession(session)
    }

    // Distraction Apps
    val monitoredApps: Flow<List<DistractionAppEntity>> = database.distractionAppDao().getMonitoredApps()
    val allConfiguredApps: Flow<List<DistractionAppEntity>> = database.distractionAppDao().getAllApps()

    suspend fun getMonitoredAppsList(): List<DistractionAppEntity> = withContext(Dispatchers.IO) {
        database.distractionAppDao().getMonitoredAppsList()
    }

    suspend fun updateDistractionApp(app: DistractionAppEntity) = withContext(Dispatchers.IO) {
        database.distractionAppDao().updateApp(app)
    }

    suspend fun insertDistractionApp(app: DistractionAppEntity) = withContext(Dispatchers.IO) {
        database.distractionAppDao().insertApp(app)
    }

    suspend fun deleteDistractionApp(app: DistractionAppEntity) = withContext(Dispatchers.IO) {
        database.distractionAppDao().deleteApp(app)
    }

    // User Profile
    val userProfile: Flow<UserProfileEntity?> = database.userProfileDao().getUserProfile()

    suspend fun getUserProfileDirect(): UserProfileEntity? = withContext(Dispatchers.IO) {
        database.userProfileDao().getUserProfileDirect()
    }

    suspend fun updateUserProfile(profile: UserProfileEntity) = withContext(Dispatchers.IO) {
        database.userProfileDao().insertOrUpdateProfile(profile)
    }

    // User Accounts (Local & Offline Authentication)
    suspend fun registerAccount(account: com.example.data.local.entity.UserAccountEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val existing = database.userAccountDao().getAccountByEmail(account.email)
            if (existing != null) {
                return@withContext Result.failure(IllegalArgumentException("An account with this email already exists."))
            }
            database.userAccountDao().insertAccount(account)
            // Sync with profile
            database.userProfileDao().insertOrUpdateProfile(
                UserProfileEntity(
                    id = "primary_user",
                    name = account.name,
                    semester = account.semester,
                    isGuestMode = false,
                    userEmail = account.email,
                    hasCompletedOnboarding = true
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAccountByEmail(email: String): com.example.data.local.entity.UserAccountEntity? = withContext(Dispatchers.IO) {
        database.userAccountDao().getAccountByEmail(email)
    }
}
