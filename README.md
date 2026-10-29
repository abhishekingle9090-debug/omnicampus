# OmniCampus AI
> **Learn Smarter. Stay Focused.**

OmniCampus AI is an intelligent Android student productivity application built natively with Kotlin and Jetpack Compose (Material 3). It converts recorded classroom lectures into searchable transcripts, structured AI notes, and revision flashcards, while guarding study focus with FocusSentry.

---

## Key Features & Connected Modules

### 1. Learning Intelligence
- **Lecture Recording Module**: Real microphone audio capture via `MediaRecorder` encoding to AAC (`.m4a`) in app-private storage. Includes a live harmonic audio amplitude visualizer, recording timer, pause/resume, and safe cancellation.
- **Speech-to-Text Engine**: Uses Android's speech framework (`SpeechRecognizer`) for on-device/online transcription with confidence scores and timestamps. Provides full architecture for offline acoustic transcription via Vosk models.
- **Word Review & Error Correction**: Non-destructive transcript review that suggests phonetic/acoustic replacements without overwriting the original transcript. Students individually accept or reject suggested words.
- **AI Note Generation**: Supports 6 distinct academic note formats (Concise, Detailed Academic, Exam-oriented, Chapter-wise, Definitions & Formulas, and Q&A) with 3 detail levels. Powered by Gemini (`gemini-3.8-flash`) via Firebase AI Logic / Gemini API, with an on-device heuristic engine fallback for offline use.
- **Active Revision Flashcards**: Flashcards generated from lecture content with SM-2 spaced repetition grading (Easy, Medium, Hard), 3D flip animation, and review interval scheduling.
- **Search, Export & Share**: In-transcript search, subject filtering, multi-page PDF generation via Android's `PdfDocument`, Markdown (`.md`) export, and Android system share sheet.

### 2. FocusSentry Guard
- **Focus Sessions**: Study intervals (25m Pomodoro, 45m Standard, 60m Deep Work, or custom) with circular countdown visualization and session persistence across app restarts.
- **Distraction Detection**: Integrates `UsageStatsManager` with battery-friendly background checks to monitor when configured distraction apps are brought to the foreground.
- **Voice-Based Study Verification**: Tests active study engagement by speaking or displaying questions derived from the current subject's notes/flashcards, evaluating spoken answers semantically via speech recognition and AI.
- **Academic Analytics**: 100% computed metrics from Room storage (today's focus minutes, completed sessions, saved lectures, generated notes, and distraction events).

---

## Technical Architecture

```
com.example/
├── MainActivity.kt                  # Edge-to-edge, App Check wiring, Theme root
├── AppCheckHelper.kt                # Firebase App Check contract helper
├── data/
│   ├── local/
│   │   ├── OmniCampusDatabase.kt    # Room database with pre-populated sample seeds
│   │   ├── entity/                  # Entities (Subject, Lecture, Transcript, Note, Flashcard, FocusSession, App, Profile)
│   │   └── dao/                     # Room DAOs with reactive Flows
│   └── repository/
│       └── OmniCampusRepository.kt  # Clean architecture repository layer
├── recording/
│   ├── LectureAudioRecorder.kt      # MediaRecorder audio capture & amplitude stream
│   └── LectureRecordingService.kt   # Foreground service with microphone ongoing notification
├── transcription/
│   ├── SpeechToTextEngine.kt        # Engine interface, Android System engine, Vosk stub
│   └── TranscriptCorrectionHelper.kt# Diffing and student-approved word correction
├── ai/
│   └── GeminiAiService.kt           # Firebase AI Logic, Gemini API, and offline extractor
├── export/
│   └── NoteExporter.kt              # PDF multi-page builder, Markdown exporter, FileProvider share
├── focussentry/
│   └── FocusSentryManager.kt        # Session timer, UsageStats monitoring, TextToSpeech, voice check
├── viewmodel/
│   └── OmniCampusViewModel.kt       # Unified state management via StateFlow
└── ui/
    ├── theme/                       # Deep Navy (#0B132B), Violet (#6C5CE7), Cyan (#00E5FF)
    ├── navigation/                  # Bottom navigation bar & type-safe screen routes
    ├── components/                  # Stat cards, waveform visualizer, empty states
    └── screens/                     # Splash, Onboarding, Home, Recording, Lectures, Notes, Flashcards, Focus, Profile
```

---

## Required Permissions & System Configuration

1. **Microphone (`RECORD_AUDIO`)**: Requested at runtime when starting a lecture recording or voice study check.
2. **Foreground Service (`FOREGROUND_SERVICE_MICROPHONE`)**: Displays an ongoing notification during lecture recording.
3. **Usage Access (`PACKAGE_USAGE_STATS`)**: System permission required for FocusSentry distraction app detection. Grant via **Settings -> Apps -> Special App Access -> Usage Access -> OmniCampus AI**.
4. **Internet (`INTERNET`)**: Used for Gemini AI cloud generation. The app gracefully falls back to offline local extraction when disconnected.

---

## Verification & Testing

Run unit tests and local JVM Robolectric tests via:
```bash
gradle :app:testDebugUnitTest
```
