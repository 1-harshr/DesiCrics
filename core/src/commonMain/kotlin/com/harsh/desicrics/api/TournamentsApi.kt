package com.harsh.desicrics.api
import com.harsh.desicrics.model.AbandonReason
import com.harsh.desicrics.model.MatchEnding

import com.harsh.desicrics.model.EntryPolicy
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.OrganiserRole
import com.harsh.desicrics.model.Stage
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.TournamentFormat
import kotlinx.serialization.Serializable

// Tournaments.
//
//   GET    /tournaments/mine                       → List<TournamentSummary> (organised, or a team entered)
//   POST   /tournaments                            CreateTournamentRequest → TournamentDetails
//   GET    /tournaments/{id}                       → TournamentDetails
//   PUT    /tournaments/{id}/entry                 EntrySettingsRequest → TournamentDetails (organisers, before it starts)
//   POST   /tournaments/{id}/teams                 EnterTeamRequest → TournamentDetails (organisers, or a team admin as teamEntry allows)
//   PUT    /tournaments/{id}/teams/{teamId}        ReviewEntryRequest → TournamentDetails (organisers)
//   DELETE /tournaments/{id}/teams/{teamId}        → TournamentDetails (organisers, before it starts)
//   POST   /tournaments/{id}/players               AddTournamentPlayerRequest → TournamentDetails (yourself as playerEntry allows;
//                                                  organisers anyone; team admins their team's members, into its squad)
//   PUT    /tournaments/{id}/players/{profileId}   PlacePlayerRequest → TournamentDetails (organisers)
//   DELETE /tournaments/{id}/players/{profileId}   → TournamentDetails (organisers; the player; their team's admins before it starts)
//   POST   /tournaments/{id}/organisers            AddOrganiserRequest → TournamentDetails (organisers)
//   POST   /tournaments/{id}/start                 → TournamentDetails with the first fixtures (organisers)
//   POST   /tournaments/{id}/fixtures              CustomFixtureRequest → TournamentDetails (organisers)
//   PUT    /matches/{id}/schedule                  ScheduleRequest → MatchDetails (organisers)
//   POST   /matches/{id}/start                     StartFixtureRequest → MatchDetails (organisers; the caller scores it)
//   GET    /t/{id}                                 → public HTML tournament page

@Serializable
data class CreateTournamentRequest(
    val name: String,
    val city: String = "",
    val format: TournamentFormat,
    val oversPerInnings: Int,
    val rules: MatchRules = MatchRules(),
    val groups: Int = 1,
    val advance: Int = 0,
    val startsAt: Long? = null,
    val teamEntry: EntryPolicy = EntryPolicy.APPROVAL,
    val playerEntry: EntryPolicy = EntryPolicy.APPROVAL,
) {
    fun validate(): TournamentError? = when {
        name.isBlank() -> TournamentError.NAME_MISSING
        name.trim().length > MAX_NAME -> TournamentError.NAME_TOO_LONG
        city.length > 100 -> TournamentError.NAME_TOO_LONG
        oversPerInnings !in CreateMatchRequest.OVERS -> TournamentError.OVERS_OUT_OF_RANGE
        !rules.isValid() -> TournamentError.RULES_INVALID
        format == TournamentFormat.GROUPS_KNOCKOUT && groups !in 2..8 -> TournamentError.GROUPS_INVALID
        format != TournamentFormat.GROUPS_KNOCKOUT && groups != 1 -> TournamentError.GROUPS_INVALID
        advance !in ADVANCE -> TournamentError.ADVANCE_INVALID
        format == TournamentFormat.GROUPS_KNOCKOUT && advance == 0 -> TournamentError.ADVANCE_INVALID
        else -> null
    }

    companion object {
        const val MAX_NAME = 80
        /** Teams through to a playoff, per group: none, a final, semis or quarters. */
        val ADVANCE = setOf(0, 1, 2, 4, 8)
    }
}

@Serializable
data class EntrySettingsRequest(val teamEntry: EntryPolicy, val playerEntry: EntryPolicy)

/** Enters a saved team. Organisers' entries are confirmed; a team admin's waits for review unless entry is open. */
@Serializable
data class EnterTeamRequest(val teamId: String)

/**
 * Adds a player to a tournament: with no [profileId], the caller joins the pool themselves. Organisers add
 * anyone, to the pool or straight into [teamId]'s squad; a team's admins add its members to its squad.
 */
@Serializable
data class AddTournamentPlayerRequest(val profileId: String? = null, val teamId: String? = null)

/** Confirms a waiting player, and puts them in [teamId]'s squad, or back in the pool when it's null. */
@Serializable
data class PlacePlayerRequest(val teamId: String? = null)

@Serializable
data class ReviewEntryRequest(val confirm: Boolean)

@Serializable
data class AddOrganiserRequest(val profileId: String, val role: OrganiserRole = OrganiserRole.CO_ORGANISER)

/** A fixture an organiser adds by hand. */
@Serializable
data class CustomFixtureRequest(val teamAId: String, val teamBId: String, val stage: Stage = Stage.MATCH, val scheduledAt: Long? = null)

@Serializable
data class ScheduleRequest(val scheduledAt: Long?)

/** Starts a scheduled fixture: who's playing for each side, and the toss. */
@Serializable
data class StartFixtureRequest(
    val teamA: SquadRequest,
    val teamB: SquadRequest,
    val tossWinner: TeamSide,
    val tossDecision: TossDecision,
)

/**
 * Ends a scheduled match that never starts: [MatchEnding.WALKOVER] to [winner] when the other side didn't turn
 * up, or [MatchEnding.ABANDONED] for [reason] when it's called off.
 */
@Serializable
data class EndBeforeStartRequest(
    val ending: MatchEnding,
    val winner: TeamSide? = null,
    val reason: AbandonReason? = null,
)

enum class TournamentError(val message: String) {
    NAME_MISSING("Give the tournament a name"),
    NAME_TOO_LONG("That name is too long"),
    OVERS_OUT_OF_RANGE("Overs must be between 1 and 50"),
    RULES_INVALID("Some of the match rules are out of range"),
    GROUPS_INVALID("Group tournaments have 2 to 8 groups; other formats have one"),
    ADVANCE_INVALID("Pick how many teams go through: 1, 2, 4 or 8"),
    TOO_FEW_TEAMS("Not enough teams for this format yet"),
}
