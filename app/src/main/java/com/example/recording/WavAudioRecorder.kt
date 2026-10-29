package com.example.recording

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "WavAudioRecorder"

data class AudioDiagnostics(
    val filePath: String,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val averageRms: Double,
    val peakRms: Double,
    val isSilent: Boolean,
    val isValid: Boolean,
    val formatDescription: String
)

class WavAudioRecorder(private val context: Context) {

    private var actualSampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isRecording = false

    @Volatile
    private var isPaused = false

    private var currentWavFile: File? = null
    private var recordingStartTime = 0L
    private var accumulatedSeconds = 0L

    private var peakRmsObserved = 0.0
    private var sumRms = 0.0
    private var rmsCount = 0

    private val _status = MutableStateFlow(RecordingStatus())
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    /**
     * Initializes a hardware-compatible AudioRecord instance.
     * Prioritizes VOICE_RECOGNITION for crystal-clear microphone audio with noise suppression,
     * testing 16000 Hz, 44100 Hz, and 48000 Hz for universal smartphone compatibility.
     */
    @SuppressLint("MissingPermission")
    private fun createOptimizedAudioRecord(): Pair<AudioRecord, Int>? {
        val candidateSources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )
        val candidateSampleRates = intArrayOf(16000, 44100, 48000)

        for (source in candidateSources) {
            for (rate in candidateSampleRates) {
                val minBuffer = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat)
                if (minBuffer > 0) {
                    val bufferSize = (minBuffer * 4).coerceAtLeast(8192)
                    try {
                        val record = AudioRecord(
                            source,
                            rate,
                            channelConfig,
                            audioFormat,
                            bufferSize
                        )
                        if (record.state == AudioRecord.STATE_INITIALIZED) {
                            Log.i(TAG, "AudioRecord initialized successfully (Source: $source, Rate: $rate Hz, Buffer: $bufferSize bytes)")
                            return Pair(record, rate)
                        } else {
                            record.release()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed creating AudioRecord (Source: $source, Rate: $rate): ${e.message}")
                    }
                }
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun startRecording(lectureId: String): Boolean {
        if (isRecording || _status.value.state == RecorderState.RECORDING) {
            Log.w(TAG, "Recording is already active")
            return false
        }

        try {
            val recordPair = createOptimizedAudioRecord()
            if (recordPair == null) {
                Log.e(TAG, "Failed to initialize microphone hardware")
                _status.value = RecordingStatus(
                    state = RecorderState.ERROR,
                    errorMessage = "Microphone hardware initialization failed. Please check app permissions."
                )
                return false
            }

            audioRecord = recordPair.first
            actualSampleRate = recordPair.second

            val outputDir = File(context.getExternalFilesDir(null), "lectures").apply {
                if (!exists()) mkdirs()
            }
            val wavFile = File(outputDir, "lecture_${lectureId}_${System.currentTimeMillis()}.wav")
            currentWavFile = wavFile

            accumulatedSeconds = 0L
            recordingStartTime = System.currentTimeMillis()
            isPaused = false
            isRecording = true
            peakRmsObserved = 0.0
            sumRms = 0.0
            rmsCount = 0

            audioRecord?.startRecording()
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord failed to start recording state")
                audioRecord?.release()
                audioRecord = null
                isRecording = false
                _status.value = RecordingStatus(
                    state = RecorderState.ERROR,
                    errorMessage = "Microphone failed to start audio capture."
                )
                return false
            }

            _status.value = RecordingStatus(
                state = RecorderState.RECORDING,
                durationSeconds = 0L,
                outputFilePath = wavFile.absolutePath
            )

            startRecordingLoop(wavFile)
            startDurationTicker()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord recording", e)
            _status.value = RecordingStatus(
                state = RecorderState.ERROR,
                errorMessage = e.localizedMessage ?: "Failed to start recording"
            )
            isRecording = false
            return false
        }
    }

    private fun startRecordingLoop(outputFile: File) {
        recordingJob?.cancel()
        recordingJob = scope.launch {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (_: Exception) {}

            val fos = FileOutputStream(outputFile)
            val bos = BufferedOutputStream(fos, 32768)

            // Reserve standard 44-byte WAV header at the start of the file
            bos.write(ByteArray(44))

            val minBuf = AudioRecord.getMinBufferSize(actualSampleRate, channelConfig, audioFormat).coerceAtLeast(2048)
            val buffer = ByteArray(minBuf * 2)

            try {
                while (isRecording && isActive) {
                    if (isPaused) {
                        Thread.sleep(100)
                        continue
                    }

                    val record = audioRecord ?: break
                    val bytesRead = record.read(buffer, 0, buffer.size)

                    if (bytesRead > 0) {
                        bos.write(buffer, 0, bytesRead)

                        // Compute true PCM 16-bit RMS amplitude for live UI feedback
                        val numSamples = bytesRead / 2
                        var sum = 0.0
                        for (i in 0 until numSamples) {
                            val low = buffer[i * 2].toInt() and 0xFF
                            val high = buffer[i * 2 + 1].toInt() // sign-extended
                            val sample = (high shl 8) or low
                            sum += sample.toLong() * sample.toLong()
                        }

                        val rms = if (numSamples > 0) Math.sqrt(sum / numSamples) else 0.0
                        if (rms > peakRmsObserved) peakRmsObserved = rms
                        sumRms += rms
                        rmsCount++

                        _status.value = _status.value.copy(
                            currentAmplitude = rms.toInt()
                        )
                    } else if (bytesRead < 0) {
                        Log.w(TAG, "AudioRecord read returned error code: $bytesRead")
                        Thread.sleep(20)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio recording loop", e)
            } finally {
                try {
                    bos.flush()
                    bos.close()
                    fos.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing audio output stream", e)
                }
            }
        }
    }

    private fun startDurationTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && isRecording) {
                kotlinx.coroutines.delay(500)
                if (!isPaused) {
                    val sec = accumulatedSeconds + ((System.currentTimeMillis() - recordingStartTime) / 1000)
                    _status.value = _status.value.copy(durationSeconds = sec)
                }
            }
        }
    }

    fun pauseRecording() {
        if (!isRecording || isPaused) return
        isPaused = true
        accumulatedSeconds += (System.currentTimeMillis() - recordingStartTime) / 1000
        _status.value = _status.value.copy(state = RecorderState.PAUSED, currentAmplitude = 0)
    }

    fun resumeRecording() {
        if (!isRecording || !isPaused) return
        isPaused = false
        recordingStartTime = System.currentTimeMillis()
        _status.value = _status.value.copy(state = RecorderState.RECORDING)
    }

    fun stopRecording(): File? {
        tickerJob?.cancel()
        isRecording = false
        isPaused = false

        // 1. Wait synchronously for the background loop to flush and close the file stream
        runBlocking(Dispatchers.IO) {
            try {
                recordingJob?.join()
            } catch (_: Exception) {}
        }

        // 2. Safely stop and release AudioRecord hardware
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }

        // 3. Finalize valid 44-byte RIFF/WAVE header directly on the closed file
        val file = currentWavFile
        file?.let { targetFile ->
            if (targetFile.exists()) {
                val totalAudioLen = (targetFile.length() - 44).coerceAtLeast(0)
                writeWavHeader(targetFile, totalAudioLen, actualSampleRate, 1, 16)
                Log.i(TAG, "Finalized WAV: ${targetFile.absolutePath}, audioBytes=$totalAudioLen, rate=$actualSampleRate")
            }
        }

        accumulatedSeconds += (System.currentTimeMillis() - recordingStartTime) / 1000

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
        isRecording = false
        isPaused = false

        runBlocking(Dispatchers.IO) {
            try {
                recordingJob?.join()
            } catch (_: Exception) {}
        }

        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
                release()
            }
        } catch (_: Exception) {}
        audioRecord = null

        currentWavFile?.let { if (it.exists()) it.delete() }
        currentWavFile = null

        _status.value = RecordingStatus(state = RecorderState.IDLE)
    }

    fun getDiagnostics(file: File?): AudioDiagnostics {
        val target = file ?: currentWavFile
        if (target == null || !target.exists()) {
            return AudioDiagnostics(
                filePath = "",
                durationSeconds = 0,
                fileSizeBytes = 0,
                averageRms = 0.0,
                peakRms = 0.0,
                isSilent = true,
                isValid = false,
                formatDescription = "Audio file missing"
            )
        }

        val size = target.length()
        val isWav = target.extension.equals("wav", ignoreCase = true)

        var detectedSampleRate = actualSampleRate
        if (isWav && size >= 44) {
            try {
                RandomAccessFile(target, "r").use { raf ->
                    raf.seek(24)
                    val b0 = raf.read()
                    val b1 = raf.read()
                    val b2 = raf.read()
                    val b3 = raf.read()
                    val rate = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
                    if (rate in 8000..96000) {
                        detectedSampleRate = rate
                    }
                }
            } catch (_: Exception) {}
        }

        val durationSec = if (isWav) {
            val dataBytes = (size - 44).coerceAtLeast(0)
            dataBytes / (detectedSampleRate * 2 * 1)
        } else {
            size / 16000
        }

        var avgRms = if (rmsCount > 0) sumRms / rmsCount else 0.0
        var peakRms = peakRmsObserved

        if (isWav && rmsCount == 0 && size > 44) {
            try {
                java.io.FileInputStream(target).use { fis ->
                    fis.skip(44)
                    val sampleBuffer = ByteArray(32768.coerceAtMost((size - 44).toInt()))
                    val readBytes = fis.read(sampleBuffer)
                    if (readBytes > 1) {
                        var sampleSum = 0.0
                        var localPeak = 0.0
                        val shortsCount = readBytes / 2
                        val bb = ByteBuffer.wrap(sampleBuffer, 0, readBytes).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until shortsCount) {
                            val sample = bb.short.toDouble()
                            val absVal = Math.abs(sample)
                            if (absVal > localPeak) localPeak = absVal
                            sampleSum += sample * sample
                        }
                        if (shortsCount > 0) {
                            avgRms = Math.sqrt(sampleSum / shortsCount)
                            peakRms = localPeak
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not sample WAV audio amplitude from disk: ${e.message}")
            }
        }

        val isSilent = size <= 44 || (avgRms < 80 && peakRms < 250)
        val isValid = size > 1024 && durationSec >= 1

        val formatDesc = if (isWav) {
            "PCM 16-bit Mono WAV @ ${detectedSampleRate} Hz (${size / 1024} KB)"
        } else {
            "AAC Compressed Audio (${size / 1024} KB)"
        }

        return AudioDiagnostics(
            filePath = target.absolutePath,
            durationSeconds = durationSec,
            fileSizeBytes = size,
            sampleRate = detectedSampleRate,
            channels = 1,
            bitDepth = 16,
            averageRms = avgRms,
            peakRms = peakRms,
            isSilent = isSilent,
            isValid = isValid,
            formatDescription = formatDesc
        )
    }

    private fun writeWavHeader(
        file: File,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitDepth / 8).toLong()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            val header = ByteArray(44)
            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            bb.order(ByteOrder.BIG_ENDIAN).putInt(0x52494646) // "RIFF"
            bb.order(ByteOrder.LITTLE_ENDIAN).putInt(totalDataLen.toInt())
            bb.order(ByteOrder.BIG_ENDIAN).putInt(0x57415645) // "WAVE"

            // fmt subchunk
            bb.order(ByteOrder.BIG_ENDIAN).putInt(0x666d7420) // "fmt "
            bb.order(ByteOrder.LITTLE_ENDIAN).putInt(16) // Subchunk1Size for PCM
            bb.putShort(1.toShort()) // AudioFormat 1 = PCM
            bb.putShort(channels.toShort())
            bb.putInt(sampleRate)
            bb.putInt(byteRate.toInt())
            bb.putShort((channels * bitDepth / 8).toShort()) // BlockAlign
            bb.putShort(bitDepth.toShort())

            // data subchunk
            bb.order(ByteOrder.BIG_ENDIAN).putInt(0x64617461) // "data"
            bb.order(ByteOrder.LITTLE_ENDIAN).putInt(totalAudioLen.toInt())

            raf.write(header)
        }
    }
}
