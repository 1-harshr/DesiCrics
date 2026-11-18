package com.harsh.desicrics.api

import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchRules
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
//   POST   /matches/{id}/events             MatchEvent → MatchDetails
//   POST   /matches/{id}/players            AddPlayerRequest → MatchDetails
//   DELETE /matches/{id}/last               → MatchDetails (undoes the latest ball or event)
//   POST   /matches/{id}/handover           → HandoverCode (scorer only)
//   POST   /handover/{code}                 → MatchDetails (the caller becomes the scorer)

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

/** One side of a new match: a saved team's picked squad, or a name and typed-in players. */
@Serializable
data class SquadRequest(
    val name: String,
    val players: List<SquadPlayer>,
    val teamId: String? = null,
    /** Indexes into [players]. */
    val captain: Int? = null,
    val keeper: Int? = null,
)

/** A player picked for a match: a member of a saved team ([profileId]), or a guest known only by name. */
@Serializable
data class SquadPlayer(val name: String, val profileId: String? = null)

@Serializable
data class CreateMatchRequest(
    val teamA: SquadRequest,
    val teamB: SquadRequest,
    val oversPerInnings: Int,
    val tossWinner: TeamSide,
    val tossDecision: TossDecision,
    val rules: MatchRules = MatchRules(),
) {
    /** Checked on the device for instant feedback and again on the server. */
    fun validate(): MatchSetupError? {
        val sides = listOf(teamA, teamB)
        val profiles = sides.flatMap { side -> side.players.mapNotNull { it.profileId } }
        return when {
            sides.any { it.name.isBlank() } -> MatchSetupError.TEAM_NAME_MISSING
            teamA.name.trim().equals(teamB.name.trim(), ignoreCase = true) -> MatchSetupError.SAME_TEAM_NAMES
            sides.any { it.name.length > MAX_NAME } -> MatchSetupError.TEAM_NAME_TOO_LONG
            sides.any { it.players.size < MIN_PLAYERS } -> MatchSetupError.TOO_FEW_PLAYERS
            sides.any { it.players.size > MAX_PLAYERS } -> MatchSetupError.TOO_MANY_PLAYERS
            sides.any { side -> side.players.any { it.name.isBlank() || it.name.length > MAX_NAME } } -> MatchSetupError.PLAYER_NAME_INVALID
            // Nobody can play for both sides, or twice for one.
            profiles.size != profiles.toSet().size -> MatchSetupError.PLAYER_TWICE
            sides.any { side -> listOfNotNull(side.captain, side.keeper).any { it !in side.players.indices } } -> MatchSetupError.CAPTAIN_INVALID
            oversPerInnings !in OVERS -> MatchSetupError.OVERS_OUT_OF_RANGE
            !rules.isValid() -> MatchSetupError.RULES_INVALID
            else -> null
        }
    }

    /** English message for API error responses; the app shows its own localised text for [validate]. */
    fun validationError(): String? = validate()?.message

    companion object {
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 20
        const val MAX_NAME = 60
        val OVERS = 1..50
    }
}

enum class MatchSetupError(val message: String) {
    TEAM_NAME_MISSING("Give both teams a name"),
    SAME_TEAM_NAMES("Teams need different names"),
    TEAM_NAME_TOO_LONG("Team names must be 60 characters or fewer"),
    TOO_FEW_PLAYERS("Each team needs at least 2 players"),
    TOO_MANY_PLAYERS("Teams can have at most 20 players"),
    PLAYER_NAME_INVALID("Player names must be 1–60 characters"),
    OVERS_OUT_OF_RANGE("Overs must be between 1 and 50"),
    RULES_INVALID("Some of the match rules are out of range"),
    PLAYER_TWICE("A player can only be picked once"),
    CAPTAIN_INVALID("The captain and keeper must be in the squad"),
}

/** A match with every ball bowled and every event so far, which is everything a scorecard needs. */
@Serializable
data class MatchDetails(
    val match: Match,
    val deliveries: List<Delivery>,
    val events: List<MatchEvent> = emptyList(),
) {
    /** The sequence number the next ball or event must use. */
    val nextSeq: Int get() = (deliveries.map { it.seq } + events.map { it.seq }).maxOrNull()?.plus(1) ?: 0
}

/** A latecomer or substitute joining a team mid-match. */
@Serializable
data class AddPlayerRequest(val side: TeamSide, val name: String) {
    fun validationError(): String? = when {
        name.isBlank() || name.trim().length > CreateMatchRequest.MAX_NAME -> MatchSetupError.PLAYER_NAME_INVALID.message
        else -> null
    }
}

/** A short-lived code the scorer shows so someone else can take over scoring on their phone. */
@Serializable
data class HandoverCode(val code: String, val expiresAt: Long)

@Serializable
data class ApiError(val message: String)
