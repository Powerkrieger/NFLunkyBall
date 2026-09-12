package com.example.nflunkyball.model

import kotlinx.serialization.Serializable

/** Each player can be drinking something different, so this is never tied to who won.
 *  [teamA]/[teamB] are the singles-era per-side fields (still what the current result dialog
 *  records); [byPlayer] is the per-player record squads need and takes precedence per player
 *  when present.
 *
 *  Deliberately NOT part of [MatchResult]/[Tournament] — see
 *  [com.example.nflunkyball.persistence.MatchDrinkStore] for why it's kept out of anything that
 *  gets broadcast or live-synced. */
@Serializable
data class MatchDrinks(
    val teamA: String? = null,
    val teamB: String? = null,
    val byPlayer: Map<String, String> = emptyMap()
)
