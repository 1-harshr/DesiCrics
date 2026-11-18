package com.harsh.desicrics.store

import com.harsh.desicrics.db.MatchesTable
import com.harsh.desicrics.db.PlayerMatchLinesTable
import com.harsh.desicrics.db.UsersTable
import com.harsh.desicrics.db.query
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.PlayerMatchLine
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

class ExposedStatsStore(private val db: Database) : StatsStore {

    override suspend fun replace(matchId: String, lines: List<PlayerMatchLine>) {
        db.query {
            PlayerMatchLinesTable.deleteWhere { PlayerMatchLinesTable.matchId eq matchId }
            PlayerMatchLinesTable.batchInsert(lines) { line ->
                this[PlayerMatchLinesTable.matchId] = matchId
                this[PlayerMatchLinesTable.profileId] = line.profileId
                this[PlayerMatchLinesTable.playedAt] = line.playedAt
                this[PlayerMatchLinesTable.tournamentId] = line.tournamentId
                this[PlayerMatchLinesTable.oversPerInnings] = line.oversPerInnings
                this[PlayerMatchLinesTable.line] = Json.encodeToString(line)
            }
        }
    }

    override suspend fun forProfiles(profileIds: Collection<String>): List<PlayerMatchLine> = if (profileIds.isEmpty()) emptyList() else db.query {
        PlayerMatchLinesTable.selectAll().where { PlayerMatchLinesTable.profileId inList profileIds }.map { it.toLine() }
    }

    override suspend fun forTournament(tournamentId: String): List<PlayerMatchLine> = db.query {
        PlayerMatchLinesTable.selectAll().where { PlayerMatchLinesTable.tournamentId eq tournamentId }.map { it.toLine() }
    }

    override suspend fun completedMatchesWithoutLines(): List<String> = db.query {
        val projected = PlayerMatchLinesTable.select(PlayerMatchLinesTable.matchId)
        MatchesTable.select(MatchesTable.id)
            .where { (MatchesTable.status eq MatchStatus.COMPLETED) and (MatchesTable.id notInSubQuery projected) }
            .map { it[MatchesTable.id] }
    }

    override suspend fun profilesInCity(city: String): List<String> = db.query {
        UsersTable.select(UsersTable.uid)
            .where { (UsersTable.city.lowerCase() eq city.trim().lowercase()) and UsersTable.deletedAt.isNull() }
            .map { it[UsersTable.uid] }
    }

    /** The column is the source of truth for whose line it is: claims move lines by updating only that. */
    private fun ResultRow.toLine(): PlayerMatchLine =
        Json.decodeFromString<PlayerMatchLine>(this[PlayerMatchLinesTable.line]).copy(profileId = this[PlayerMatchLinesTable.profileId])
}
