package com.example.nflunkyball.model

import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val id: String,
    val name: String,
    val teamIds: List<String>,
    val matches: List<Match> = emptyList()
)
