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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nflunkyball.model.TournamentPhase
import com.example.nflunkyball.ui.HomeScreen
import com.example.nflunkyball.ui.RulebookScreen
import com.example.nflunkyball.ui.SettingsScreen
import com.example.nflunkyball.ui.organizer.BracketScreen
import com.example.nflunkyball.ui.organizer.GroupStageScreen
import com.example.nflunkyball.ui.organizer.HostingScreen
import com.example.nflunkyball.ui.organizer.LinkAccountScreen
import com.example.nflunkyball.ui.organizer.ManageGroupsScreen
import com.example.nflunkyball.ui.organizer.ManagePlayersScreen
import com.example.nflunkyball.ui.organizer.OrganizerViewModel
import com.example.nflunkyball.ui.organizer.SetupScreen
import com.example.nflunkyball.ui.organizer.TournamentSettingsScreen
import com.example.nflunkyball.ui.shared.PermissionsGate
import com.example.nflunkyball.ui.theme.NFLunkyBallTheme
import com.example.nflunkyball.ui.viewer.HistoryScreen
import com.example.nflunkyball.ui.viewer.HistoryTournamentDetailScreen
import com.example.nflunkyball.ui.viewer.JoinScreen
import com.example.nflunkyball.ui.viewer.PlayerStatsScreen
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
            val hostedTournament by organizerViewModel.tournament.collectAsState()
            val watchedTournament by viewerViewModel.tournament.collectAsState()
            HomeScreen(
                // Highlights "My tournaments" once there's actually something there — a
                // tournament in progress (either role) or a previously linked/joined account
                // (history/leaderboard viewing) — and Host/Join otherwise, on an empty home
                // screen where starting something new is the more useful default.
                hasActiveOrLinkedSession = hostedTournament != null ||
                    watchedTournament != null ||
                    viewerViewModel.historyAvailable(),
                // Always starts a fresh tournament — never resumes one. An in-progress
                // tournament is no longer reachable through this button at all; it's managed
                // exclusively via "My tournaments" from here on (SetupScreen itself guards
                // against clobbering one that's already active). An account is also not a
                // prerequisite for hosting — see SettingsScreen's "Organizer account" section
                // for linking independent of any tournament; unlinked hosting just means no
                // server sync/history/known-players until one's linked.
                onHost = { navController.navigate("organizer/setup") },
                onJoin = { navController.navigate("viewer/join") },
                onMyTournaments = { navController.navigate("viewer/history") },
                onRulebook = { navController.navigate("rulebook") },
                onSettings = { navController.navigate("settings") }
            )
        }
        composable("rulebook") {
            RulebookScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                organizerViewModel = organizerViewModel,
                onBack = { navController.popBackStack() },
                onLinkAccount = { navController.navigate("organizer/link_account") }
            )
        }
        composable("organizer/link_account") {
            LinkAccountScreen(
                viewModel = organizerViewModel,
                onDone = {
                    // Reached from several places now (Settings, HostingScreen, tournament
                    // settings, the finish-without-linking warning) — popping back returns to
                    // whichever one it was, rather than assuming a specific next screen. If a
                    // tournament is active, (re)kick off sync now that credentials exist; a
                    // fresh link from Settings with nothing hosted yet is a no-op here.
                    if (organizerViewModel.tournament.value != null) organizerViewModel.startHosting()
                    navController.popBackStack()
                }
            )
        }
        composable("organizer/setup") {
            SetupScreen(
                viewModel = organizerViewModel,
                onNavigateToMyTournaments = {
                    navController.navigate("viewer/history") {
                        popUpTo("organizer/setup") { inclusive = true }
                    }
                }
            ) { name, teams, groups ->
                organizerViewModel.startTournament(name, teams, groups)
                // Go straight to the QR/room-code screen so it's immediately shareable — but
                // rewrite the back stack first so back-from-there lands on My Tournaments (the
                // one place an in-progress tournament is managed from now on), not back into
                // this creation flow. Two navigate() calls on purpose: the first swaps out
                // organizer/setup for viewer/history, the second then pushes hosting on top of
                // that, same shape as reaching hosting via My Tournaments' "Hosting · ..." row.
                navController.navigate("viewer/history") {
                    popUpTo("organizer/setup") { inclusive = true }
                }
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
                },
                onLinkAccount = { navController.navigate("organizer/link_account") }
            )
        }
        composable("organizer/group_stage") {
            GroupStageScreen(
                viewModel = organizerViewModel,
                onAdvanceToBracket = {
                    organizerViewModel.advanceToBracket()
                    navController.navigate("organizer/bracket")
                },
                onOpenSettings = { navController.navigate("organizer/tournament_settings") }
            )
        }
        composable("organizer/bracket") {
            BracketScreen(
                viewModel = organizerViewModel,
                onFinish = { finishInfo ->
                    organizerViewModel.finishTournament()
                    organizerViewModel.uploadToHistory(finishInfo)
                    organizerViewModel.clearTournament()
                    navController.popBackStack(route = "home", inclusive = false)
                },
                onOpenSettings = { navController.navigate("organizer/tournament_settings") },
                onLinkAccount = { navController.navigate("organizer/link_account") }
            )
        }
        composable("organizer/tournament_settings") {
            TournamentSettingsScreen(
                viewModel = organizerViewModel,
                onBack = { navController.popBackStack() },
                onAbandoned = { navController.popBackStack(route = "home", inclusive = false) },
                onLinkAccount = { navController.navigate("organizer/link_account") },
                onManagePlayers = { navController.navigate("organizer/manage_players") },
                onManageGroups = { navController.navigate("organizer/manage_groups") }
            )
        }
        composable("organizer/manage_players") {
            ManagePlayersScreen(viewModel = organizerViewModel, onBack = { navController.popBackStack() })
        }
        composable("organizer/manage_groups") {
            ManageGroupsScreen(viewModel = organizerViewModel, onBack = { navController.popBackStack() })
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
                organizerViewModel = organizerViewModel,
                onOpenTournament = { id -> navController.navigate("viewer/history/$id") },
                onReconnected = {
                    navController.navigate("viewer/scoreboard") {
                        popUpTo("viewer/history") { inclusive = true }
                    }
                },
                onResumeHosting = { navController.navigate("organizer/hosting") },
                onOpenPlayer = { id -> navController.navigate("viewer/player/$id") }
            )
        }
        composable("viewer/history/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            if (id != null) {
                HistoryTournamentDetailScreen(viewModel = viewerViewModel, savedId = id)
            }
        }
        composable("viewer/player/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull()
            if (id != null) {
                PlayerStatsScreen(viewModel = viewerViewModel, competitorId = id, onBack = { navController.popBackStack() })
            }
        }
    }
}
