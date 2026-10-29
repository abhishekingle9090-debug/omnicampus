package com.example.ui.screens.flashcards

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.FlashcardEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandViolet
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.OmniCampusViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    viewModel: OmniCampusViewModel,
    onNavigateBack: () -> Unit
) {
    val allFlashcards by viewModel.flashcards.collectAsState()
    val subjects by viewModel.subjects.collectAsState()

    var selectedSubjectId by remember { mutableStateOf<String?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isAnswerRevealed by remember { mutableStateOf(false) }
    var filterDueOnly by remember { mutableStateOf(false) }
    var isShuffled by remember { mutableStateOf(false) }
    var isSessionCompleted by remember { mutableStateOf(false) }
    var knownCount by remember { mutableIntStateOf(0) }
    var needReviewCount by remember { mutableIntStateOf(0) }

    val currentTime = System.currentTimeMillis()
    val baseCards = allFlashcards.filter { card ->
        val matchesSubject = selectedSubjectId == null || card.subjectId == selectedSubjectId
        val matchesDue = !filterDueOnly || card.nextReviewAt <= currentTime
        matchesSubject && matchesDue
    }

    val filteredCards = remember(baseCards, isShuffled) {
        if (isShuffled) baseCards.shuffled() else baseCards
    }

    val activeCard: FlashcardEntity? = if (filteredCards.isNotEmpty() && currentIndex in filteredCards.indices) {
        filteredCards[currentIndex]
    } else {
        null
    }

    val rotation by animateFloatAsState(
        targetValue = if (isAnswerRevealed) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "card_flip"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Revision Flashcards") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isShuffled = !isShuffled
                            currentIndex = 0
                            isAnswerRevealed = false
                            isSessionCompleted = false
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle Flashcards",
                            tint = if (isShuffled) BrandCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                .testTag("flashcards_screen")
        ) {
            // Subject Decks Filter
            LazyRow(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    DeckFilterChip(
                        label = "All (${allFlashcards.size})",
                        isSelected = selectedSubjectId == null && !filterDueOnly,
                        onClick = {
                            selectedSubjectId = null
                            filterDueOnly = false
                            currentIndex = 0
                            isAnswerRevealed = false
                        }
                    )
                }
                item {
                    DeckFilterChip(
                        label = "Due for Revision",
                        isSelected = filterDueOnly,
                        onClick = {
                            filterDueOnly = true
                            currentIndex = 0
                            isAnswerRevealed = false
                        }
                    )
                }
                items(subjects) { subject ->
                    val count = allFlashcards.count { it.subjectId == subject.id }
                    DeckFilterChip(
                        label = "${subject.code.ifBlank { subject.name }} ($count)",
                        isSelected = selectedSubjectId == subject.id && !filterDueOnly,
                        onClick = {
                            selectedSubjectId = subject.id
                            filterDueOnly = false
                            currentIndex = 0
                            isAnswerRevealed = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isSessionCompleted) {
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
                            .background(SuccessGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Session Complete!",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You reviewed all ${filteredCards.size} cards in this deck.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$knownCount",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = SuccessGreen
                                )
                                Text("Known", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$needReviewCount",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = ErrorRed
                                )
                                Text("Need Review", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            currentIndex = 0
                            isAnswerRevealed = false
                            isSessionCompleted = false
                            knownCount = 0
                            needReviewCount = 0
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandViolet)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restart Deck")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Return to Lecture / Home")
                    }
                }
            } else if (filteredCards.isEmpty() || activeCard == null) {
                EmptyStateView(
                    icon = Icons.Default.Psychology,
                    title = "No flashcards in this deck",
                    message = "Generate study flashcards from your lecture notes to start reviewing."
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Progress & Deck Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Card ${currentIndex + 1} of ${filteredCards.size}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = BrandViolet
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (activeCard.difficulty) {
                                "EASY" -> SuccessGreen.copy(alpha = 0.15f)
                                "HARD" -> ErrorRed.copy(alpha = 0.15f)
                                else -> WarningAmber.copy(alpha = 0.15f)
                            }
                        ) {
                            Text(
                                text = "${activeCard.type} • ${activeCard.difficulty}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = when (activeCard.difficulty) {
                                    "EASY" -> SuccessGreen
                                    "HARD" -> ErrorRed
                                    else -> WarningAmber
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val progress = (currentIndex + 1).toFloat() / filteredCards.size
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = BrandViolet,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3D Flip Flashcard
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .graphicsLayer {
                                rotationY = rotation
                                cameraDistance = 12f * density
                            }
                            .clickable { isAnswerRevealed = !isAnswerRevealed }
                            .testTag("flashcard_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (rotation <= 90f) {
                                // Question Side
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "QUESTION",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            letterSpacing = 1.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = BrandCyan
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = activeCard.question,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = 28.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Tap to reveal answer",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                // Answer Side (Flipped 180 degrees back to normal readability)
                                Column(
                                    modifier = Modifier.graphicsLayer { rotationY = 180f },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "ANSWER",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            letterSpacing = 1.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = SuccessGreen
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = activeCard.answer,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Normal,
                                            lineHeight = 24.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Grading Buttons (SM-2 Spaced Repetition)
                    if (isAnswerRevealed) {
                        Text(
                            text = "Rate your recall confidence:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.reviewFlashcard(activeCard, "HARD")
                                    needReviewCount++
                                    isAnswerRevealed = false
                                    if (currentIndex < filteredCards.size - 1) {
                                        currentIndex++
                                    } else {
                                        isSessionCompleted = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("rate_hard_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.85f))
                            ) {
                                Text("Hard", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    viewModel.reviewFlashcard(activeCard, "MEDIUM")
                                    knownCount++
                                    isAnswerRevealed = false
                                    if (currentIndex < filteredCards.size - 1) {
                                        currentIndex++
                                    } else {
                                        isSessionCompleted = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("rate_medium_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber.copy(alpha = 0.9f))
                            ) {
                                Text("Medium", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    viewModel.reviewFlashcard(activeCard, "EASY")
                                    knownCount++
                                    isAnswerRevealed = false
                                    if (currentIndex < filteredCards.size - 1) {
                                        currentIndex++
                                    } else {
                                        isSessionCompleted = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("rate_easy_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                            ) {
                                Text("Easy", color = Color.White)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { isAnswerRevealed = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_reveal_answer"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Flip, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reveal Answer")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Next / Prev Deck Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (currentIndex > 0) {
                                    currentIndex--
                                    isAnswerRevealed = false
                                }
                            },
                            enabled = currentIndex > 0,
                            modifier = Modifier.testTag("btn_prev_card")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
                        }

                        IconButton(
                            onClick = {
                                viewModel.deleteFlashcard(activeCard)
                                if (currentIndex > 0) currentIndex--
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Flashcard", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        IconButton(
                            onClick = {
                                if (currentIndex < filteredCards.size - 1) {
                                    currentIndex++
                                    isAnswerRevealed = false
                                }
                            },
                            enabled = currentIndex < filteredCards.size - 1,
                            modifier = Modifier.testTag("btn_next_card")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckFilterChip(
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
