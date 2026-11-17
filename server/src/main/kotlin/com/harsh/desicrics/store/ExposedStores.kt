package com.harsh.desicrics.store

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.db.DeliveriesTable
import com.harsh.desicrics.db.MatchPlayersTable
import com.harsh.desicrics.db.MatchesTable
import com.harsh.desicrics.db.UsersTable
import com.harsh.desicrics.db.query
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
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
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.sql.SQLException

class ExposedUserStore(private val db: Database) : UserStore {

    override suspend fun get(uid: String): UserProfile? = db.query {
        UsersTable.selectAll().where { UsersTable.uid eq uid }.singleOrNull()?.toProfile()
    }

    override suspend fun upsert(profile: UserProfile) {
        db.query {
            UsersTable.upsert {
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
            }
        }
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
    )
}

class ExposedMatchStore(private val db: Database) : MatchStore {

    override suspend fun recent(limit: Int): List<Match> = db.query {
        val rows = MatchesTable.selectAll()
            .orderBy(MatchesTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .toList()
        // List cards don't show rosters, so skip loading players here.
        rows.map { it.toMatch(players = emptyList()) }
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
        MatchDetails(row.toMatch(players), deliveries)
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
                it[createdAt] = delivery.timestamp
            }
            updateScore(match)
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun removeDelivery(match: Match, seq: Int): Boolean = db.query {
        val deleted = DeliveriesTable.deleteWhere { (matchId eq match.id) and (DeliveriesTable.seq eq seq) }
        if (deleted > 0) updateScore(match)
        deleted > 0
    }

    private fun JdbcTransaction.insertRoster(matchId: String, side: TeamSide, team: Team) {
        team.players.forEachIndexed { index, player ->
            MatchPlayersTable.insert {
                it[id] = player.id
                it[MatchPlayersTable.matchId] = matchId
                it[MatchPlayersTable.side] = side
                it[position] = index
                it[name] = player.name
            }
        }
    }

    private fun JdbcTransaction.updateScore(match: Match) {
        MatchesTable.update({ MatchesTable.id eq match.id }) {
            it[scoreSummary] = match.scoreSummary
            it[status] = match.status
            it.setResult(match)
        }
    }

    private fun ResultRow.toMatch(players: List<ResultRow>): Match {
        fun team(side: TeamSide, name: String) = Team(
            name = name,
            players = players.filter { it[MatchPlayersTable.side] == side }
                .map { Player(it[MatchPlayersTable.id], it[MatchPlayersTable.name]) },
        )
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
            result = when {
                this[MatchesTable.status] != MatchStatus.COMPLETED -> null
                else -> MatchResult(
                    winner = this[MatchesTable.resultWinner],
                    margin = this[MatchesTable.resultMargin] ?: 0,
                    marginUnit = this[MatchesTable.resultMarginUnit],
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
        )
    }
}

private fun UpdateBuilder<*>.setResult(match: Match) {
    this[MatchesTable.resultWinner] = match.result?.winner
    this[MatchesTable.resultMargin] = match.result?.margin
    this[MatchesTable.resultMarginUnit] = match.result?.marginUnit
}

/** SQLSTATE 23505 means a unique or primary-key violation in both H2 and Postgres. */
private fun Throwable.isUniqueViolation(): Boolean =
    generateSequence(this) { it.cause }.any { it is SQLException && it.sqlState == "23505" }
