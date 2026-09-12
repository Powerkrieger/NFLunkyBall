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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.nflunkyball.ui.organizer.LoginScreen
import com.example.nflunkyball.ui.organizer.ManageGroupsScreen
import com.example.nflunkyball.ui.organizer.ManagePlayersScreen
import com.example.nflunkyball.ui.organizer.OrganizerViewModel
import com.example.nflunkyball.ui.organizer.SetupScreen
import com.example.nflunkyball.ui.organizer.TournamentSettingsScreen
import com.example.nflunkyball.ui.shared.PermissionsGate
import com.example.nflunkyball.ui.theme.NFLunkyBallTheme
import com.example.nflunkyball.ui.viewer.HistoryScreen
import com.example.nflunkyball.ui.viewer.HistoryTournamentDetailScreen
import com.example.nflunkyball.ui.viewer.MatchDetailScreen
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

/** Every destination in the single nav graph below. Parametrised routes come in pairs: the
 *  pattern registered with `composable(...)` and a builder producing the concrete path. */
private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val RULEBOOK = "rulebook"
    const val SETTINGS = "settings"
    const val LINK_ACCOUNT = "organizer/link_account"
    const val SETUP = "organizer/setup"
    const val HOSTING = "organizer/hosting"
    const val GROUP_STAGE = "organizer/group_stage"
    const val BRACKET = "organizer/bracket"
    const val TOURNAMENT_SETTINGS = "organizer/tournament_settings"
    const val MANAGE_PLAYERS = "organizer/manage_players"
    const val MANAGE_GROUPS = "organizer/manage_groups"
    const val JOIN = "viewer/join"
    const val SCOREBOARD = "viewer/scoreboard"
    const val HISTORY = "viewer/history"

    const val ARG_ID = "id"
    const val SAVED_TOURNAMENT = "viewer/history/{$ARG_ID}"
    const val SERVER_TOURNAMENT = "viewer/tournament/{$ARG_ID}"
    const val PLAYER = "viewer/player/{$ARG_ID}"
    const val MATCH = "viewer/match/{$ARG_ID}"

    fun savedTournament(savedId: String) = "viewer/history/$savedId"
    fun serverTournament(serverId: Int) = "viewer/tournament/$serverId"
    fun player(competitorId: Int) = "viewer/player/$competitorId"
    fun match(matchId: Int) = "viewer/match/$matchId"
}

@Composable
private fun NfLunkyBallApp() {
    val navController = rememberNavController()
    val factory = (LocalContext.current.applicationContext as NfLunkyBallApplication).container.viewModelFactory
    val organizerViewModel: OrganizerViewModel = viewModel(factory = factory)
    val viewerViewModel: ViewerViewModel = viewModel(factory = factory)

    // Evaluated once at composition (a device's link state doesn't change without an explicit
    // navigation afterward), so an already-linked device — organizer or viewer, however that was
    // obtained — never sees a redirect flash through the login screen. The ViewModel already
    // loaded both credentials from the same store at construction.
    val isAlreadyLinked = remember {
        organizerViewModel.organizerAccount != null || organizerViewModel.readPassword != null
    }

    NavHost(navController = navController, startDestination = if (isAlreadyLinked) Routes.HOME else Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = organizerViewModel,
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onJoinTournament = {
                    // Rewrite the back stack to Home first (same two-call shape used elsewhere in
                    // this graph, e.g. SetupScreen's onStart) so backing out of Join/Scoreboard
                    // lands on Home instead of exiting the app or bouncing back to Login.
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                    navController.navigate(Routes.JOIN)
                }
            )
        }
        composable(Routes.HOME) {
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
                onHost = { navController.navigate(Routes.SETUP) },
                onJoin = { navController.navigate(Routes.JOIN) },
                onMyTournaments = { navController.navigate(Routes.HISTORY) },
                onRulebook = { navController.navigate(Routes.RULEBOOK) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.RULEBOOK) {
            RulebookScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                organizerViewModel = organizerViewModel,
                onBack = { navController.popBackStack() },
                onLinkAccount = { navController.navigate(Routes.LINK_ACCOUNT) }
            )
        }
        composable(Routes.LINK_ACCOUNT) {
            LoginScreen(
                viewModel = organizerViewModel,
                onDone = {
                    // Reached from several places now (Settings, HostingScreen, tournament
                    // settings, the finish-without-linking warning) — popping back returns to
                    // whichever one it was, rather than assuming a specific next screen. If a
                    // tournament is active, (re)kick off sync now that credentials exist; a
                    // fresh link from Settings with nothing hosted yet is a no-op here.
                    when {
                        organizerViewModel.hasPendingUpload -> organizerViewModel.retryUpload()
                        organizerViewModel.tournament.value != null -> organizerViewModel.startHosting()
                    }
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(
                viewModel = organizerViewModel,
                onNavigateToMyTournaments = {
                    navController.navigate(Routes.HISTORY) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            ) { name, teams, groups, squadSize ->
                organizerViewModel.startTournament(name, teams, groups, squadSize)
                // Go straight to the QR/room-code screen so it's immediately shareable — but
                // rewrite the back stack first so back-from-there lands on My Tournaments (the
                // one place an in-progress tournament is managed from now on), not back into
                // this creation flow. Two navigate() calls on purpose: the first swaps out
                // organizer/setup for viewer/history, the second then pushes hosting on top of
                // that, same shape as reaching hosting via My Tournaments' "Hosting · ..." row.
                navController.navigate(Routes.HISTORY) {
                    popUpTo(Routes.SETUP) { inclusive = true }
                }
                navController.navigate(Routes.HOSTING)
            }
        }
        composable(Routes.HOSTING) {
            HostingScreen(
                viewModel = organizerViewModel,
                onContinue = {
                    // Resuming a tournament that already reached the bracket phase should land
                    // back on BracketScreen, not restart at GroupStageScreen.
                    val destination = if (organizerViewModel.tournament.value?.phase == TournamentPhase.BRACKET) {
                        Routes.BRACKET
                    } else {
                        Routes.GROUP_STAGE
                    }
                    navController.navigate(destination)
                },
                onLinkAccount = { navController.navigate(Routes.LINK_ACCOUNT) }
            )
        }
        composable(Routes.GROUP_STAGE) {
            GroupStageScreen(
                viewModel = organizerViewModel,
                onAdvanceToBracket = {
                    organizerViewModel.advanceToBracket()
                    navController.navigate(Routes.BRACKET)
                },
                onOpenSettings = { navController.navigate(Routes.TOURNAMENT_SETTINGS) }
            )
        }
        composable(Routes.BRACKET) {
            BracketScreen(
                viewModel = organizerViewModel,
                onFinish = { finishInfo ->
                    // Upload runs in the background; if it fails the tournament stays on the
                    // device and My tournaments shows the error with retry/discard.
                    organizerViewModel.finishAndUpload(finishInfo)
                    navController.popBackStack(route = Routes.HOME, inclusive = false)
                },
                onOpenSettings = { navController.navigate(Routes.TOURNAMENT_SETTINGS) },
                onLinkAccount = { navController.navigate(Routes.LINK_ACCOUNT) }
            )
        }
        composable(Routes.TOURNAMENT_SETTINGS) {
            TournamentSettingsScreen(
                viewModel = organizerViewModel,
                onBack = { navController.popBackStack() },
                onAbandoned = { navController.popBackStack(route = Routes.HOME, inclusive = false) },
                onLinkAccount = { navController.navigate(Routes.LINK_ACCOUNT) },
                onManagePlayers = { navController.navigate(Routes.MANAGE_PLAYERS) },
                onManageGroups = { navController.navigate(Routes.MANAGE_GROUPS) }
            )
        }
        composable(Routes.MANAGE_PLAYERS) {
            ManagePlayersScreen(viewModel = organizerViewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.MANAGE_GROUPS) {
            ManageGroupsScreen(viewModel = organizerViewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.JOIN) {
            JoinScreen(viewModel = viewerViewModel, onJoined = { navController.navigate(Routes.SCOREBOARD) })
        }
        composable(Routes.SCOREBOARD) {
            ViewerScoreboardScreen(
                viewModel = viewerViewModel,
                onOpenHistory = { navController.navigate(Routes.HISTORY) }
            )
        }
        composable(Routes.HISTORY) {
            val hostedTournament by organizerViewModel.tournament.collectAsState()
            HistoryScreen(
                viewModel = viewerViewModel,
                hostedTournament = hostedTournament,
                hostedUploadStatus = organizerViewModel.uploadStatus,
                onRetryUpload = { organizerViewModel.retryUpload() },
                onDiscardHosted = { organizerViewModel.discardFinishedTournament() },
                onOpenTournament = { id -> navController.navigate(Routes.savedTournament(id)) },
                onReconnected = {
                    navController.navigate(Routes.SCOREBOARD) {
                        popUpTo(Routes.HISTORY) { inclusive = true }
                    }
                },
                onResumeHosting = { navController.navigate(Routes.HOSTING) },
                onOpenPlayer = { id -> navController.navigate(Routes.player(id)) }
            )
        }
        composable(Routes.SAVED_TOURNAMENT) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(Routes.ARG_ID)
            if (id != null) {
                HistoryTournamentDetailScreen(
                    viewModel = viewerViewModel,
                    savedId = id,
                    serverId = null,
                    onOpenMatch = { matchId -> navController.navigate(Routes.match(matchId)) },
                    onOpenPlayer = { playerId -> navController.navigate(Routes.player(playerId)) }
                )
            }
        }
        // Same screen as above, addressed by backend id — how a player's Elo history or a match
        // page links to a tournament the viewer never saved locally.
        composable(Routes.SERVER_TOURNAMENT) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(Routes.ARG_ID)?.toIntOrNull()
            if (id != null) {
                HistoryTournamentDetailScreen(
                    viewModel = viewerViewModel,
                    savedId = null,
                    serverId = id,
                    onOpenMatch = { matchId -> navController.navigate(Routes.match(matchId)) },
                    onOpenPlayer = { playerId -> navController.navigate(Routes.player(playerId)) }
                )
            }
        }
        composable(Routes.PLAYER) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(Routes.ARG_ID)?.toIntOrNull()
            if (id != null) {
                PlayerStatsScreen(
                    viewModel = viewerViewModel,
                    competitorId = id,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { playerId -> navController.navigate(Routes.player(playerId)) },
                    onOpenMatch = { matchId -> navController.navigate(Routes.match(matchId)) },
                    onOpenTournament = { tournamentId -> navController.navigate(Routes.serverTournament(tournamentId)) }
                )
            }
        }
        composable(Routes.MATCH) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(Routes.ARG_ID)?.toIntOrNull()
            if (id != null) {
                MatchDetailScreen(
                    viewModel = viewerViewModel,
                    matchId = id,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { playerId -> navController.navigate(Routes.player(playerId)) },
                    onOpenTournament = { tournamentId -> navController.navigate(Routes.serverTournament(tournamentId)) }
                )
            }
        }
    }
}
