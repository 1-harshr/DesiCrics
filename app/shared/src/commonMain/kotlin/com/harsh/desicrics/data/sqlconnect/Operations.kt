package com.harsh.desicrics.data.sqlconnect

import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.model.Wicket
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// Variables and results for the operations in dataconnect/connector/*.gql. Field names must match.

@Serializable
object NoVariables

@Serializable
class KeyResult(val id: String)

// region Profile

@Serializable
class GetMyProfileData(val user: UserRow? = null)

@Serializable
class UserRow(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val photoUrl: String? = null,
    val city: String,
    val role: PlayerRole,
    val battingStyle: BattingStyle,
    val bowlingStyle: BowlingStyle,
    val createdAt: String,
) {
    fun toModel() = UserProfile(
        uid = id,
        name = name,
        phone = phone,
        email = email,
        photoUrl = photoUrl,
        city = city,
        role = role,
        battingStyle = battingStyle,
        bowlingStyle = bowlingStyle,
        createdAt = createdAt.toEpochMillis(),
    )
}

@Serializable
class UpsertMyProfileVariables(
    val name: String,
    val phone: String,
    val email: String?,
    val photoUrl: String?,
    val city: String,
    val role: PlayerRole,
    val battingStyle: BattingStyle,
    val bowlingStyle: BowlingStyle,
)

// endregion

// region Match setup

@Serializable
class CreateMatchVariables(
    val teamAName: String,
    val teamBName: String,
    val oversPerInnings: Int,
    val tossWinner: TeamSide,
    val tossDecision: TossDecision,
)

@Serializable
class CreateMatchData(@Suppress("PropertyName") val match_insert: KeyResult)

@Serializable
class AddMatchPlayerVariables(val matchId: String, val side: TeamSide, val position: Int, val name: String)

@Serializable
class MatchIdVariables(val matchId: String)

// endregion

// region Reading matches

@Serializable
class ListRecentMatchesVariables(val limit: Int)

@Serializable
class ListRecentMatchesData(val matches: List<MatchRow>)

@Serializable
class GetMatchVariables(val id: String)

@Serializable
class GetMatchData(val match: MatchRow? = null)

@Serializable
class MatchRow(
    val id: String,
    val createdById: String,
    val createdAt: String,
    val teamAName: String,
    val teamBName: String,
    val oversPerInnings: Int,
    val tossWinner: TeamSide,
    val tossDecision: TossDecision,
    val status: MatchStatus,
    val scoreSummary: String,
    val result: String? = null,
    val players: List<PlayerRow> = emptyList(),
    val deliveries: List<DeliveryRow> = emptyList(),
) {
    fun toModel(): Match {
        fun team(side: TeamSide, name: String) = Team(
            name = name,
            players = players.filter { it.side == side }.sortedBy { it.position }.map { Player(it.id, it.name) },
        )
        return Match(
            id = id,
            createdBy = createdById,
            createdAt = createdAt.toEpochMillis(),
            teamA = team(TeamSide.A, teamAName),
            teamB = team(TeamSide.B, teamBName),
            oversPerInnings = oversPerInnings,
            tossWinner = tossWinner,
            tossDecision = tossDecision,
            status = status,
            scoreSummary = scoreSummary,
            result = result,
        )
    }
}

@Serializable
class PlayerRow(val id: String, val side: TeamSide, val position: Int, val name: String)

@Serializable
class DeliveryRow(
    val seq: Int,
    val innings: Int,
    val strikerId: String,
    val nonStrikerId: String,
    val bowlerId: String,
    val runsOffBat: Int,
    val extra: ExtraType? = null,
    val extraRuns: Int,
    val wicketKind: DismissalKind? = null,
    val playerOutId: String? = null,
) {
    fun toModel() = Delivery(
        seq = seq,
        innings = innings,
        strikerId = strikerId,
        nonStrikerId = nonStrikerId,
        bowlerId = bowlerId,
        runsOffBat = runsOffBat,
        extra = extra,
        extraRuns = extraRuns,
        wicket = if (wicketKind != null && playerOutId != null) Wicket(wicketKind, playerOutId) else null,
    )
}

// endregion

// region Scoring

@Serializable
class RecordDeliveryVariables(
    val matchId: String,
    val seq: Int,
    val innings: Int,
    val strikerId: String,
    val nonStrikerId: String,
    val bowlerId: String,
    val runsOffBat: Int,
    val extra: ExtraType?,
    val extraRuns: Int,
    val wicketKind: DismissalKind?,
    val playerOutId: String?,
    val scoreSummary: String,
    val status: MatchStatus,
    val result: String?,
)

@Serializable
class UndoDeliveryVariables(
    val matchId: String,
    val seq: Int,
    val scoreSummary: String,
    val status: MatchStatus,
    val result: String?,
)

/** Mutation results we don't read; errors surface as exceptions from [SqlConnectClient]. */
@Serializable
class IgnoredData

// endregion

@OptIn(ExperimentalTime::class)
private fun String.toEpochMillis(): Long = Instant.parse(this).toEpochMilliseconds()
