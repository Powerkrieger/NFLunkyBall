package com.example.nflunkyball

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
        enableEdgeToEdge()
        setContent {
            NFLunkyBallTheme {
                PermissionsGate {
                    NfLunkyBallApp()
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
                onHost = { navController.navigate("organizer/setup") },
                onJoin = { navController.navigate("viewer/join") }
            )
        }
        composable("organizer/setup") {
            SetupScreen { name, teams, groups ->
                organizerViewModel.startTournament(name, teams, groups)
                navController.navigate("organizer/hosting")
            }
        }
        composable("organizer/hosting") {
            HostingScreen(
                viewModel = organizerViewModel,
                onLinkAccount = { navController.navigate("organizer/link_account") },
                onContinue = { navController.navigate("organizer/group_stage") }
            )
        }
        composable("organizer/link_account") {
            LinkAccountScreen(viewModel = organizerViewModel, onDone = { navController.popBackStack() })
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
                    if (organizerViewModel.organizerAccount != null) {
                        organizerViewModel.uploadToHistory()
                    }
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
