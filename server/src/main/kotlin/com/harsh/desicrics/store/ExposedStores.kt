package com.harsh.desicrics.store

import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.db.TeamsTable
import com.harsh.desicrics.db.FollowsTable
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.db.DeliveriesTable
import com.harsh.desicrics.db.MatchEventsTable
import com.harsh.desicrics.db.MatchPlayersTable
import com.harsh.desicrics.db.MatchesTable
import com.harsh.desicrics.db.ScorerHandoversTable
import com.harsh.desicrics.db.UsersTable
import com.harsh.desicrics.db.query
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.FixtureInfo
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.model.Wicket
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlinx.serialization.json.Json
import java.sql.SQLException

class ExposedUserStore(private val db: Database) : UserStore {

    /** Deleted accounts read as having no profile, so signing up again starts from scratch. */
    override suspend fun get(uid: String): UserProfile? = db.query {
        UsersTable.selectAll().where { (UsersTable.uid eq uid) and UsersTable.deletedAt.isNull() }.singleOrNull()?.toProfile()
    }

    /** A new user gets a fresh public code; an existing one keeps theirs. */
    override suspend fun upsert(profile: UserProfile) {
        // Codes are random, so a clash is rare and retrying is enough.
        repeat(5) { if (save(profile, com.harsh.desicrics.service.newPublicCode())) return }
        error("Couldn't make a player code")
    }

    private suspend fun save(profile: UserProfile, newCode: String): Boolean = try {
        db.query {
            UsersTable.upsert(onUpdateExclude = listOf(UsersTable.code)) {
                it[uid] = profile.uid
                it[name] = profile.name
                it[phone] = profile.phone
                it[email] = profile.email
                it[photoUrl] = profile.photoUrl
                it[city] = profile.city
                it[role] = profile.role
                it[battingStyle] = profile.battingStyle
                it[bowlingStyle] = profile.bowlingStyle
                it[createdAt] = profile.createdAt
                it[deletedAt] = null
                it[code] = profile.code.ifEmpty { newCode }
            }
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    private fun ResultRow.toProfile() = UserProfile(
        uid = this[UsersTable.uid],
        name = this[UsersTable.name],
        phone = this[UsersTable.phone],
        email = this[UsersTable.email],
        photoUrl = this[UsersTable.photoUrl],
        city = this[UsersTable.city],
        role = this[UsersTable.role],
        battingStyle = this[UsersTable.battingStyle],
        bowlingStyle = this[UsersTable.bowlingStyle],
        createdAt = this[UsersTable.createdAt],
        code = this[UsersTable.code],
    )
}

class ExposedMatchStore(private val db: Database) : MatchStore {

    override suspend fun feedPage(status: MatchStatus?, before: Long?, afterId: String, uid: String, scope: String?): List<Match> = db.query {
        val query = MatchesTable.selectAll()
        when (scope) {
            "following" -> {
                fun followed(type: FollowType) = FollowsTable.select(FollowsTable.targetId).where { (FollowsTable.uid eq uid) and (FollowsTable.targetType eq type) }
                val played = MatchPlayersTable.select(MatchPlayersTable.matchId).where { MatchPlayersTable.profileId inSubQuery followed(FollowType.PLAYER) }
                query.andWhere { (MatchesTable.teamAId inSubQuery followed(FollowType.TEAM)) or (MatchesTable.teamBId inSubQuery followed(FollowType.TEAM)) or (MatchesTable.id inSubQuery played) }
            }
            "city" -> {
                val city = UsersTable.select(UsersTable.city).where { UsersTable.uid eq uid }.singleOrNull()?.get(UsersTable.city)?.trim()?.lowercase().orEmpty()
                if (city.isBlank()) return@query emptyList()
                val local = TeamsTable.select(TeamsTable.id).where { TeamsTable.city.lowerCase() eq city }
                val hosts = UsersTable.select(UsersTable.uid).where { UsersTable.city.lowerCase() eq city }
                query.andWhere { (MatchesTable.teamAId inSubQuery local) or (MatchesTable.teamBId inSubQuery local) or (MatchesTable.createdBy inSubQuery hosts) }
            }
        }
        status?.let { query.andWhere { MatchesTable.status eq it } }
        before?.let { time -> query.andWhere {
            (MatchesTable.createdAt less time) or ((MatchesTable.createdAt eq time) and (MatchesTable.id less afterId))
        } }
        query.orderBy(MatchesTable.createdAt to SortOrder.DESC, MatchesTable.id to SortOrder.DESC)
            .limit(30).map { it.toMatch(players = emptyList()) }
    }

    override suspend fun recent(limit: Int): List<Match> = db.query {
        val rows = MatchesTable.selectAll()
            .orderBy(MatchesTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .toList()
        // List cards don't show rosters, so skip loading players here.
        rows.map { it.toMatch(players = emptyList()) }
    }

    override suspend fun forPlayer(profileId: String, limit: Int): List<Match> = db.query {
        val played = MatchPlayersTable.select(MatchPlayersTable.matchId).where { MatchPlayersTable.profileId eq profileId }
        MatchesTable.selectAll()
            .where { (MatchesTable.id inSubQuery played) or (MatchesTable.scorerUid eq profileId) or (MatchesTable.createdBy eq profileId) }
            .orderBy(MatchesTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toMatch(players = emptyList()) }
    }

    override suspend fun forTournament(tournamentId: String): List<Match> = db.query {
        MatchesTable.selectAll()
            .where { MatchesTable.tournamentId eq tournamentId }
            .orderBy(MatchesTable.roundNo to SortOrder.ASC, MatchesTable.fixtureNo to SortOrder.ASC, MatchesTable.groupNo to SortOrder.ASC)
            .map { it.toMatch(players = emptyList()) }
    }

    override suspend fun startFixture(match: Match): Boolean = db.query {
        val updated = MatchesTable.update({ (MatchesTable.id eq match.id) and (MatchesTable.status eq MatchStatus.SCHEDULED) }) {
            it[status] = match.status
            it[scorerUid] = match.scorerUid
            it[tossWinner] = match.tossWinner
            it[tossDecision] = match.tossDecision
            it[teamAName] = match.teamA.name
            it[teamBName] = match.teamB.name
            it[scoreSummary] = match.scoreSummary
        }
        if (updated == 0) return@query false
        MatchPlayersTable.deleteWhere { matchId eq match.id }
        insertRoster(match.id, TeamSide.A, match.teamA)
        insertRoster(match.id, TeamSide.B, match.teamB)
        true
    }

    override suspend fun setSchedule(matchId: String, scheduledAt: Long?) {
        db.query { MatchesTable.update({ MatchesTable.id eq matchId }) { it[MatchesTable.scheduledAt] = scheduledAt } }
    }

    override suspend fun profileIds(matchIds: Collection<String>): Map<String, Set<String>> =
        if (matchIds.isEmpty()) emptyMap() else db.query {
            MatchPlayersTable.select(MatchPlayersTable.matchId, MatchPlayersTable.profileId)
                .where { (MatchPlayersTable.matchId inList matchIds) and MatchPlayersTable.profileId.isNotNull() }
                .groupBy({ it[MatchPlayersTable.matchId] }, { it[MatchPlayersTable.profileId]!! })
                .mapValues { it.value.toSet() }
        }

    override suspend fun search(query: String, limit: Int): List<Match> = db.query {
        val pattern = "%${query.trim().lowercase().escapeLike()}%"
        MatchesTable.selectAll()
            .where { (MatchesTable.teamAName.lowerCase() like pattern) or (MatchesTable.teamBName.lowerCase() like pattern) }
            .orderBy(MatchesTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toMatch(players = emptyList()) }
    }

    override suspend fun completedBetweenSavedTeams(): List<Match> = db.query {
        MatchesTable.selectAll()
            .where { (MatchesTable.status eq MatchStatus.COMPLETED) and MatchesTable.teamAId.isNotNull() and MatchesTable.teamBId.isNotNull() }
            .orderBy(MatchesTable.createdAt)
            .map { it.toMatch(players = emptyList()) }
    }

    override suspend fun between(teamId: String, otherId: String): List<Match> = db.query {
        MatchesTable.selectAll()
            .where {
                ((MatchesTable.teamAId eq teamId) and (MatchesTable.teamBId eq otherId)) or
                    ((MatchesTable.teamAId eq otherId) and (MatchesTable.teamBId eq teamId))
            }
            .map { it.toMatch(players = emptyList()) }
    }

    override suspend fun completedForTeam(teamId: String, limit: Int): List<MatchDetails> {
        val ids = db.query {
            MatchesTable.select(MatchesTable.id)
                .where { (MatchesTable.status eq MatchStatus.COMPLETED) and ((MatchesTable.teamAId eq teamId) or (MatchesTable.teamBId eq teamId)) }
                .orderBy(MatchesTable.createdAt, SortOrder.DESC)
                .limit(limit)
                .map { it[MatchesTable.id] }
        }
        return ids.mapNotNull { details(it) }
    }

    override suspend fun lastSquad(teamId: String): List<String> = db.query {
        val latest = MatchesTable.selectAll()
            .where { (MatchesTable.teamAId eq teamId) or (MatchesTable.teamBId eq teamId) }
            .orderBy(MatchesTable.createdAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull() ?: return@query emptyList()
        val side = if (latest[MatchesTable.teamAId] == teamId) TeamSide.A else TeamSide.B
        MatchPlayersTable.selectAll()
            .where { (MatchPlayersTable.matchId eq latest[MatchesTable.id]) and (MatchPlayersTable.side eq side) }
            .orderBy(MatchPlayersTable.position)
            .mapNotNull { it[MatchPlayersTable.profileId] }
    }

    override suspend fun details(matchId: String): MatchDetails? = db.query {
        val row = MatchesTable.selectAll().where { MatchesTable.id eq matchId }.singleOrNull() ?: return@query null
        val players = MatchPlayersTable.selectAll()
            .where { MatchPlayersTable.matchId eq matchId }
            .orderBy(MatchPlayersTable.position)
            .toList()
        val deliveries = DeliveriesTable.selectAll()
            .where { DeliveriesTable.matchId eq matchId }
            .orderBy(DeliveriesTable.seq)
            .map { it.toDelivery() }
        val events = MatchEventsTable.selectAll()
            .where { MatchEventsTable.matchId eq matchId }
            .orderBy(MatchEventsTable.seq)
            .map { it.toEvent() }
        MatchDetails(row.toMatch(players), deliveries, events)
    }

    override suspend fun create(match: Match) {
        db.query {
            MatchesTable.insert {
                it[id] = match.id
                it[createdBy] = match.createdBy
                it[createdAt] = match.createdAt
                it[teamAName] = match.teamA.name
                it[teamBName] = match.teamB.name
                it[oversPerInnings] = match.oversPerInnings
                it[tossWinner] = match.tossWinner
                it[tossDecision] = match.tossDecision
                it[status] = match.status
                it[scoreSummary] = match.scoreSummary
                it[rules] = Json.encodeToString(match.rules)
                it[scorerUid] = match.scorerUid
                it[teamAId] = match.teamA.teamId
                it[teamBId] = match.teamB.teamId
                it[venue] = match.venue
                it[scheduledAt] = match.scheduledAt
                it[tournamentId] = match.fixture?.tournamentId
                it[stage] = match.fixture?.stage
                it[roundNo] = match.fixture?.round
                it[fixtureNo] = match.fixture?.number
                it[groupNo] = match.fixture?.group
                it.setResult(match)
            }
            insertRoster(match.id, TeamSide.A, match.teamA)
            insertRoster(match.id, TeamSide.B, match.teamB)
        }
    }

    override suspend fun addDelivery(match: Match, delivery: Delivery): Boolean = try {
        db.query {
            DeliveriesTable.insert {
                it[matchId] = match.id
                it[seq] = delivery.seq
                it[innings] = delivery.innings
                it[strikerId] = delivery.strikerId
                it[nonStrikerId] = delivery.nonStrikerId
                it[bowlerId] = delivery.bowlerId
                it[runsOffBat] = delivery.runsOffBat
                it[extra] = delivery.extra
                it[extraRuns] = delivery.extraRuns
                it[wicketKind] = delivery.wicket?.kind
                it[playerOutId] = delivery.wicket?.playerOutId
                it[fielderId] = delivery.wicket?.fielderId
                it[extraPenalty] = delivery.extraPenalty
                it[countsAsBall] = delivery.countsAsBall
                it[zone] = delivery.zone
                it[createdAt] = delivery.timestamp
            }
            updateScore(match)
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun addEvent(match: Match, event: MatchEvent): Boolean = try {
        db.query {
            MatchEventsTable.insert {
                it[matchId] = match.id
                it[seq] = event.seq
                it[innings] = event.innings
                it[type] = event.typeName
                it[playerId] = when (event) {
                    is MatchEvent.Retire -> event.playerId
                    is MatchEvent.ReplaceBowler -> event.bowlerId
                    else -> null
                }
                it[isOut] = (event as? MatchEvent.Retire)?.out ?: false
                it[runs] = (event as? MatchEvent.Penalty)?.runs ?: 0
                it[toBattingSide] = (event as? MatchEvent.Penalty)?.toBattingSide ?: false
                it[overs] = (event as? MatchEvent.ReviseTarget)?.overs
                it[target] = (event as? MatchEvent.ReviseTarget)?.target
                (event as? MatchEvent.EndMatch)?.let { end ->
                    it[ending] = end.ending
                    it[winner] = end.winner
                    it[reason] = end.reason
                    it[beforeStart] = end.beforeStart
                }
                it[createdAt] = event.timestamp
            }
            updateScore(match)
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun removeEntry(match: Match, seq: Int): Boolean = db.query {
        val deleted = DeliveriesTable.deleteWhere { (matchId eq match.id) and (DeliveriesTable.seq eq seq) } +
            MatchEventsTable.deleteWhere { (matchId eq match.id) and (MatchEventsTable.seq eq seq) }
        if (deleted > 0) updateScore(match)
        deleted > 0
    }

    override suspend fun addPlayer(matchId: String, side: TeamSide, player: Player) {
        db.query {
            val position = MatchPlayersTable.selectAll()
                .where { (MatchPlayersTable.matchId eq matchId) and (MatchPlayersTable.side eq side) }
                .count().toInt()
            MatchPlayersTable.insert {
                it[id] = player.id
                it[MatchPlayersTable.matchId] = matchId
                it[MatchPlayersTable.side] = side
                it[MatchPlayersTable.position] = position
                it[name] = player.name
                it[profileId] = player.profileId
                it[isCaptain] = false
                it[isKeeper] = false
            }
        }
    }

    private fun ResultRow.toEvent(): MatchEvent {
        val seq = this[MatchEventsTable.seq]
        val innings = this[MatchEventsTable.innings]
        val at = this[MatchEventsTable.createdAt]
        return when (this[MatchEventsTable.type]) {
            RETIRE -> MatchEvent.Retire(seq, innings, checkNotNull(this[MatchEventsTable.playerId]), this[MatchEventsTable.isOut], at)
            SWAP_ENDS -> MatchEvent.SwapEnds(seq, innings, at)
            REPLACE_BOWLER -> MatchEvent.ReplaceBowler(seq, innings, this[MatchEventsTable.playerId], at)
            PENALTY -> MatchEvent.Penalty(seq, innings, this[MatchEventsTable.toBattingSide], this[MatchEventsTable.runs], at)
            DECLARE -> MatchEvent.Declare(seq, innings, at)
            REVISE_TARGET ->
                MatchEvent.ReviseTarget(seq, innings, checkNotNull(this[MatchEventsTable.overs]), this[MatchEventsTable.target], at)
            END_MATCH -> MatchEvent.EndMatch(
                seq, innings, checkNotNull(this[MatchEventsTable.ending]), this[MatchEventsTable.winner],
                this[MatchEventsTable.reason], this[MatchEventsTable.beforeStart], at,
            )
            else -> error("Unknown match event type ${this[MatchEventsTable.type]}")
        }
    }

    private fun JdbcTransaction.insertRoster(matchId: String, side: TeamSide, team: Team) {
        team.players.forEachIndexed { index, player ->
            MatchPlayersTable.insert {
                it[id] = player.id
                it[MatchPlayersTable.matchId] = matchId
                it[MatchPlayersTable.side] = side
                it[position] = index
                it[name] = player.name
                it[profileId] = player.profileId
                it[isCaptain] = player.id == team.captainId
                it[isKeeper] = player.id == team.keeperId
            }
        }
    }

    override suspend fun saveHandover(matchId: String, code: String, expiresAt: Long): Boolean = try {
        db.query {
            ScorerHandoversTable.deleteWhere { ScorerHandoversTable.matchId eq matchId }
            ScorerHandoversTable.insert {
                it[this.code] = code
                it[this.matchId] = matchId
                it[this.expiresAt] = expiresAt
            }
        }
        true
    } catch (e: Exception) {
        // The code is the key; a clash means another match holds it, so the caller picks a new one.
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun takeHandover(code: String, now: Long, newScorer: String): String? = db.query {
        val row = ScorerHandoversTable.selectAll().where { ScorerHandoversTable.code eq code }.singleOrNull()
            ?: return@query null
        ScorerHandoversTable.deleteWhere { ScorerHandoversTable.code eq code }
        if (row[ScorerHandoversTable.expiresAt] < now) return@query null
        val matchId = row[ScorerHandoversTable.matchId]
        MatchesTable.update({ MatchesTable.id eq matchId }) { it[scorerUid] = newScorer }
        matchId
    }

    override suspend fun idsMissingInningsScores(): List<String> = db.query {
        val withBalls = DeliveriesTable.select(DeliveriesTable.matchId).withDistinct()
        MatchesTable.select(MatchesTable.id)
            .where { (MatchesTable.inningsScores eq "[]") and (MatchesTable.id inSubQuery withBalls) }
            .map { it[MatchesTable.id] }
    }

    override suspend fun setScorer(matchId: String, uid: String) {
        db.query { MatchesTable.update({ MatchesTable.id eq matchId }) { it[scorerUid] = uid } }
    }

    override suspend fun updateScore(match: Match) {
        db.query { updateScore(match) }
    }

    private fun JdbcTransaction.updateScore(match: Match) {
        MatchesTable.update({ MatchesTable.id eq match.id }) {
            it[scoreSummary] = match.scoreSummary
            it[status] = match.status
            it.setResult(match)
        }
    }

    private fun ResultRow.toMatch(players: List<ResultRow>): Match {
        fun team(side: TeamSide, name: String): Team {
            val rows = players.filter { it[MatchPlayersTable.side] == side }
            return Team(
                name = name,
                players = rows.map { Player(it[MatchPlayersTable.id], it[MatchPlayersTable.name], it[MatchPlayersTable.profileId]) },
                teamId = this[if (side == TeamSide.A) MatchesTable.teamAId else MatchesTable.teamBId],
                captainId = rows.firstOrNull { it[MatchPlayersTable.isCaptain] }?.get(MatchPlayersTable.id),
                keeperId = rows.firstOrNull { it[MatchPlayersTable.isKeeper] }?.get(MatchPlayersTable.id),
            )
        }
        return Match(
            id = this[MatchesTable.id],
            createdBy = this[MatchesTable.createdBy],
            createdAt = this[MatchesTable.createdAt],
            teamA = team(TeamSide.A, this[MatchesTable.teamAName]),
            teamB = team(TeamSide.B, this[MatchesTable.teamBName]),
            oversPerInnings = this[MatchesTable.oversPerInnings],
            tossWinner = this[MatchesTable.tossWinner],
            tossDecision = this[MatchesTable.tossDecision],
            status = this[MatchesTable.status],
            scoreSummary = this[MatchesTable.scoreSummary],
            innings = Json.decodeFromString(this[MatchesTable.inningsScores]),
            rules = Json.decodeFromString(this[MatchesTable.rules]),
            scorerUid = this[MatchesTable.scorerUid],
            venue = this[MatchesTable.venue],
            scheduledAt = this[MatchesTable.scheduledAt],
            fixture = this[MatchesTable.tournamentId]?.let { tournament ->
                FixtureInfo(tournament, this[MatchesTable.stage]!!, this[MatchesTable.roundNo]!!, this[MatchesTable.fixtureNo]!!, this[MatchesTable.groupNo])
            },
            result = when {
                this[MatchesTable.status] != MatchStatus.COMPLETED -> null
                else -> MatchResult(
                    winner = this[MatchesTable.resultWinner],
                    margin = this[MatchesTable.resultMargin] ?: 0,
                    marginUnit = this[MatchesTable.resultMarginUnit],
                    ending = this[MatchesTable.resultEnding],
                    reason = this[MatchesTable.resultReason],
                )
            },
        )
    }

    private fun ResultRow.toDelivery(): Delivery {
        val kind = this[DeliveriesTable.wicketKind]
        val out = this[DeliveriesTable.playerOutId]
        return Delivery(
            seq = this[DeliveriesTable.seq],
            innings = this[DeliveriesTable.innings],
            strikerId = this[DeliveriesTable.strikerId],
            nonStrikerId = this[DeliveriesTable.nonStrikerId],
            bowlerId = this[DeliveriesTable.bowlerId],
            runsOffBat = this[DeliveriesTable.runsOffBat],
            extra = this[DeliveriesTable.extra],
            extraRuns = this[DeliveriesTable.extraRuns],
            wicket = if (kind != null && out != null) Wicket(kind, out, this[DeliveriesTable.fielderId]) else null,
            timestamp = this[DeliveriesTable.createdAt],
            extraPenalty = this[DeliveriesTable.extraPenalty],
            countsAsBall = this[DeliveriesTable.countsAsBall],
            zone = this[DeliveriesTable.zone],
        )
    }
}

private fun UpdateBuilder<*>.setResult(match: Match) {
    this[MatchesTable.resultWinner] = match.result?.winner
    this[MatchesTable.resultMargin] = match.result?.margin
    this[MatchesTable.resultMarginUnit] = match.result?.marginUnit
    this[MatchesTable.resultEnding] = match.result?.ending
    this[MatchesTable.resultReason] = match.result?.reason
    this[MatchesTable.inningsScores] = Json.encodeToString(match.innings)
}

private const val RETIRE = "retire"
private const val SWAP_ENDS = "swap_ends"
private const val REPLACE_BOWLER = "replace_bowler"
private const val PENALTY = "penalty"
private const val DECLARE = "declare"
private const val REVISE_TARGET = "revise_target"
private const val END_MATCH = "end_match"

private val MatchEvent.typeName: String
    get() = when (this) {
        is MatchEvent.Retire -> RETIRE
        is MatchEvent.SwapEnds -> SWAP_ENDS
        is MatchEvent.ReplaceBowler -> REPLACE_BOWLER
        is MatchEvent.Penalty -> PENALTY
        is MatchEvent.Declare -> DECLARE
        is MatchEvent.ReviseTarget -> REVISE_TARGET
        is MatchEvent.EndMatch -> END_MATCH
    }

/** SQLSTATE 23505 means a unique or primary-key violation in both H2 and Postgres. */
internal fun Throwable.isUniqueViolation(): Boolean =
    generateSequence(this) { it.cause }.any { it is SQLException && it.sqlState == "23505" }
