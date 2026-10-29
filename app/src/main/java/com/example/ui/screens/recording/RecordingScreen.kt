package com.example.ui.screens.recording

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.recording.RecorderState
import com.example.ui.components.AudioAmplitudeVisualizer
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandViolet
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.OmniCampusViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    viewModel: OmniCampusViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProcessing: (String) -> Unit
) {
    val context = LocalContext.current
    val recorderStatus by viewModel.wavRecorder.status.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val playerState by viewModel.audioPlayer.playerState.collectAsState()
    val micTestState by viewModel.micTestUtility.testState.collectAsState()

    var title by remember { mutableStateOf(viewModel.recordingFormTitle.value) }
    var topic by remember { mutableStateOf(viewModel.recordingFormTopic.value) }
    var description by remember { mutableStateOf(viewModel.recordingFormDesc.value) }
    var selectedLanguage by remember { mutableStateOf(viewModel.recordingLanguage.value) }
    var selectedSubjectId by remember {
        mutableStateOf(
            viewModel.recordingFormSubjectId.value.ifBlank {
                subjects.firstOrNull()?.id ?: ""
            }
        )
    }

    var showCancelDialog by remember { mutableStateOf(false) }
    var showMicTestModal by remember { mutableStateOf(false) }

    val languages = listOf(
        "en-US" to "English (Global)",
        "en-IN" to "English (Indian Accent)",
        "hi-IN" to "Hindi (हिन्दी)",
        "mr-IN" to "Marathi (मराठी)",
        "auto" to "Hinglish / Mixed"
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordGranted) {
            val lectureId = UUID.randomUUID().toString()
            viewModel.startLectureRecording(lectureId, title)
        } else {
            Toast.makeText(context, "Microphone permission is required to capture lectures.", Toast.LENGTH_LONG).show()
        }
    }

    val isRecording = recorderStatus.state == RecorderState.RECORDING
    val isPaused = recorderStatus.state == RecorderState.PAUSED
    val isStopped = recorderStatus.state == RecorderState.STOPPED
    val isIdle = recorderStatus.state == RecorderState.IDLE || isStopped

    val minutes = recorderStatus.durationSeconds / 60
    val seconds = recorderStatus.durationSeconds % 60
    val formattedTimer = String.format("%02d:%02d", minutes, seconds)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Classroom Audio Recorder") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!isIdle) {
                            showCancelDialog = true
                        } else {
                            viewModel.audioPlayer.stopAudio()
                            onNavigateBack()
                        }
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isIdle) {
                        TextButton(onClick = {
                            showMicTestModal = true
                            viewModel.micTestUtility.startMicTest()
                        }) {
                            Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mic Test", color = BrandCyan, fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .testTag("recording_screen"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Placement Advice Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Classroom Tip: Place phone near the teacher with an unobstructed microphone. Confirm the audio level bars animate when the teacher speaks.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Recording Timer & Waveform Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isRecording -> ErrorRed.copy(alpha = 0.15f)
                            isPaused -> WarningAmber.copy(alpha = 0.15f)
                            isStopped -> SuccessGreen.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isRecording -> ErrorRed
                                            isPaused -> WarningAmber
                                            isStopped -> SuccessGreen
                                            else -> MaterialTheme.colorScheme.outline
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isRecording -> "RECORDING LIVE"
                                    isPaused -> "RECORDING PAUSED"
                                    isStopped -> "RECORDING SAVED"
                                    else -> "MICROPHONE READY"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    isRecording -> ErrorRed
                                    isPaused -> WarningAmber
                                    isStopped -> SuccessGreen
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = formattedTimer,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 46.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AudioAmplitudeVisualizer(
                        amplitude = recorderStatus.currentAmplitude,
                        isRecording = isRecording
                    )
                }
            }

            // Post-Recording Audio Playback Card (Step 3: Save first, verify audio playback second!)
            if (recorderStatus.outputFilePath != null && isIdle) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Verify Audio Playback",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Listen back to confirm teacher's voice is distinct.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (playerState.isPlaying) {
                                        viewModel.audioPlayer.pauseAudio()
                                    } else {
                                        recorderStatus.outputFilePath?.let { viewModel.audioPlayer.playAudio(it) }
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(BrandViolet)
                                    .testTag("btn_playback_play_pause")
                            ) {
                                Icon(
                                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play Audio",
                                    tint = Color.White
                                )
                            }
                        }

                        if (playerState.durationMs > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val currentPosSec = playerState.currentPositionMs / 1000
                            val totalDurSec = playerState.durationMs / 1000
                            Slider(
                                value = playerState.currentPositionMs.toFloat(),
                                onValueChange = { viewModel.audioPlayer.seekTo(it.toInt()) },
                                valueRange = 0f..playerState.durationMs.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = BrandViolet, activeTrackColor = BrandViolet)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = String.format("%02d:%02d", currentPosSec / 60, currentPosSec % 60), style = MaterialTheme.typography.labelSmall)
                                Text(text = String.format("%02d:%02d", totalDurSec / 60, totalDurSec % 60), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Form Inputs (Title, Subject, Language)
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    viewModel.recordingFormTitle.value = it
                },
                label = { Text("Lecture Title *") },
                placeholder = { Text("e.g. Raft Consensus Algorithm") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lecture_title_input"),
                enabled = isIdle,
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subject Selector
            Text(
                text = "Subject *",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subjects.forEach { subject ->
                    val isSelected = subject.id == selectedSubjectId
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) BrandViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = isIdle) {
                                selectedSubjectId = subject.id
                                viewModel.recordingFormSubjectId.value = subject.id
                            },
                        color = if (isSelected) BrandViolet.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = subject.code.ifBlank { subject.name },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) BrandViolet else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Language Selector
            Text(
                text = "Lecture Spoken Language *",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                languages.take(3).forEach { (code, label) ->
                    val isSelected = selectedLanguage == code
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) BrandCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = isIdle) {
                                selectedLanguage = code
                                viewModel.recordingLanguage.value = code
                            },
                        color = if (isSelected) BrandCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            ),
                            color = if (isSelected) BrandCyan else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = topic,
                onValueChange = {
                    topic = it
                    viewModel.recordingFormTopic.value = it
                },
                label = { Text("Topic (Optional)") },
                placeholder = { Text("e.g. Terms, Heartbeats, Election Timeouts") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isIdle,
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            if (isIdle) {
                Button(
                    onClick = {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasMic) {
                            val lectureId = UUID.randomUUID().toString()
                            viewModel.startLectureRecording(lectureId, title)
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_recording_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandViolet)
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Recording Lecture",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("cancel_recording_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = ErrorRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Discard", color = ErrorRed)
                    }

                    Button(
                        onClick = {
                            if (isRecording) viewModel.pauseLectureRecording()
                            else viewModel.resumeLectureRecording()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("pause_resume_recording_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isRecording) "Pause" else "Resume",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.stopAndSaveLecture { savedId ->
                                onNavigateToProcessing(savedId)
                            }
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(50.dp)
                            .testTag("stop_recording_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Audio", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Cancel Recording Dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Discard Recording?") },
            text = { Text("Are you sure you want to discard this recording? The audio file will be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelLectureRecording()
                        showCancelDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Discard", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Continue")
                }
            }
        )
    }

    // 5-Second Mic Test Modal
    if (showMicTestModal) {
        AlertDialog(
            onDismissRequest = {
                viewModel.micTestUtility.resetTest()
                showMicTestModal = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = BrandCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Classroom Microphone Test")
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (micTestState.isTesting) "Speak at your normal classroom volume... (${micTestState.secondsRemaining}s)" else "Microphone Test Result",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    AudioAmplitudeVisualizer(
                        amplitude = micTestState.currentRms,
                        isRecording = micTestState.isTesting
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (micTestState.isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (micTestState.passed) SuccessGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = micTestState.recommendation ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.micTestUtility.resetTest()
                        showMicTestModal = false
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }
}
