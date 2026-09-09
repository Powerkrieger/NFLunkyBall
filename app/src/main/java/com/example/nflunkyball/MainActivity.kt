package com.example.nflunkyball

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.ui.HomeScreen
import com.example.nflunkyball.ui.organizer.BracketScreen
import com.example.nflunkyball.ui.organizer.GroupStageScreen
import com.example.nflunkyball.ui.organizer.HostingScreen
import com.example.nflunkyball.ui.organizer.LinkAccountScreen
import com.example.nflunkyball.ui.organizer.OrganizerViewModel
import com.example.nflunkyball.ui.organizer.SetupScreen
import com.example.nflunkyball.ui.shared.PermissionsGate
import com.example.nflunkyball.ui.theme.NFLunkyBallTheme
import com.example.nflunkyball.ui.viewer.HistoryScreen
import com.example.nflunkyball.ui.viewer.HistoryTournamentDetailScreen
import com.example.nflunkyball.ui.viewer.JoinScreen
import com.example.nflunkyball.ui.viewer.ViewerScoreboardScreen
import com.example.nflunkyball.ui.viewer.ViewerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Explicit light/dark bar style (rather than relying on enableEdgeToEdge()'s auto
        // detection) so status/nav bar icons reliably match our fixed theme colors instead of
        // ever landing on white-on-white. Content itself is edge-to-edge (required on API 35+
        // regardless), so it's padded away from the bars below via WindowInsets.safeDrawing.
        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val barStyle = if (isDarkTheme) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

        setContent {
            NFLunkyBallTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                        PermissionsGate {
                            NfLunkyBallApp()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NfLunkyBallApp() {
    val navController = rememberNavController()
    val organizerViewModel: OrganizerViewModel = viewModel()
    val viewerViewModel: ViewerViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onHost = {
                    // Resume an in-progress tournament (e.g. the app got killed mid-event)
                    // instead of dropping the organizer into a blank "New tournament" form and
                    // silently orphaning what's already on disk.
                    val destination = when {
                        organizerViewModel.organizerAccount == null -> "organizer/link_account"
                        organizerViewModel.tournament.value != null -> "organizer/hosting"
                        else -> "organizer/setup"
                    }
                    navController.navigate(destination)
                },
                onJoin = { navController.navigate("viewer/join") }
            )
        }
        composable("organizer/link_account") {
            LinkAccountScreen(
                viewModel = organizerViewModel,
                onDone = {
                    navController.navigate("organizer/setup") {
                        popUpTo("organizer/link_account") { inclusive = true }
                    }
                }
            )
        }
        composable("organizer/setup") {
            SetupScreen(viewModel = organizerViewModel) { name, teams, groups ->
                organizerViewModel.startTournament(name, teams, groups)
                navController.navigate("organizer/hosting")
            }
        }
        composable("organizer/hosting") {
            HostingScreen(
                viewModel = organizerViewModel,
                onContinue = {
                    // Resuming a tournament that already reached the bracket phase should land
                    // back on BracketScreen, not restart at GroupStageScreen.
                    val destination = if (organizerViewModel.tournament.value?.phase == TournamentPhase.BRACKET) {
                        "organizer/bracket"
                    } else {
                        "organizer/group_stage"
                    }
                    navController.navigate(destination)
                }
            )
        }
        composable("organizer/group_stage") {
            GroupStageScreen(
                viewModel = organizerViewModel,
                onAdvanceToBracket = {
                    organizerViewModel.advanceToBracket()
                    navController.navigate("organizer/bracket")
                }
            )
        }
        composable("organizer/bracket") {
            BracketScreen(
                viewModel = organizerViewModel,
                onFinish = {
                    organizerViewModel.finishTournament()
                    organizerViewModel.uploadToHistory()
                    organizerViewModel.clearTournament()
                    navController.popBackStack(route = "home", inclusive = false)
                }
            )
        }
        composable("viewer/join") {
            JoinScreen(viewModel = viewerViewModel, onJoined = { navController.navigate("viewer/scoreboard") })
        }
        composable("viewer/scoreboard") {
            ViewerScoreboardScreen(
                viewModel = viewerViewModel,
                onOpenHistory = { navController.navigate("viewer/history") }
            )
        }
        composable("viewer/history") {
            HistoryScreen(
                viewModel = viewerViewModel,
                onOpenTournament = { id -> navController.navigate("viewer/history/$id") }
            )
        }
        composable("viewer/history/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull()
            if (id != null) {
                HistoryTournamentDetailScreen(viewModel = viewerViewModel, tournamentId = id)
            }
        }
    }
}
