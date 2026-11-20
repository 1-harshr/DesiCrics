package com.harsh.desicrics.store

import com.harsh.desicrics.db.MatchesTable
import com.harsh.desicrics.db.PlayersTable
import com.harsh.desicrics.db.TeamMembersTable
import com.harsh.desicrics.db.TeamsTable
import com.harsh.desicrics.db.TournamentOrganisersTable
import com.harsh.desicrics.db.TournamentPlayersTable
import com.harsh.desicrics.db.TournamentSquadChangesTable
import com.harsh.desicrics.db.TournamentSquadOffersTable
import com.harsh.desicrics.db.TournamentTeamsTable
import com.harsh.desicrics.db.TournamentsTable
import com.harsh.desicrics.db.query
import com.harsh.desicrics.api.EntrySettingsRequest
import com.harsh.desicrics.model.EntryPolicy
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Organiser
import com.harsh.desicrics.model.OrganiserRole
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.SquadChange
import com.harsh.desicrics.model.SquadOffer
import com.harsh.desicrics.model.TournamentPlayer
import com.harsh.desicrics.model.TournamentSummary
import com.harsh.desicrics.model.TournamentTeam
import kotlinx.serialization.json.Json
import java.util.UUID
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
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
                it[maxTeams] = tournament.maxTeams
                it[maxPlayers] = tournament.maxPlayers
                it[entryClosesAt] = tournament.entryClosesAt
                it[poolMode] = tournament.poolMode
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
        val changeRows = TournamentSquadChangesTable.selectAll().where { TournamentSquadChangesTable.tournamentId eq tournamentId }
            .orderBy(TournamentSquadChangesTable.changedAt).toList()
        val summaries = playerSummaries(entries.map { it[TournamentPlayersTable.playerId] } + changeRows.map { it[TournamentSquadChangesTable.playerId] })
        val changes = changeRows.map {
            val id = it[TournamentSquadChangesTable.playerId]
            SquadChange(id, summaries[id]?.name.orEmpty(), it[TournamentSquadChangesTable.teamId], it[TournamentSquadChangesTable.added], it[TournamentSquadChangesTable.changedAt])
        }
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
            maxTeams = row[TournamentsTable.maxTeams],
            maxPlayers = row[TournamentsTable.maxPlayers],
            entryClosesAt = row[TournamentsTable.entryClosesAt],
            poolMode = row[TournamentsTable.poolMode],
            squadChanges = changes,
            offers = TournamentSquadOffersTable.selectAll().where { TournamentSquadOffersTable.tournamentId eq tournamentId }
                .orderBy(TournamentSquadOffersTable.offeredAt)
                .map { SquadOffer(it[TournamentSquadOffersTable.playerId], it[TournamentSquadOffersTable.teamId]) },
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

    override suspend fun open(city: String, now: Long, limit: Int): List<TournamentSummary> = db.query {
        val taking = (TournamentsTable.teamEntry neq EntryPolicy.ORGANISERS) or (TournamentsTable.playerEntry neq EntryPolicy.ORGANISERS)
        val notClosed = TournamentsTable.entryClosesAt.isNull() or (TournamentsTable.entryClosesAt greater now)
        val where = (TournamentsTable.started eq false) and taking and notClosed
        val rows = TournamentsTable.selectAll()
            .where { if (city.isBlank()) where else where and (TournamentsTable.city.lowerCase() eq city.trim().lowercase()) }
            .orderBy(TournamentsTable.startsAt to SortOrder.ASC_NULLS_LAST, TournamentsTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .toList()
        val ids = rows.map { it[TournamentsTable.id] }
        val counts = if (ids.isEmpty()) emptyMap() else TournamentTeamsTable.selectAll()
            .where { (TournamentTeamsTable.tournamentId inList ids) and (TournamentTeamsTable.status eq EntryStatus.CONFIRMED) }
            .groupingBy { it[TournamentTeamsTable.tournamentId] }.eachCount()
        rows.map {
            val id = it[TournamentsTable.id]
            TournamentSummary(
                id, it[TournamentsTable.name], it[TournamentsTable.city], it[TournamentsTable.format], counts[id] ?: 0,
                startsAt = it[TournamentsTable.startsAt],
                takesTeams = it[TournamentsTable.teamEntry] != EntryPolicy.ORGANISERS,
                takesPlayers = it[TournamentsTable.playerEntry] != EntryPolicy.ORGANISERS,
            )
        }
    }

    override suspend fun browse(city: String, limit: Int): List<TournamentSummary> = db.query {
        summaries(
            TournamentsTable.selectAll()
                .apply { if (city.isNotBlank()) where { TournamentsTable.city.lowerCase() eq city.trim().lowercase() } }
                .orderBy(TournamentsTable.startsAt to SortOrder.DESC_NULLS_FIRST, TournamentsTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .toList(),
        )
    }

    override suspend fun search(query: String, limit: Int): List<TournamentSummary> = db.query {
        summaries(
            TournamentsTable.selectAll()
                .where { TournamentsTable.name.lowerCase() like "%${query.trim().lowercase().escapeLike()}%" }
                .orderBy(TournamentsTable.createdAt, SortOrder.DESC)
                .limit(limit)
                .toList(),
        )
    }

    /** Public summaries of tournament [rows]: confirmed teams, and whether each has started and finished. */
    private fun summaries(rows: List<ResultRow>): List<TournamentSummary> {
        val ids = rows.map { it[TournamentsTable.id] }
        if (ids.isEmpty()) return emptyList()
        val counts = TournamentTeamsTable.selectAll()
            .where { (TournamentTeamsTable.tournamentId inList ids) and (TournamentTeamsTable.status eq EntryStatus.CONFIRMED) }
            .groupingBy { it[TournamentTeamsTable.tournamentId] }.eachCount()
        val fixtures = MatchesTable.select(MatchesTable.tournamentId, MatchesTable.status)
            .where { MatchesTable.tournamentId inList ids }
            .groupBy({ it[MatchesTable.tournamentId]!! }, { it[MatchesTable.status] })
        return rows.map {
            val id = it[TournamentsTable.id]
            val started = it[TournamentsTable.started]
            // The next round is made as soon as one ends, so no unfinished fixture left means it's over.
            val played = fixtures[id].orEmpty()
            TournamentSummary(
                id, it[TournamentsTable.name], it[TournamentsTable.city], it[TournamentsTable.format], counts[id] ?: 0,
                startsAt = it[TournamentsTable.startsAt],
                started = started,
                finished = started && played.isNotEmpty() && played.all { status -> status == MatchStatus.COMPLETED },
            )
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
            TournamentSquadOffersTable.deleteWhere { (TournamentSquadOffersTable.tournamentId eq tournamentId) and (TournamentSquadOffersTable.teamId eq teamId) }
        }
    }

    override suspend fun setEntry(tournamentId: String, settings: EntrySettingsRequest) {
        db.query {
            TournamentsTable.update({ TournamentsTable.id eq tournamentId }) {
                it[teamEntry] = settings.teamEntry
                it[playerEntry] = settings.playerEntry
                it[maxTeams] = settings.maxTeams
                it[maxPlayers] = settings.maxPlayers
                it[entryClosesAt] = settings.entryClosesAt
                it[poolMode] = settings.poolMode
                // A new closing time gets its own reminder.
                it[entryReminded] = false
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
            // A player in a squad has no more use for other teams' offers.
            if (teamId != null) deleteOffers(tournamentId, profileId)
        }
    }

    override suspend fun removePlayer(tournamentId: String, profileId: String) {
        db.query {
            TournamentPlayersTable.deleteWhere { (TournamentPlayersTable.tournamentId eq tournamentId) and (playerId eq profileId) }
            deleteOffers(tournamentId, profileId)
        }
    }

    override suspend fun addOffer(tournamentId: String, profileId: String, teamId: String, offeredBy: String, at: Long): Boolean = try {
        db.query {
            TournamentSquadOffersTable.insert {
                it[this.tournamentId] = tournamentId
                it[playerId] = profileId
                it[this.teamId] = teamId
                it[this.offeredBy] = offeredBy
                it[offeredAt] = at
            }
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun claimReminders(from: Long, until: Long): List<String> = db.query {
        val due = (TournamentsTable.started eq false) and (TournamentsTable.entryReminded eq false) and
            (TournamentsTable.entryClosesAt greater from) and (TournamentsTable.entryClosesAt lessEq until)
        val ids = TournamentsTable.select(TournamentsTable.id).where { due }.map { it[TournamentsTable.id] }
        if (ids.isNotEmpty()) TournamentsTable.update({ TournamentsTable.id inList ids }) { it[entryReminded] = true }
        ids
    }

    override suspend fun addSquadChange(tournamentId: String, teamId: String, profileId: String, added: Boolean, by: String, at: Long) {
        db.query {
            TournamentSquadChangesTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[this.tournamentId] = tournamentId
                it[this.teamId] = teamId
                it[playerId] = profileId
                it[this.added] = added
                it[changedBy] = by
                it[changedAt] = at
            }
        }
    }

    override suspend fun removeOffer(tournamentId: String, profileId: String, teamId: String) {
        db.query {
            TournamentSquadOffersTable.deleteWhere {
                (TournamentSquadOffersTable.tournamentId eq tournamentId) and (playerId eq profileId) and (TournamentSquadOffersTable.teamId eq teamId)
            }
        }
    }

    private fun deleteOffers(tournamentId: String, profileId: String) {
        TournamentSquadOffersTable.deleteWhere { (TournamentSquadOffersTable.tournamentId eq tournamentId) and (playerId eq profileId) }
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
        // Entries nobody confirmed, or invites nobody accepted, don't play.
        TournamentTeamsTable.deleteWhere { (TournamentTeamsTable.tournamentId eq tournamentId) and (status neq EntryStatus.CONFIRMED) }
        TournamentPlayersTable.deleteWhere { (TournamentPlayersTable.tournamentId eq tournamentId) and (status neq EntryStatus.CONFIRMED) }
        true
    }
}
