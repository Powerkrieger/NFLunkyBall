package com.example.nflunkyball.model

import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val id: String,
    val name: String
)
