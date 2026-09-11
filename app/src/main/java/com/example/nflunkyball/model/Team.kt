package com.example.nflunkyball.model

import kotlinx.serialization.Serializable

/**
 * A squad competing as one unit through a tournament — it advances or drops out together, and
 * groups/standings/bracket only ever see its [id]. [members] are the player names, which is
 * what persists across tournaments (as backend competitors); the squad itself is only ever a
 * per-tournament grouping.
 *
 * [members] is empty for the classic singles format, where the player *is* the team and
 * [name] is their name — see [memberNames]. That keeps every pre-squad tournament (cached,
 * archived, or broadcast from an older organizer) decodable and meaning exactly what it did.
 */
@Serializable
data class Team(
    val id: String,
    val name: String,
    val members: List<String> = emptyList()
) {
    /** The players in this team, always at least one. */
    val memberNames: List<String> get() = members.ifEmpty { listOf(name) }

    companion object {
        /** The default squad display name: "Anna & Ben", in the order the members were added. */
        fun autoName(members: List<String>): String = members.joinToString(" & ")
    }
}
