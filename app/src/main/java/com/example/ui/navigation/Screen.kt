package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Lectures : Screen("lectures")
    data object RecordLecture : Screen("record_lecture")
    data object LectureReview : Screen("lecture_review/{lectureId}") {
        fun createRoute(lectureId: String) = "lecture_review/$lectureId"
    }
    data object TranscriptEditor : Screen("transcript_editor/{lectureId}") {
        fun createRoute(lectureId: String) = "transcript_editor/$lectureId"
    }
    data object NoteGenerator : Screen("note_generator/{lectureId}") {
        fun createRoute(lectureId: String) = "note_generator/$lectureId"
    }
    data object NoteDetail : Screen("note_detail/{noteId}") {
        fun createRoute(noteId: String) = "note_detail/$noteId"
    }
    data object Flashcards : Screen("flashcards")
    data object Focus : Screen("focus")
    data object Profile : Screen("profile")
}
