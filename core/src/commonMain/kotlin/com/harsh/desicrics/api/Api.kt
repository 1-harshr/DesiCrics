package com.harsh.desicrics.api

import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import kotlinx.serialization.Serializable

// Request and response bodies shared by the Ktor server and the app's API client.
//
//   GET    /me                              → UserProfile (404 until onboarding)
//   PUT    /me                              UpdateProfileRequest → UserProfile
//   GET    /matches?limit=30                → List<Match>
//   POST   /matches                         CreateMatchRequest → MatchDetails
//   GET    /matches/{id}                    → MatchDetails
//   POST   /matches/{id}/deliveries         Delivery → MatchDetails
//   DELETE /matches/{id}/deliveries/last    → MatchDetails

@Serializable
data class UpdateProfileRequest(
    val name: String,
    val phone: String,
    val city: String = "",
    val role: PlayerRole = PlayerRole.BATTER,
    val battingStyle: BattingStyle = BattingStyle.RIGHT_HAND,
    val bowlingStyle: BowlingStyle = BowlingStyle.NONE,
    val photoUrl: String? = null,
) {
    /** Checked on the device for instant feedback and again on the server. */
    fun validate(): ProfileError? = when {
        name.isBlank() -> ProfileError.NAME_MISSING
        name.length > 100 -> ProfileError.NAME_TOO_LONG
        phone.count { it.isDigit() } !in 10..13 -> ProfileError.PHONE_INVALID
        city.length > 100 -> ProfileError.CITY_TOO_LONG
        else -> null
    }

    /** English message for API error responses; the app shows its own localised text for [validate]. */
    fun validationError(): String? = validate()?.message
}

enum class ProfileError(val message: String) {
    NAME_MISSING("Please enter your name"),
    NAME_TOO_LONG("That name is too long"),
    PHONE_INVALID("Please enter a valid mobile number"),
    CITY_TOO_LONG("That city name is too long"),
}

@Serializable
data class CreateMatchRequest(
    val teamAName: String,
    val teamAPlayers: List<String>,
    val teamBName: String,
    val teamBPlayers: List<String>,
    val oversPerInnings: Int,
    val tossWinner: TeamSide,
    val tossDecision: TossDecision,
) {
    fun validationError(): String? = when {
        teamAName.isBlank() || teamBName.isBlank() -> "Give both teams a name"
        teamAName.trim().equals(teamBName.trim(), ignoreCase = true) -> "Teams need different names"
        teamAName.length > 60 || teamBName.length > 60 -> "Team names must be 60 characters or fewer"
        teamAPlayers.size < 2 || teamBPlayers.size < 2 -> "Each team needs at least 2 players"
        teamAPlayers.size > 20 || teamBPlayers.size > 20 -> "Teams can have at most 20 players"
        (teamAPlayers + teamBPlayers).any { it.isBlank() || it.length > 60 } -> "Player names must be 1–60 characters"
        oversPerInnings !in 1..50 -> "Overs must be between 1 and 50"
        else -> null
    }
}

/** A match with every ball bowled so far, which is everything a scorecard needs. */
@Serializable
data class MatchDetails(
    val match: Match,
    val deliveries: List<Delivery>,
)

@Serializable
data class ApiError(val message: String)
