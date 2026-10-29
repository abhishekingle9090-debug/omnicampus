package com.example.ui.screens.lectures

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.LectureEntity
import com.example.data.local.entity.TranscriptEntity
import com.example.transcription.TranscriptCorrectionHelper
import com.example.transcription.TranscriptSegment
import com.example.transcription.WordDiff
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandViolet
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.OmniCampusViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptEditorScreen(
    lectureId: String,
    viewModel: OmniCampusViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNoteGen: (String) -> Unit
) {
    val lectures by viewModel.lectures.collectAsState()
    val lecture = lectures.firstOrNull { it.id == lectureId }

    val transcriptFlow = remember(lectureId) { viewModel.repository.getTranscriptByLecture(lectureId) }
    val transcript by transcriptFlow.collectAsState(initial = null)

    val transcriptionState by viewModel.transcriptionState.collectAsState()
    val reviewDiffs by viewModel.reviewDiffs.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Confirmed/Editable, 1 = Word Diff Review, 2 = Original
    var editableText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var hasInitializedText by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val playerState by viewModel.audioPlayer.playerState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.audioPlayer.release()
        }
    }

    LaunchedEffect(transcript) {
        transcript?.let {
            if (!hasInitializedText) {
                editableText = it.correctedText.ifBlank { it.originalText }
                viewModel.prepareWordReview(it.originalText)
                hasInitializedText = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = lecture?.title ?: "Transcript Review",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        Text(
                            text = if (transcript?.isEdited == true) "Confirmed & Edited" else "Original Recognition",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (transcript?.isEdited == true) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (transcript != null) {
                        Button(
                            onClick = { onNavigateToNoteGen(lectureId) },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandViolet),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("btn_proceed_to_notes")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generate Notes", fontSize = 12.sp)
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
                .background(MaterialTheme.colorScheme.background)
                .testTag("transcript_editor_screen")
        ) {
            // If transcript is not yet created or failed, offer Speech-to-Text Action
            if (transcript == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(BrandViolet.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = BrandViolet,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Transcript Pending",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Execute speech-to-text to convert your lecture audio into searchable text, ready for AI notes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (transcriptionState.isTranscribing) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LinearProgressIndicator(
                                progress = { transcriptionState.progress },
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = BrandCyan
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Transcribing lecture audio... ${(transcriptionState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Button(
                            onClick = { lecture?.let { viewModel.transcribeLecture(it) } },
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("btn_run_transcription"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandViolet)
                        ) {
                            Text("Run Speech-to-Text Recognition")
                        }

                        transcriptionState.error?.let { err ->
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = err,
                                color = ErrorRed,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Audio Playback Bar for simultaneous listening and editing
                if (!lecture?.audioFilePath.isNullOrBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (playerState.isPlaying) {
                                        viewModel.audioPlayer.pauseAudio()
                                    } else {
                                        lecture?.audioFilePath?.let { viewModel.audioPlayer.playAudio(it) }
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(BrandViolet)
                            ) {
                                Icon(
                                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            val curPos = playerState.currentPositionMs
                            val totalDur = playerState.durationMs.coerceAtLeast((lecture?.durationSeconds ?: 0L).toInt() * 1000)
                            Slider(
                                value = curPos.toFloat().coerceIn(0f, totalDur.toFloat().coerceAtLeast(1f)),
                                onValueChange = { viewModel.audioPlayer.seekTo(it.toInt()) },
                                valueRange = 0f..totalDur.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(thumbColor = BrandViolet, activeTrackColor = BrandViolet),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val curSec = curPos / 1000
                            val totSec = totalDur / 1000
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, totSec / 60, totSec % 60),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Transcript is present: Review tabs and editor
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = BrandViolet
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Confirmed Text") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val changesCount = reviewDiffs.count { it.isChanged }
                            Text("Word Review ($changesCount)")
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Original") }
                    )
                }

                // In-transcript search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search text within transcript...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                when (selectedTab) {
                    0 -> {
                        // Editable / Confirmed text
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            val wordCount = editableText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$wordCount words • ${editableText.length} characters",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tap text to edit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandViolet
                                )
                            }

                            OutlinedTextField(
                                value = editableText,
                                onValueChange = { editableText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("transcript_text_editor"),
                                shape = RoundedCornerShape(14.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        transcript?.let {
                                            viewModel.saveConfirmedTranscript(it, editableText)
                                            Toast.makeText(context, "Transcript saved successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(48.dp)
                                        .testTag("save_transcript_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Changes")
                                }

                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(editableText))
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy")
                                }

                                OutlinedButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, lecture?.title ?: "Lecture Transcript")
                                            putExtra(Intent.EXTRA_TEXT, editableText)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Transcript"))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }

                    1 -> {
                        // Word Diff / Correction Review
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = "Acoustic & Semantic Word Review",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Speech recognition can confuse phonetically similar words (e.g. 'hi' vs 'bye', technical acronyms). OmniCampus preserves your original audio and lets you review suggestions individually before note synthesis.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            val changedDiffs = reviewDiffs.filter { it.isChanged }
                            if (changedDiffs.isEmpty()) {
                                item {
                                    Text(
                                        text = "No ambiguous words detected. The transcript looks consistent!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SuccessGreen,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                itemsIndexed(reviewDiffs) { index, diff ->
                                    if (diff.isChanged) {
                                        WordDiffReviewCard(
                                            diff = diff,
                                            onAccept = {
                                                viewModel.toggleDiffAccepted(index)
                                                transcript?.let {
                                                    val updated = TranscriptCorrectionHelper.applyAcceptedCorrections(reviewDiffs)
                                                    editableText = updated
                                                }
                                            },
                                            onReject = {
                                                viewModel.toggleDiffRejected(index)
                                                transcript?.let {
                                                    val updated = TranscriptCorrectionHelper.applyAcceptedCorrections(reviewDiffs)
                                                    editableText = updated
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // Original Raw Transcript (Read-only, preserves verbatim recording output)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Verbatim Engine Output (Immutable)",
                                style = MaterialTheme.typography.labelMedium,
                                color = BrandViolet
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(
                                    text = transcript?.originalText ?: "",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordDiffReviewCard(
    diff: WordDiff,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                diff.isAccepted -> SuccessGreen.copy(alpha = 0.1f)
                diff.isRejected -> ErrorRed.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = diff.originalWord,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (diff.isAccepted) MaterialTheme.colorScheme.onSurfaceVariant else ErrorRed
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "➔", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = diff.suggestedWord,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (diff.isAccepted) SuccessGreen else BrandCyan
                        )
                    )
                }
                diff.reason?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onReject,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (diff.isRejected) ErrorRed else MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Keep original word",
                        tint = if (diff.isRejected) Color.White else ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onAccept,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (diff.isAccepted) SuccessGreen else MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Accept suggestion",
                        tint = if (diff.isAccepted) Color.White else SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
