package com.example.recording

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
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
import java.io.IOException

private const val TAG = "LectureAudioRecorder"

enum class RecorderState {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED,
    ERROR
}

data class RecordingStatus(
    val state: RecorderState = RecorderState.IDLE,
    val durationSeconds: Long = 0L,
    val currentAmplitude: Int = 0,
    val outputFilePath: String? = null,
    val errorMessage: String? = null
)

class LectureAudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _status = MutableStateFlow(RecordingStatus())
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    private var recordingStartTime = 0L
    private var accumulatedSeconds = 0L

    fun startRecording(lectureId: String): Boolean {
        if (_status.value.state == RecorderState.RECORDING) {
            Log.w(TAG, "Recording is already active")
            return false
        }

        try {
            val outputDir = File(context.getExternalFilesDir(null), "lectures").apply {
                if (!exists()) mkdirs()
            }
            val audioFile = File(outputDir, "lecture_${lectureId}_${System.currentTimeMillis()}.m4a")
            currentFile = audioFile

            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            accumulatedSeconds = 0L
            recordingStartTime = System.currentTimeMillis()
            _status.value = RecordingStatus(
                state = RecorderState.RECORDING,
                durationSeconds = 0L,
                outputFilePath = audioFile.absolutePath
            )

            startTimerAndAmplitudeTracker()
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            _status.value = RecordingStatus(
                state = RecorderState.ERROR,
                errorMessage = "Microphone initialization failed: ${e.localizedMessage}"
            )
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting recording", e)
            _status.value = RecordingStatus(
                state = RecorderState.ERROR,
                errorMessage = e.localizedMessage ?: "Unknown recording error"
            )
            return false
        }
    }

    fun pauseRecording() {
        if (_status.value.state != RecorderState.RECORDING) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
                accumulatedSeconds += (System.currentTimeMillis() - recordingStartTime) / 1000
                _status.value = _status.value.copy(
                    state = RecorderState.PAUSED,
                    durationSeconds = accumulatedSeconds,
                    currentAmplitude = 0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause recorder", e)
        }
    }

    fun resumeRecording() {
        if (_status.value.state != RecorderState.PAUSED) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
                recordingStartTime = System.currentTimeMillis()
                _status.value = _status.value.copy(state = RecorderState.RECORDING)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume recorder", e)
        }
    }

    fun stopRecording(): File? {
        tickerJob?.cancel()
        val file = currentFile
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {}
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder", e)
        } finally {
            mediaRecorder = null
        }

        if (_status.value.state == RecorderState.RECORDING) {
            accumulatedSeconds += (System.currentTimeMillis() - recordingStartTime) / 1000
        }

        _status.value = RecordingStatus(
            state = RecorderState.STOPPED,
            durationSeconds = accumulatedSeconds,
            outputFilePath = file?.absolutePath,
            currentAmplitude = 0
        )
        return file
    }

    fun cancelRecording() {
        tickerJob?.cancel()
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {}
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null

        // Clean up uncommitted audio file
        currentFile?.let {
            if (it.exists()) it.delete()
        }
        currentFile = null

        _status.value = RecordingStatus(
            state = RecorderState.IDLE,
            durationSeconds = 0L,
            currentAmplitude = 0
        )
    }

    private fun startTimerAndAmplitudeTracker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && _status.value.state == RecorderState.RECORDING) {
                delay(200)
                if (_status.value.state == RecorderState.RECORDING) {
                    val currentSec = accumulatedSeconds + ((System.currentTimeMillis() - recordingStartTime) / 1000)
                    val amp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (_: Exception) { 0 }

                    _status.value = _status.value.copy(
                        durationSeconds = currentSec,
                        currentAmplitude = amp
                    )
                }
            }
        }
    }
}
