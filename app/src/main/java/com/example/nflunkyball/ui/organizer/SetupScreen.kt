package com.example.nflunkyball.ui.organizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.example.nflunkyball.model.ELO_STARTING_RATING
import com.example.nflunkyball.model.PlayerSeed
import com.example.nflunkyball.model.Team
import com.example.nflunkyball.model.assignGroupsBySeeding
import com.example.nflunkyball.model.assignGroupsRandomly
import com.example.nflunkyball.ui.theme.Spacing
import java.util.Locale
import java.util.UUID

/** Squad sizes the organizer can pick from; 1 is the classic singles format. */
private val SQUAD_SIZES = listOf(1, 2, 3, 4)

@Composable
fun SetupScreen(
    viewModel: OrganizerViewModel,
    onNavigateToMyTournaments: () -> Unit,
    onStart: (name: String, teams: List<Team>, groupAssignments: Map<String, List<String>>, squadSize: Int) -> Unit
) {
    // Only one tournament fits in TournamentRepository's single slot at a time — "Host a
    // tournament" always lands here now (see MainActivity), so this is the one place that has
    // to actively guard against silently clobbering whatever's already in progress, rather than
    // relying on the caller to have checked first.
    val activeTournament by viewModel.tournament.collectAsState()
    val blocking = activeTournament
    if (blocking != null) {
        ActiveTournamentGuard(name = blocking.name, onNavigateToMyTournaments = onNavigateToMyTournaments)
        return
    }

    LaunchedEffect(Unit) { viewModel.loadKnownCompetitors() }

    val focusManager = LocalFocusManager.current
    var tournamentName by remember { mutableStateOf("") }
    var playerNameInput by remember { mutableStateOf("") }
    var groupNameInput by remember { mutableStateOf("") }
    var squadSize by remember { mutableIntStateOf(1) }
    val teams = remember { mutableStateListOf<Team>() }
    // Players picked for the squad currently being assembled (squad tournaments only).
    val pendingMembers = remember { mutableStateListOf<String>() }
    val groupNames = remember { mutableStateListOf<String>() }
    val assignments = remember { mutableStateMapOf<String, String>() } // teamId -> groupName

    val takenNames = (teams.flatMap { it.memberNames } + pendingMembers).map { it.lowercase() }.toSet()

    /** Singles: one player is one team. Squads: collect into [pendingMembers] until full. */
    fun addPlayer(rawName: String) {
        val name = rawName.trim()
        if (name.isBlank() || name.lowercase() in takenNames) return
        if (squadSize == 1) {
            teams.add(Team(id = UUID.randomUUID().toString(), name = name))
        } else {
            pendingMembers.add(name)
            if (pendingMembers.size == squadSize) {
                val members = pendingMembers.toList()
                teams.add(Team(id = UUID.randomUUID().toString(), name = Team.autoName(members), members = members))
                pendingMembers.clear()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.md)
            .verticalScroll(rememberScrollState())
    ) {
        Text("New tournament", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = tournamentName,
            onValueChange = { tournamentName = it },
            label = { Text("Tournament name") },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
        )

        Text("Players per team", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.lg))
        Row(Modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SQUAD_SIZES.forEach { size ->
                FilterChip(
                    selected = squadSize == size,
                    // Changing the format mid-setup would leave half-built squads behind, so it's
                    // locked once the first team exists.
                    enabled = teams.isEmpty() && pendingMembers.isEmpty(),
                    onClick = { squadSize = size },
                    label = { Text(if (size == 1) "Singles" else "$size") }
                )
            }
        }

        // Groups first — teams get assigned to a group as they're added below, so having the
        // groups already exist here means that dropdown is never empty.
        Text("Groups", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = groupNameInput,
                onValueChange = { groupNameInput = it },
                label = { Text("Group name") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val trimmed = groupNameInput.trim()
                    if (trimmed.isNotBlank() && trimmed !in groupNames) {
                        groupNames.add(trimmed)
                        groupNameInput = ""
                    }
                },
                modifier = Modifier.padding(start = Spacing.sm)
            ) { Text("Add") }
        }
        groupNames.forEach { name -> Text("• $name", Modifier.padding(vertical = 2.dp)) }

        Text(
            if (squadSize == 1) "Players" else "Teams",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.lg)
        )
        if (squadSize > 1) {
            Text(
                if (pendingMembers.isEmpty()) "Pick $squadSize players to form a team"
                else "${pendingMembers.size} of $squadSize picked — ${squadSize - pendingMembers.size} more",
                style = MaterialTheme.typography.bodySmall
            )
            if (pendingMembers.isNotEmpty()) {
                LazyRow(Modifier.padding(top = Spacing.xs)) {
                    items(pendingMembers.toList()) { member ->
                        InputChip(
                            selected = false,
                            onClick = { pendingMembers.remove(member) },
                            label = { Text(member) },
                            trailingIcon = { Text("✕") },
                            modifier = Modifier.padding(end = Spacing.sm)
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = playerNameInput,
                onValueChange = { playerNameInput = it },
                label = { Text("Player name") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { addPlayer(playerNameInput); playerNameInput = "" },
                modifier = Modifier.padding(start = Spacing.sm)
            ) { Text("Add") }
        }

        // Already sorted by Elo desc (see OrganizerViewModel.loadKnownCompetitors).
        val suggestions = viewModel.knownCompetitors.filter { it.name.lowercase() !in takenNames }
        if (suggestions.isNotEmpty()) {
            Text(
                "Known players",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = Spacing.sm)
            )
            LazyRow(Modifier.padding(top = Spacing.xs)) {
                items(suggestions) { competitor ->
                    SuggestionChip(
                        onClick = { addPlayer(competitor.name) },
                        label = { Text("${competitor.name} (${String.format(Locale.US, "%.1f", competitor.elo)})") },
                        modifier = Modifier.padding(end = Spacing.sm)
                    )
                }
            }
        }

        if (groupNames.size >= 2 && teams.isNotEmpty()) {
            Row(Modifier.padding(top = Spacing.sm)) {
                TextButton(
                    onClick = {
                        val eloByName = viewModel.knownCompetitors.associateBy { it.name.lowercase() }
                        // A squad seeds by its members' mean Elo — the same side rating the
                        // backend uses to rate a team match.
                        val seeds = teams.map { team ->
                            PlayerSeed(
                                team.id,
                                team.memberNames.map { eloByName[it.lowercase()]?.elo ?: ELO_STARTING_RATING }.average()
                            )
                        }
                        val assigned = assignGroupsBySeeding(seeds, groupNames)
                        assigned.forEach { (groupName, teamIds) ->
                            teamIds.forEach { teamId -> assignments[teamId] = groupName }
                        }
                    }
                ) { Text("Auto-assign to groups") }
                TextButton(
                    onClick = {
                        val assigned = assignGroupsRandomly(teams.map { it.id }, groupNames)
                        assigned.forEach { (groupName, teamIds) ->
                            teamIds.forEach { teamId -> assignments[teamId] = groupName }
                        }
                    }
                ) { Text("Random groups") }
            }
        }

        teams.forEach { team ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(team.name, Modifier.weight(1f))
                var expanded by remember { mutableStateOf(false) }
                TextButton(onClick = {
                    // Drop focus (and with it, the keyboard) before opening the dropdown —
                    // otherwise the keyboard staying up shrinks the visible list and the screen
                    // jumps around as it tries to scroll the open menu into view.
                    focusManager.clearFocus()
                    expanded = true
                }) {
                    Text(assignments[team.id] ?: "Assign group")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    groupNames.forEach { groupName ->
                        DropdownMenuItem(
                            text = { Text(groupName) },
                            onClick = {
                                focusManager.clearFocus()
                                assignments[team.id] = groupName
                                expanded = false
                            }
                        )
                    }
                }
                TextButton(onClick = { teams.remove(team); assignments.remove(team.id) }) {
                    Text("✕")
                }
            }
        }

        val allAssigned = teams.isNotEmpty() && teams.all { assignments.containsKey(it.id) }
        Button(
            onClick = {
                val grouped = groupNames.associateWith { groupName ->
                    teams.filter { assignments[it.id] == groupName }.map { it.id }
                }
                onStart(tournamentName.ifBlank { "Flunkyball Tournament" }, teams.toList(), grouped, squadSize)
            },
            enabled = tournamentName.isNotBlank() && allAssigned && pendingMembers.isEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.lg)
        ) { Text("Start Group Stage") }
    }
}

@Composable
private fun ActiveTournamentGuard(name: String, onNavigateToMyTournaments: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("You already have an active tournament", style = MaterialTheme.typography.headlineSmall)
        Text(
            "\"$name\" is still in progress on this device. Finish or abandon it before " +
                "starting another one.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.sm)
        )
        Button(
            onClick = onNavigateToMyTournaments,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)
        ) { Text("Go to My tournaments") }
    }
}
