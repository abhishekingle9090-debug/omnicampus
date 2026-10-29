package com.example.recording

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "LectureAudioPlayer"

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val filePath: String? = null,
    val errorMessage: String? = null
)

class LectureAudioPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    fun playAudio(filePath: String) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            _playerState.value = _playerState.value.copy(
                errorMessage = "Audio file not found or empty."
            )
            return
        }

        try {
            stopAudio()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        isPaused = false,
                        currentPositionMs = duration
                    )
                    progressJob?.cancel()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        errorMessage = "Playback failed (Code: $what)"
                    )
                    true
                }
            }

            val dur = mediaPlayer?.duration ?: 0
            _playerState.value = AudioPlayerState(
                isPlaying = true,
                isPaused = false,
                durationMs = dur,
                filePath = filePath
            )

            startProgressTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio playback", e)
            _playerState.value = _playerState.value.copy(
                isPlaying = false,
                errorMessage = e.localizedMessage ?: "Playback initialization failed"
            )
        }
    }

    fun pauseAudio() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            _playerState.value = _playerState.value.copy(isPlaying = false, isPaused = true)
        }
    }

    fun resumeAudio() {
        if (_playerState.value.isPaused && mediaPlayer != null) {
            mediaPlayer?.start()
            _playerState.value = _playerState.value.copy(isPlaying = true, isPaused = false)
            startProgressTracker()
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
    }

    fun stopAudio() {
        progressJob?.cancel()
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        _playerState.value = AudioPlayerState()
    }

    fun release() {
        stopAudio()
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                delay(200)
                val pos = mediaPlayer?.currentPosition ?: 0
                _playerState.value = _playerState.value.copy(currentPositionMs = pos)
            }
        }
    }
}
