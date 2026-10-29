package com.example.ui.screens.lectures

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.NoteEntity
import com.example.export.NoteExporter
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandViolet
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.OmniCampusViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: String,
    viewModel: OmniCampusViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsState()
    val note = notes.firstOrNull { it.id == noteId }

    val subjects by viewModel.subjects.collectAsState()
    val subject = subjects.firstOrNull { it.id == note?.subjectId }
    val subjectName = subject?.name ?: "Academic Note"

    val lectures by viewModel.lectures.collectAsState()
    val lecture = lectures.firstOrNull { it.id == note?.lectureId }
    val lectureTitle = lecture?.title ?: "Recorded Lecture"

    var studentNotes by remember(note) { mutableStateOf(note?.customNotes ?: "") }
    var isEditingCustomNotes by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(note?.title ?: "Note Reader", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            note?.let {
                                val file = NoteExporter.exportToPdf(context, it, subjectName, lectureTitle)
                                NoteExporter.shareFile(context, file, "application/pdf")
                            }
                        },
                        modifier = Modifier.testTag("export_pdf_button")
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = BrandCyan)
                    }

                    IconButton(
                        onClick = {
                            note?.let {
                                val file = NoteExporter.exportToMarkdown(context, it, subjectName, lectureTitle)
                                NoteExporter.shareFile(context, file, "text/markdown")
                            }
                        },
                        modifier = Modifier.testTag("export_markdown_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share Markdown", tint = BrandViolet)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (note == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Note not found or deleted.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("note_detail_screen")
            ) {
                // Header Meta
                val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                val dateString = dateFormat.format(Date(note.createdAt))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BrandViolet.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${note.style} • ${note.detailLevel}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = BrandViolet,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = note.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Overview Card
                SectionContainer(title = "Overview", icon = Icons.Default.Bookmark, accentColor = BrandViolet) {
                    Text(
                        text = note.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Key Concepts
                val keyConcepts = parseJsonList(note.keyConceptsJson)
                if (keyConcepts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionContainer(title = "Key Concepts", accentColor = BrandCyan) {
                        keyConcepts.forEach { concept ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(text = "•", color = BrandCyan, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = concept,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Definitions
                val definitions = parseDefinitions(note.definitionsJson)
                if (definitions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionContainer(title = "Core Definitions", accentColor = SuccessGreen) {
                        definitions.forEach { (term, def) ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = term,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = def,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Formulas
                val formulas = parseFormulas(note.formulasJson)
                if (formulas.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionContainer(title = "Formulas & Equations", accentColor = BrandViolet) {
                        formulas.forEach { (name, formula, desc) ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = formula,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = BrandCyan,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                if (desc.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Summary
                Spacer(modifier = Modifier.height(16.dp))
                SectionContainer(title = "Summary Synthesis", accentColor = BrandCyan) {
                    Text(
                        text = note.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Revision Questions
                val revisionQuestions = parseJsonList(note.revisionQuestionsJson)
                if (revisionQuestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionContainer(title = "Self-Assessment Questions", accentColor = BrandViolet) {
                        revisionQuestions.forEachIndexed { index, q ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = BrandViolet
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = q,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Student Annotations / Custom Notes
                Spacer(modifier = Modifier.height(20.dp))
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
                            Text(
                                text = "Personal Annotations",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { isEditingCustomNotes = !isEditingCustomNotes }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit annotations",
                                    tint = BrandViolet
                                )
                            }
                        }

                        if (isEditingCustomNotes) {
                            OutlinedTextField(
                                value = studentNotes,
                                onValueChange = { studentNotes = it },
                                placeholder = { Text("Add your own study notes, doubts, or professor remarks...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                maxLines = 6,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    viewModel.updateCustomNoteContent(note, studentNotes)
                                    isEditingCustomNotes = false
                                    Toast.makeText(context, "Annotations saved", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandViolet),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Save Annotations")
                            }
                        } else {
                            Text(
                                text = if (studentNotes.isNotBlank()) studentNotes else "No personal annotations yet. Tap edit above to add notes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (studentNotes.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SectionContainer(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(4.dp, 16.dp)
                        .background(accentColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

private fun parseJsonList(jsonString: String): List<String> {
    val list = mutableListOf<String>()
    try {
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) list.add(array.getString(i))
    } catch (_: Exception) {}
    return list
}

private fun parseDefinitions(jsonString: String): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    try {
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(obj.optString("term", "") to obj.optString("definition", ""))
        }
    } catch (_: Exception) {}
    return list
}

private fun parseFormulas(jsonString: String): List<Triple<String, String, String>> {
    val list = mutableListOf<Triple<String, String, String>>()
    try {
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Triple(
                    obj.optString("name", ""),
                    obj.optString("formula", ""),
                    obj.optString("description", "")
                )
            )
        }
    } catch (_: Exception) {}
    return list
}
