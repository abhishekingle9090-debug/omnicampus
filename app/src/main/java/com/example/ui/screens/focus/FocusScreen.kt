package com.example.ui.screens.focus

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.DistractionAppEntity
import com.example.ui.components.MetricStatCard
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandViolet
import com.example.ui.theme.DeepNavyDark
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.OmniCampusViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: OmniCampusViewModel
) {
    val context = LocalContext.current
    val sessionState by viewModel.focusSentryManager.sessionState.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val allConfiguredApps by viewModel.allConfiguredApps.collectAsState()

    val totalFocusMinutes by viewModel.totalFocusMinutes.collectAsState()
    val completedSessions by viewModel.completedFocusSessionsCount.collectAsState()
    val totalDistractions by viewModel.totalDistractionsCount.collectAsState()

    var selectedMinutes by remember { mutableIntStateOf(25) }
    var selectedSubjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    var sessionGoal by remember { mutableStateOf("") }
    var verificationAnswerInput by remember { mutableStateOf("") }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var newAppPkg by remember { mutableStateOf("") }
    var newAppName by remember { mutableStateOf("") }

    val hasUsageAccess = viewModel.focusSentryManager.hasUsageStatsPermission()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FocusSentry Engine") },
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
                .verticalScroll(rememberScrollState())
                .testTag("focus_screen")
        ) {
            // Main Focus Timer Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (sessionState.isActive) {
                        // Active Timer State
                        val remainingMin = sessionState.remainingSeconds / 60
                        val remainingSec = sessionState.remainingSeconds % 60
                        val timerText = String.format("%02d:%02d", remainingMin, remainingSec)

                        val totalDurationSec = (sessionState.plannedMinutes * 60).toFloat()
                        val progressFraction = (sessionState.remainingSeconds.toFloat() / totalDurationSec.coerceAtLeast(1f)).coerceIn(0f, 1f)

                        Text(
                            text = sessionState.subjectName.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                            color = BrandCyan
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Circular Timer Indicator
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(190.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                strokeWidth = 10.dp
                            )
                            CircularProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.fillMaxSize(),
                                color = if (sessionState.isPaused) WarningAmber else BrandViolet,
                                strokeWidth = 10.dp
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = timerText,
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 42.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (sessionState.isPaused) "PAUSED" else "FOCUSING",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (sessionState.isPaused) WarningAmber else SuccessGreen
                                )
                            }
                        }

                        if (sessionState.goal.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Goal: ${sessionState.goal}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Session Action Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (sessionState.isPaused) viewModel.focusSentryManager.resumeSession()
                                    else viewModel.focusSentryManager.pauseSession()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("btn_focus_pause_resume"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sessionState.isPaused) BrandViolet else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Icon(
                                    imageVector = if (sessionState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null,
                                    tint = if (sessionState.isPaused) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (sessionState.isPaused) "Resume" else "Pause",
                                    color = if (sessionState.isPaused) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Button(
                                onClick = { viewModel.focusSentryManager.endSession(completed = true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("btn_focus_finish"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Complete", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(
                            onClick = { viewModel.focusSentryManager.launchVoiceVerification() }
                        ) {
                            Icon(imageVector = Icons.Default.Psychology, contentDescription = null, tint = BrandCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Take Voice Study Verification Challenge", color = BrandCyan)
                        }
                    } else {
                        // Setup New Session State
                        Text(
                            text = "START STUDY SESSION",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                            color = BrandCyan
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Duration Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(25, 45, 60).forEach { mins ->
                                val isSelected = selectedMinutes == mins
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) BrandViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedMinutes = mins },
                                    color = if (isSelected) BrandViolet.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "${mins}m",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) BrandViolet else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = when (mins) {
                                                25 -> "Pomodoro"
                                                45 -> "Standard"
                                                else -> "Deep Work"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Goal input
                        OutlinedTextField(
                            value = sessionGoal,
                            onValueChange = { sessionGoal = it },
                            label = { Text("Session Goal (Optional)") },
                            placeholder = { Text("e.g. Master Raft leader election steps") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("focus_session_goal_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val subject = subjects.firstOrNull { it.id == selectedSubjectId }
                                viewModel.focusSentryManager.startSession(
                                    subjectId = selectedSubjectId,
                                    subjectName = subject?.name ?: "General Focus",
                                    durationMinutes = selectedMinutes,
                                    goal = sessionGoal
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("btn_start_focus_session"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandViolet)
                        ) {
                            Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start Focus Session ($selectedMinutes min)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Focus Statistics Grid
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "FocusSentry Analytics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "Total Time",
                        value = "${totalFocusMinutes}m",
                        icon = Icons.Default.Timer,
                        accentColor = BrandViolet,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Completed",
                        value = completedSessions.toString(),
                        icon = Icons.Default.CheckCircle,
                        accentColor = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Distractions",
                        value = totalDistractions.toString(),
                        icon = Icons.Default.Warning,
                        accentColor = WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Distraction App Monitoring Section
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Distraction App Guard",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (hasUsageAccess) "Usage Access Active (Battery-friendly polling)" else "Requires Usage Access in Android Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasUsageAccess) SuccessGreen else WarningAmber
                        )
                    }

                    if (!hasUsageAccess) {
                        OutlinedButton(
                            onClick = { context.startActivity(viewModel.focusSentryManager.requestUsageAccessIntent()) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Grant Access", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        for (app in allConfiguredApps) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = app.isMonitored,
                                    onCheckedChange = { viewModel.toggleMonitoredApp(app) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = BrandViolet
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { showAddAppDialog = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Custom App")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Distraction Alert Dialog
    if (sessionState.showDistractionAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.focusSentryManager.dismissDistractionAlert() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Focus Reminder")
                }
            },
            text = {
                Column {
                    Text("You have an active focus session in '${sessionState.subjectName}'.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "FocusSentry detected an external app switch. Ready to return to your study flow or test your understanding with a quick study check?",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.focusSentryManager.dismissDistractionAlert()
                        viewModel.focusSentryManager.launchVoiceVerification()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandViolet)
                ) {
                    Text("Take Study Check")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.focusSentryManager.dismissDistractionAlert() }) {
                    Text("Return to Study")
                }
            }
        )
    }

    // Voice Study Verification Dialog
    if (sessionState.isVerificationActive) {
        AlertDialog(
            onDismissRequest = { viewModel.focusSentryManager.closeVerification() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Psychology, contentDescription = null, tint = BrandCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Voice Study Check")
                }
            },
            text = {
                Column {
                    Text(
                        text = sessionState.verificationQuestion,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = verificationAnswerInput,
                        onValueChange = { verificationAnswerInput = it },
                        placeholder = { Text("Speak or type your answer here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verification_answer_input"),
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp)
                    )

                    sessionState.verificationFeedback?.let { feedback ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (sessionState.verificationPassed == true) SuccessGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)
                            )
                        ) {
                            Text(
                                text = "Score: ${sessionState.verificationScore}/100\n$feedback",
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
                    onClick = { viewModel.focusSentryManager.submitVerificationAnswer(verificationAnswerInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandViolet)
                ) {
                    Text("Submit Answer")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.focusSentryManager.closeVerification() }) {
                    Text("Close")
                }
            }
        )
    }

    if (showAddAppDialog) {
        AlertDialog(
            onDismissRequest = { showAddAppDialog = false },
            title = { Text("Add Monitored App") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newAppName,
                        onValueChange = { newAppName = it },
                        label = { Text("App Name (e.g. Netflix)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newAppPkg,
                        onValueChange = { newAppPkg = it },
                        label = { Text("Package Name (e.g. com.netflix.mediaclient)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAppPkg.isNotBlank() && newAppName.isNotBlank()) {
                            viewModel.addCustomDistractionApp(newAppPkg.trim(), newAppName.trim())
                            newAppName = ""
                            newAppPkg = ""
                            showAddAppDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAppDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
