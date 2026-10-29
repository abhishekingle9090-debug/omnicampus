package com.example.ui.screens.lectures

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.LectureEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.screens.home.StatusBadge
import com.example.ui.theme.BrandViolet
import com.example.ui.theme.ErrorRed
import com.example.viewmodel.OmniCampusViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LecturesScreen(
    viewModel: OmniCampusViewModel,
    onNavigateToRecord: () -> Unit,
    onNavigateToTranscript: (String) -> Unit
) {
    val allLectures by viewModel.lectures.collectAsState()
    val subjects by viewModel.subjects.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf<String?>(null) }
    var lectureToDelete by remember { mutableStateOf<LectureEntity?>(null) }

    val filteredLectures = allLectures.filter { lecture ->
        val matchesSubject = selectedSubjectId == null || lecture.subjectId == selectedSubjectId
        val matchesQuery = query.isBlank() ||
            lecture.title.contains(query, ignoreCase = true) ||
            lecture.topic.contains(query, ignoreCase = true) ||
            lecture.description.contains(query, ignoreCase = true)
        matchesSubject && matchesQuery
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToRecord,
                containerColor = BrandViolet,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_record_lecture")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Record Lecture")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .testTag("lectures_screen")
        ) {
            // Header & Search
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Lecture Vault",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by title, topic, or subject...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lectures_search_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Subject Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SubjectFilterChip(
                            label = "All Subjects",
                            isSelected = selectedSubjectId == null,
                            onClick = { selectedSubjectId = null }
                        )
                    }
                    items(subjects) { subject ->
                        SubjectFilterChip(
                            label = subject.code.ifBlank { subject.name },
                            isSelected = selectedSubjectId == subject.id,
                            onClick = { selectedSubjectId = subject.id }
                        )
                    }
                }
            }

            // Lectures List
            if (filteredLectures.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Mic,
                    title = "No lectures found",
                    message = if (query.isNotBlank()) "No lectures matched '$query'." else "You haven't recorded any lectures for this subject yet."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    items(filteredLectures) { lecture ->
                        LectureCardItem(
                            lecture = lecture,
                            subjectName = subjects.firstOrNull { it.id == lecture.subjectId }?.name ?: "Subject",
                            onOpenTranscript = { onNavigateToTranscript(lecture.id) },
                            onDelete = { lectureToDelete = lecture },
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    lectureToDelete?.let { lecture ->
        AlertDialog(
            onDismissRequest = { lectureToDelete = null },
            title = { Text("Delete Lecture?") },
            text = { Text("Are you sure you want to delete '${lecture.title}'? This will safely remove the audio file, transcript, generated notes, and linked flashcards.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLecture(lecture)
                        lectureToDelete = null
                    }
                ) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { lectureToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SubjectFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) BrandViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        color = if (isSelected) BrandViolet.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isSelected) BrandViolet else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LectureCardItem(
    lecture: LectureEntity,
    subjectName: String,
    onOpenTranscript: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationMin = lecture.durationSeconds / 60
    val durationSec = lecture.durationSeconds % 60
    val durationFormatted = String.format("%02d:%02d", durationMin, durationSec)

    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val dateString = dateFormat.format(Date(lecture.recordedAt))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenTranscript() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subjectName,
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandViolet
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete lecture",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = lecture.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (lecture.topic.isNotBlank()) {
                Text(
                    text = lecture.topic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = lecture.status)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$durationFormatted • $dateString",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandViolet
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Study",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
