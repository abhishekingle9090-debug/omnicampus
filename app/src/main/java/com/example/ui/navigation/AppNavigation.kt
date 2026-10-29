package com.example.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.flashcards.FlashcardsScreen
import com.example.ui.screens.focus.FocusScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.lectures.LectureReviewScreen
import com.example.ui.screens.lectures.LecturesScreen
import com.example.ui.screens.lectures.NoteDetailScreen
import com.example.ui.screens.lectures.NoteGeneratorScreen
import com.example.ui.screens.lectures.TranscriptEditorScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.recording.RecordingScreen
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandViolet
import com.example.viewmodel.OmniCampusViewModel

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun AppNavigation(
    viewModel: OmniCampusViewModel,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val userProfile by viewModel.userProfile.collectAsState()
    val hasCompletedOnboarding = userProfile?.hasCompletedOnboarding ?: false

    val bottomNavItems = listOf(
        BottomNavItem(
            route = Screen.Home.route,
            label = "Home",
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            testTag = "bottom_nav_home"
        ),
        BottomNavItem(
            route = Screen.Lectures.route,
            label = "Lectures",
            selectedIcon = Icons.Filled.Description,
            unselectedIcon = Icons.Outlined.Description,
            testTag = "bottom_nav_lectures"
        ),
        BottomNavItem(
            route = Screen.Flashcards.route,
            label = "Flashcards",
            selectedIcon = Icons.Filled.Psychology,
            unselectedIcon = Icons.Outlined.Psychology,
            testTag = "bottom_nav_flashcards"
        ),
        BottomNavItem(
            route = Screen.Focus.route,
            label = "Focus",
            selectedIcon = Icons.Filled.Shield,
            unselectedIcon = Icons.Outlined.Shield,
            testTag = "bottom_nav_focus"
        ),
        BottomNavItem(
            route = Screen.Profile.route,
            label = "Profile",
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person,
            testTag = "bottom_nav_profile"
        )
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    bottomNavItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandViolet,
                                selectedTextColor = BrandViolet,
                                indicatorColor = BrandViolet.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    hasCompletedOnboarding = hasCompletedOnboarding,
                    onNavigateNext = { completed ->
                        if (completed) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Screen.Onboarding.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinishOnboarding = {
                        viewModel.completeOnboarding()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToRecord = { navController.navigate(Screen.RecordLecture.route) },
                    onNavigateToLectures = { navController.navigate(Screen.Lectures.route) },
                    onNavigateToNoteDetail = { noteId ->
                        navController.navigate(Screen.NoteDetail.createRoute(noteId))
                    },
                    onNavigateToTranscript = { lectureId ->
                        navController.navigate(Screen.LectureReview.createRoute(lectureId))
                    },
                    onNavigateToFlashcards = { navController.navigate(Screen.Flashcards.route) },
                    onNavigateToFocus = { navController.navigate(Screen.Focus.route) }
                )
            }

            composable(Screen.Lectures.route) {
                LecturesScreen(
                    viewModel = viewModel,
                    onNavigateToRecord = { navController.navigate(Screen.RecordLecture.route) },
                    onNavigateToTranscript = { lectureId ->
                        navController.navigate(Screen.LectureReview.createRoute(lectureId))
                    }
                )
            }

            composable(Screen.RecordLecture.route) {
                RecordingScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProcessing = { lectureId ->
                        navController.navigate(Screen.LectureReview.createRoute(lectureId)) {
                            popUpTo(Screen.RecordLecture.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.LectureReview.route,
                arguments = listOf(navArgument("lectureId") { type = NavType.StringType })
            ) { backStackEntry ->
                val lectureId = backStackEntry.arguments?.getString("lectureId") ?: ""
                LectureReviewScreen(
                    lectureId = lectureId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onNavigateToTranscript = { id ->
                        navController.navigate(Screen.TranscriptEditor.createRoute(id))
                    },
                    onNavigateToNoteGen = { id ->
                        navController.navigate(Screen.NoteGenerator.createRoute(id))
                    },
                    onNavigateToFlashcards = {
                        navController.navigate(Screen.Flashcards.route)
                    }
                )
            }

            composable(
                route = Screen.TranscriptEditor.route,
                arguments = listOf(navArgument("lectureId") { type = NavType.StringType })
            ) { backStackEntry ->
                val lectureId = backStackEntry.arguments?.getString("lectureId") ?: ""
                TranscriptEditorScreen(
                    lectureId = lectureId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNoteGen = { id ->
                        navController.navigate(Screen.NoteGenerator.createRoute(id))
                    }
                )
            }

            composable(
                route = Screen.NoteGenerator.route,
                arguments = listOf(navArgument("lectureId") { type = NavType.StringType })
            ) { backStackEntry ->
                val lectureId = backStackEntry.arguments?.getString("lectureId") ?: ""
                NoteGeneratorScreen(
                    lectureId = lectureId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNoteDetail = { noteId ->
                        navController.navigate(Screen.NoteDetail.createRoute(noteId)) {
                            popUpTo(Screen.NoteGenerator.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.NoteDetail.route,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
                NoteDetailScreen(
                    noteId = noteId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Flashcards.route) {
                FlashcardsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Focus.route) {
                FocusScreen(viewModel = viewModel)
            }

            composable(Screen.Profile.route) {
                ProfileScreen(viewModel = viewModel)
            }
        }
    }
}
