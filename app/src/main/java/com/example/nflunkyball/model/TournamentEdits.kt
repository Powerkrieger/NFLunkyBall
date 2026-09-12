package com.example.nflunkyball.model

import java.util.UUID

// Every organizer-side edit as a pure Tournament -> Tournament transform. OrganizerViewModel is
// a one-liner per edit (`repository.update { it.withX(...) }`), so the rules live here where a
// plain JVM test can exercise them without an Android Context.

/** "Anna, Ben" / "Anna & Ben" / "Anna" → the member names, trimmed and non-blank. */
fun parseMemberNames(input: String): List<String> =
    input.split(',', '&').map { it.trim() }.filter { it.isNotBlank() }

/** A fresh tournament in the GROUP_STAGE phase: each entry of [groupAssignments] (group name →
 *  team ids) becomes a group with its full round-robin schedule generated. */
fun newTournament(
    name: String,
    teams: List<Team>,
    groupAssignments: Map<String, List<String>>,
    squadSize: Int = 1
): Tournament {
    val groups = groupAssignments.map { (groupName, teamIds) ->
        val bareGroup = Group(id = UUID.randomUUID().toString(), name = groupName, teamIds = teamIds)
        bareGroup.copy(matches = bareGroup.generateRoundRobinMatches())
    }
    return Tournament(
        id = UUID.randomUUID().toString(),
        name = name,
        teams = teams,
        groups = groups,
        phase = TournamentPhase.GROUP_STAGE,
        squadSize = squadSize
    )
}

fun Tournament.withGroupMatchResult(groupId: String, matchId: String, result: MatchResult?): Tournament =
    copy(
        groups = groups.map { g ->
            if (g.id != groupId) g
            else g.copy(matches = g.matches.map { m -> if (m.id == matchId) m.copy(result = result) else m })
        }
    )

fun Tournament.withBracketMatchResult(matchId: String, result: MatchResult?): Tournament =
    copy(bracketMatches = bracketMatches.map { m -> if (m.id == matchId) m.copy(result = result) else m })

fun Tournament.withPhase(newPhase: TournamentPhase): Tournament = copy(phase = newPhase)

/**
 * Adds a new team to an in-progress group, generating matches against every team already in it —
 * existing results are untouched, only the new pairings are appended. [playerName] is one player
 * for singles; for a squad tournament it's the members separated by commas or "&" (the squad gets
 * the "Anna & Ben" auto-name). Returns `this` unchanged for a blank name.
 */
fun Tournament.withTeamAdded(groupId: String, playerName: String): Tournament {
    val members = parseMemberNames(playerName)
    if (members.isEmpty()) return this
    val newTeam = if (squadSize > 1 || members.size > 1) {
        Team(id = UUID.randomUUID().toString(), name = Team.autoName(members), members = members)
    } else {
        Team(id = UUID.randomUUID().toString(), name = members.single())
    }
    return copy(
        teams = teams + newTeam,
        groups = groups.map { g ->
            if (g.id != groupId) g
            else g.copy(
                teamIds = g.teamIds + newTeam.id,
                matches = g.matches + g.teamIds.map { existingTeamId ->
                    Match(id = UUID.randomUUID().toString(), teamAId = existingTeamId, teamBId = newTeam.id)
                }
            )
        }
    )
}

/** Starts a new, empty round-robin group mid-tournament — e.g. once enough late arrivals show up
 *  to field a second group. Add teams to it afterwards via [withTeamAdded]. Blank name → no-op. */
fun Tournament.withGroupAdded(groupName: String): Tournament {
    val name = groupName.trim()
    if (name.isBlank()) return this
    return copy(groups = groups + Group(id = UUID.randomUUID().toString(), name = name, teamIds = emptyList()))
}

fun Tournament.withTeamRenamed(teamId: String, newName: String): Tournament {
    val name = newName.trim()
    if (name.isBlank()) return this
    return copy(teams = teams.map { if (it.id == teamId) it.copy(name = name) else it })
}

fun Tournament.withGroupRenamed(groupId: String, newName: String): Tournament {
    val name = newName.trim()
    if (name.isBlank()) return this
    return copy(groups = groups.map { if (it.id == groupId) it.copy(name = name) else it })
}

/** True if [teamId] can be safely removed — only ever false once a match involving them actually
 *  has a recorded result, since dropping them past that point would corrupt standings/history
 *  rather than just tidying up an unplayed pairing. */
fun Tournament.canRemoveTeam(teamId: String): Boolean =
    (groups.flatMap { it.matches } + bracketMatches)
        .none { (it.teamAId == teamId || it.teamBId == teamId) && it.result != null }

/** No-ops once [canRemoveTeam] would say no — defense in depth, not just relying on the UI having
 *  disabled the action. Drops the team, its group membership, and any of its still-unplayed
 *  matches (group-stage or bracket). */
fun Tournament.withTeamRemoved(teamId: String): Tournament {
    if (!canRemoveTeam(teamId)) return this
    return copy(
        teams = teams.filterNot { it.id == teamId },
        groups = groups.map { g ->
            g.copy(
                teamIds = g.teamIds - teamId,
                matches = g.matches.filterNot { it.teamAId == teamId || it.teamBId == teamId }
            )
        },
        bracketMatches = bracketMatches.filterNot { it.teamAId == teamId || it.teamBId == teamId }
    )
}

/** Only an empty group (no teams) can be removed — one with teams in it would silently strand
 *  their matches/results, so removing those first (see [withTeamRemoved]) is required. */
fun Tournament.canRemoveGroup(groupId: String): Boolean =
    groups.find { it.id == groupId }?.teamIds?.isEmpty() == true

fun Tournament.withGroupRemoved(groupId: String): Tournament {
    if (!canRemoveGroup(groupId)) return this
    return copy(groups = groups.filterNot { it.id == groupId })
}

fun Tournament.withBracketMatchAdded(teamAId: String, teamBId: String, roundLabel: String): Tournament =
    copy(
        bracketMatches = bracketMatches + Match(
            id = UUID.randomUUID().toString(),
            teamAId = teamAId,
            teamBId = teamBId,
            roundLabel = roundLabel
        )
    )
