package com.example.recording

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import java.io.File

data class MicTestResult(
    val isTesting: Boolean = false,
    val secondsRemaining: Int = 5,
    val currentRms: Int = 0,
    val peakRms: Int = 0,
    val averageRms: Int = 0,
    val isCompleted: Boolean = false,
    val passed: Boolean = false,
    val recommendation: String? = null
)

class MicTestUtility(private val context: Context) {

    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(2048)

    private val scope = CoroutineScope(Dispatchers.IO)
    private var testJob: Job? = null

    private val _testState = MutableStateFlow(MicTestResult())
    val testState: StateFlow<MicTestResult> = _testState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startMicTest() {
        testJob?.cancel()
        _testState.value = MicTestResult(isTesting = true, secondsRemaining = 5)

        testJob = scope.launch {
            var audioRecord: AudioRecord? = null
            var peak = 0
            var sumRms = 0.0
            var count = 0

            try {
                audioRecord = try {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                } catch (_: Exception) {
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                }

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    _testState.value = MicTestResult(
                        isTesting = false,
                        isCompleted = true,
                        passed = false,
                        recommendation = "Microphone hardware could not be initialized. Check device permissions."
                    )
                    return@launch
                }

                audioRecord.startRecording()
                val buffer = ShortArray(bufferSize / 2)
                val testStart = System.currentTimeMillis()

                while (isActive && (System.currentTimeMillis() - testStart) < 5000) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i]
                            sum += sample * sample
                        }
                        val rms = Math.sqrt(sum / read).toInt()
                        if (rms > peak) peak = rms
                        sumRms += rms
                        count++

                        val elapsed = (System.currentTimeMillis() - testStart) / 1000
                        val remaining = (5 - elapsed).toInt().coerceAtLeast(0)

                        _testState.value = _testState.value.copy(
                            currentRms = rms,
                            peakRms = peak,
                            secondsRemaining = remaining
                        )
                    }
                    delay(80)
                }

                val avg = if (count > 0) (sumRms / count).toInt() else 0
                val passed = peak >= 600 || avg >= 200

                val recommendation = when {
                    avg < 150 && peak < 400 -> "Audio level is very low. Move the phone closer to the lecturer and avoid covering the microphone."
                    avg > 8000 -> "Audio level is extremely loud. You may encounter clipping or distortion; place phone slightly further away."
                    else -> "Microphone test passed! Classroom speech should be captured clearly."
                }

                _testState.value = MicTestResult(
                    isTesting = false,
                    isCompleted = true,
                    passed = passed,
                    peakRms = peak,
                    averageRms = avg,
                    recommendation = recommendation
                )
            } catch (e: Exception) {
                _testState.value = MicTestResult(
                    isTesting = false,
                    isCompleted = true,
                    passed = false,
                    recommendation = "Test failed: ${e.localizedMessage}"
                )
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun resetTest() {
        testJob?.cancel()
        _testState.value = MicTestResult()
    }
}
