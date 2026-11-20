package com.harsh.desicrics.store

import com.harsh.desicrics.db.PlayersTable
import com.harsh.desicrics.db.TeamMembersTable
import com.harsh.desicrics.db.TeamsTable
import com.harsh.desicrics.db.TournamentOrganisersTable
import com.harsh.desicrics.db.TournamentPlayersTable
import com.harsh.desicrics.db.TournamentTeamsTable
import com.harsh.desicrics.db.TournamentsTable
import com.harsh.desicrics.db.query
import com.harsh.desicrics.model.EntryPolicy
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.Organiser
import com.harsh.desicrics.model.OrganiserRole
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.TournamentPlayer
import com.harsh.desicrics.model.TournamentSummary
import com.harsh.desicrics.model.TournamentTeam
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
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
                it[teamEntry] = tournament.teamEntry
                it[playerEntry] = tournament.playerEntry
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
        val entries = TournamentPlayersTable.selectAll().where { TournamentPlayersTable.tournamentId eq tournamentId }
            .orderBy(TournamentPlayersTable.addedAt).toList()
        val summaries = playerSummaries(entries.map { it[TournamentPlayersTable.playerId] })
        val players = entries.mapNotNull {
            TournamentPlayer(
                player = summaries[it[TournamentPlayersTable.playerId]] ?: return@mapNotNull null,
                teamId = it[TournamentPlayersTable.teamId],
                status = it[TournamentPlayersTable.status],
                fromPool = it[TournamentPlayersTable.fromPool],
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
            teamEntry = row[TournamentsTable.teamEntry],
            playerEntry = row[TournamentsTable.playerEntry],
            organisers = organisers,
            teams = teams,
            players = players,
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
        val joined = TournamentPlayersTable.select(TournamentPlayersTable.tournamentId)
            .where { TournamentPlayersTable.playerId eq uid }
            .map { it[TournamentPlayersTable.tournamentId] }.toSet()
        val ids = organised + entered + joined
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
            val inSquad = (TournamentPlayersTable.tournamentId eq tournamentId) and (TournamentPlayersTable.teamId eq teamId)
            // Players who entered on their own go back to the pool; the rest came with the team.
            TournamentPlayersTable.update({ inSquad and (TournamentPlayersTable.fromPool eq true) }) { it[this.teamId] = null }
            TournamentPlayersTable.deleteWhere { inSquad }
        }
    }

    override suspend fun setEntry(tournamentId: String, teamEntry: EntryPolicy, playerEntry: EntryPolicy) {
        db.query {
            TournamentsTable.update({ TournamentsTable.id eq tournamentId }) {
                it[this.teamEntry] = teamEntry
                it[this.playerEntry] = playerEntry
            }
        }
    }

    override suspend fun addPlayer(tournamentId: String, profileId: String, teamId: String?, status: EntryStatus, fromPool: Boolean, addedBy: String, at: Long): Boolean = try {
        db.query {
            TournamentPlayersTable.insert {
                it[this.tournamentId] = tournamentId
                it[playerId] = profileId
                it[this.teamId] = teamId
                it[this.status] = status
                it[this.fromPool] = fromPool
                it[this.addedBy] = addedBy
                it[addedAt] = at
            }
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun fillSquad(tournamentId: String, teamId: String, profileIds: Collection<String>, addedBy: String, at: Long) {
        db.query {
            val existing = TournamentPlayersTable.selectAll()
                .where { (TournamentPlayersTable.tournamentId eq tournamentId) and (TournamentPlayersTable.playerId inList profileIds) }
                .associate { it[TournamentPlayersTable.playerId] to it[TournamentPlayersTable.teamId] }
            // Members waiting in the pool join their team; members already in another squad stay there.
            val pooled = existing.filterValues { it == null }.keys
            if (pooled.isNotEmpty()) {
                TournamentPlayersTable.update({
                    (TournamentPlayersTable.tournamentId eq tournamentId) and (TournamentPlayersTable.playerId inList pooled) and TournamentPlayersTable.teamId.isNull()
                }) {
                    it[this.teamId] = teamId
                    it[status] = EntryStatus.CONFIRMED
                }
            }
            for (profileId in profileIds.filter { it !in existing }) {
                TournamentPlayersTable.insert {
                    it[this.tournamentId] = tournamentId
                    it[playerId] = profileId
                    it[this.teamId] = teamId
                    it[status] = EntryStatus.CONFIRMED
                    it[fromPool] = false
                    it[this.addedBy] = addedBy
                    it[addedAt] = at
                }
            }
        }
    }

    override suspend fun setPlayer(tournamentId: String, profileId: String, teamId: String?, status: EntryStatus) {
        db.query {
            TournamentPlayersTable.update({ (TournamentPlayersTable.tournamentId eq tournamentId) and (TournamentPlayersTable.playerId eq profileId) }) {
                it[this.teamId] = teamId
                it[this.status] = status
            }
        }
    }

    override suspend fun removePlayer(tournamentId: String, profileId: String) {
        db.query {
            TournamentPlayersTable.deleteWhere { (TournamentPlayersTable.tournamentId eq tournamentId) and (playerId eq profileId) }
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
        TournamentPlayersTable.deleteWhere { (TournamentPlayersTable.tournamentId eq tournamentId) and (status eq EntryStatus.PENDING) }
        true
    }
}
