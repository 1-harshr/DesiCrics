package com.harsh.desicrics.store

import com.harsh.desicrics.db.PlayersTable
import com.harsh.desicrics.db.TeamMembersTable
import com.harsh.desicrics.db.TeamsTable
import com.harsh.desicrics.db.TournamentOrganisersTable
import com.harsh.desicrics.db.TournamentTeamsTable
import com.harsh.desicrics.db.TournamentsTable
import com.harsh.desicrics.db.query
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.Organiser
import com.harsh.desicrics.model.OrganiserRole
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.TournamentSummary
import com.harsh.desicrics.model.TournamentTeam
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

class ExposedTournamentStore(private val db: Database) : TournamentStore {

    override suspend fun create(tournament: Tournament) {
        db.query {
            TournamentsTable.insert {
                it[id] = tournament.id
                it[name] = tournament.name
                it[city] = tournament.city
                it[format] = tournament.format
                it[oversPerInnings] = tournament.oversPerInnings
                it[rules] = Json.encodeToString(tournament.rules)
                it[groupCount] = tournament.groups
                it[advance] = tournament.advance
                it[startsAt] = tournament.startsAt
                it[registrationOpen] = tournament.registrationOpen
                it[started] = false
                it[createdBy] = tournament.createdBy
                it[createdAt] = tournament.createdAt
            }
            for (organiser in tournament.organisers) {
                TournamentOrganisersTable.insert {
                    it[tournamentId] = tournament.id
                    it[uid] = organiser.uid
                    it[role] = organiser.role
                }
            }
        }
    }

    override suspend fun get(tournamentId: String): Tournament? = db.query {
        val row = TournamentsTable.selectAll().where { TournamentsTable.id eq tournamentId }.singleOrNull() ?: return@query null
        val organisers = TournamentOrganisersTable.join(PlayersTable, JoinType.LEFT, TournamentOrganisersTable.uid, PlayersTable.id)
            .selectAll().where { TournamentOrganisersTable.tournamentId eq tournamentId }
            .map { Organiser(it[TournamentOrganisersTable.uid], it.getOrNull(PlayersTable.name) ?: "", it[TournamentOrganisersTable.role]) }
        val teams = TournamentTeamsTable.join(TeamsTable, JoinType.INNER, TournamentTeamsTable.teamId, TeamsTable.id)
            .selectAll().where { TournamentTeamsTable.tournamentId eq tournamentId }
            .orderBy(TournamentTeamsTable.seed)
            .map {
                TournamentTeam(
                    teamId = it[TournamentTeamsTable.teamId],
                    name = it[TeamsTable.name],
                    seed = it[TournamentTeamsTable.seed],
                    group = it[TournamentTeamsTable.groupNo],
                    status = it[TournamentTeamsTable.status],
                )
            }
        Tournament(
            id = row[TournamentsTable.id],
            name = row[TournamentsTable.name],
            city = row[TournamentsTable.city],
            format = row[TournamentsTable.format],
            oversPerInnings = row[TournamentsTable.oversPerInnings],
            rules = Json.decodeFromString(row[TournamentsTable.rules]),
            groups = row[TournamentsTable.groupCount],
            advance = row[TournamentsTable.advance],
            createdBy = row[TournamentsTable.createdBy],
            createdAt = row[TournamentsTable.createdAt],
            startsAt = row[TournamentsTable.startsAt],
            registrationOpen = row[TournamentsTable.registrationOpen],
            organisers = organisers,
            teams = teams,
            started = row[TournamentsTable.started],
        )
    }

    override suspend fun forUser(uid: String): List<TournamentSummary> = db.query {
        val organised = TournamentOrganisersTable.select(TournamentOrganisersTable.tournamentId)
            .where { TournamentOrganisersTable.uid eq uid }
            .map { it[TournamentOrganisersTable.tournamentId] }.toSet()
        val myTeams = TeamMembersTable.select(TeamMembersTable.teamId).where { TeamMembersTable.playerId eq uid }.map { it[TeamMembersTable.teamId] }
        val entered = if (myTeams.isEmpty()) emptySet() else TournamentTeamsTable.select(TournamentTeamsTable.tournamentId)
            .where { TournamentTeamsTable.teamId inList myTeams }
            .map { it[TournamentTeamsTable.tournamentId] }.toSet()
        val ids = organised + entered
        if (ids.isEmpty()) return@query emptyList()
        val counts = TournamentTeamsTable.selectAll()
            .where { (TournamentTeamsTable.tournamentId inList ids) and (TournamentTeamsTable.status eq EntryStatus.CONFIRMED) }
            .groupingBy { it[TournamentTeamsTable.tournamentId] }.eachCount()
        TournamentsTable.selectAll().where { TournamentsTable.id inList ids }.orderBy(TournamentsTable.createdAt, SortOrder.DESC).map {
            val id = it[TournamentsTable.id]
            TournamentSummary(id, it[TournamentsTable.name], it[TournamentsTable.city], it[TournamentsTable.format], counts[id] ?: 0, id in organised)
        }
    }

    override suspend fun addTeam(tournamentId: String, team: TournamentTeam): Boolean = try {
        db.query {
            TournamentTeamsTable.insert {
                it[this.tournamentId] = tournamentId
                it[teamId] = team.teamId
                it[seed] = team.seed
                it[groupNo] = team.group
                it[status] = team.status
            }
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun setTeamStatus(tournamentId: String, teamId: String, status: EntryStatus) {
        db.query {
            TournamentTeamsTable.update({ (TournamentTeamsTable.tournamentId eq tournamentId) and (TournamentTeamsTable.teamId eq teamId) }) {
                it[this.status] = status
            }
        }
    }

    override suspend fun removeTeam(tournamentId: String, teamId: String) {
        db.query {
            TournamentTeamsTable.deleteWhere { (TournamentTeamsTable.tournamentId eq tournamentId) and (TournamentTeamsTable.teamId eq teamId) }
        }
    }

    override suspend fun addOrganiser(tournamentId: String, uid: String, role: OrganiserRole) {
        db.query {
            TournamentOrganisersTable.upsert {
                it[this.tournamentId] = tournamentId
                it[this.uid] = uid
                it[this.role] = role
            }
        }
    }

    override suspend fun markStarted(tournamentId: String, teams: List<TournamentTeam>): Boolean = db.query {
        val updated = TournamentsTable.update({ (TournamentsTable.id eq tournamentId) and (TournamentsTable.started eq false) }) { it[started] = true }
        if (updated == 0) return@query false
        for (team in teams) {
            TournamentTeamsTable.update({ (TournamentTeamsTable.tournamentId eq tournamentId) and (TournamentTeamsTable.teamId eq team.teamId) }) {
                it[groupNo] = team.group
                it[seed] = team.seed
            }
        }
        // Entries nobody confirmed don't play.
        TournamentTeamsTable.deleteWhere { (TournamentTeamsTable.tournamentId eq tournamentId) and (status eq EntryStatus.PENDING) }
        true
    }
}
