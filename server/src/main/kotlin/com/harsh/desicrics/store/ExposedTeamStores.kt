package com.harsh.desicrics.store

import com.harsh.desicrics.api.SupportRequest
import com.harsh.desicrics.db.AuctionEventsTable
import com.harsh.desicrics.db.AuctionPlayersTable
import com.harsh.desicrics.db.MatchPlayersTable
import com.harsh.desicrics.db.PlayerMatchLinesTable
import com.harsh.desicrics.db.PlayersTable
import com.harsh.desicrics.db.SupportRequestsTable
import com.harsh.desicrics.db.TeamJoinRequestsTable
import com.harsh.desicrics.db.TeamMembersTable
import com.harsh.desicrics.db.TeamsTable
import com.harsh.desicrics.db.UsersTable
import com.harsh.desicrics.db.query
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamMember
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.model.UserProfile
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

/** The last 10 digits, so "+91 98765 43210" and "09876543210" match. */
internal fun normalisePhone(phone: String): String = phone.filter { it.isDigit() }.takeLast(10)

class ExposedPlayerStore(private val db: Database) : PlayerStore {

    override suspend fun syncUser(profile: UserProfile) {
        db.query {
            PlayersTable.upsert {
                it[id] = profile.uid
                it[userUid] = profile.uid
                it[name] = profile.name
                it[phone] = normalisePhone(profile.phone)
                it[claimCode] = null
                it[createdBy] = profile.uid
                it[createdAt] = profile.createdAt
            }
        }
    }

    override suspend fun createPlaceholder(id: String, name: String, phone: String, claimCode: String, createdBy: String, at: Long): Boolean =
        try {
            db.query {
                PlayersTable.insert {
                    it[this.id] = id
                    it[userUid] = null
                    it[this.name] = name
                    it[this.phone] = normalisePhone(phone)
                    it[this.claimCode] = claimCode
                    it[this.createdBy] = createdBy
                    it[createdAt] = at
                }
            }
            true
        } catch (e: Exception) {
            if (e.isUniqueViolation()) false else throw e
        }

    override suspend fun summaries(ids: Collection<String>): Map<String, PlayerSummary> =
        if (ids.isEmpty()) emptyMap() else db.query { playerSummaries(ids) }

    override suspend fun search(query: String, limit: Int): List<PlayerSummary> = db.query {
        val text = query.trim().lowercase()
        val byName = PlayersTable.name.lowerCase() like "%${text.escapeLike()}%"
        val byCode = com.harsh.desicrics.service.publicCodeIn(query)?.let { UsersTable.code eq it }
        PlayersTable.join(UsersTable, JoinType.INNER, PlayersTable.userUid, UsersTable.uid)
            .selectAll()
            .where { PlayersTable.userUid.isNotNull() and UsersTable.deletedAt.isNull() and (if (byCode != null) byName or byCode else byName) }
            .orderBy(PlayersTable.name)
            .limit(limit)
            .map { it.toSummary() }
    }

    override suspend fun placeholdersWithPhone(phone: String): List<PlayerSummary> {
        val digits = normalisePhone(phone)
        if (digits.length < 10) return emptyList()
        return db.query {
            PlayersTable.selectAll()
                .where { PlayersTable.userUid.isNull() and PlayersTable.claimCode.isNotNull() and (PlayersTable.phone eq digits) }
                .map { it.toPlaceholder() }
        }
    }

    override suspend fun placeholderByCode(code: String): PlayerSummary? = db.query {
        PlayersTable.selectAll()
            .where { (PlayersTable.claimCode eq code.uppercase()) and PlayersTable.userUid.isNull() }
            .singleOrNull()?.toPlaceholder()
    }

    override suspend fun claimCodes(ids: Collection<String>): Map<String, String> = if (ids.isEmpty()) emptyMap() else db.query {
        PlayersTable.select(PlayersTable.id, PlayersTable.claimCode)
            .where { (PlayersTable.id inList ids) and PlayersTable.claimCode.isNotNull() }
            .associate { it[PlayersTable.id] to it[PlayersTable.claimCode]!! }
    }

    override suspend fun merge(placeholderId: String, userId: String): Boolean = db.query {
        val placeholder = PlayersTable.selectAll()
            .where { (PlayersTable.id eq placeholderId) and PlayersTable.userUid.isNull() and PlayersTable.claimCode.isNotNull() }
            .singleOrNull() ?: return@query false
        val userTeams = TeamMembersTable.select(TeamMembersTable.teamId)
            .where { TeamMembersTable.playerId eq userId }
            .map { it[TeamMembersTable.teamId] }.toSet()
        for (row in TeamMembersTable.selectAll().where { TeamMembersTable.playerId eq placeholderId }.toList()) {
            val team = row[TeamMembersTable.teamId]
            if (team in userTeams) {
                // Already a member themselves: keep the higher role.
                if (row[TeamMembersTable.role] == TeamRole.ADMIN) {
                    TeamMembersTable.update({ (TeamMembersTable.teamId eq team) and (TeamMembersTable.playerId eq userId) }) {
                        it[role] = TeamRole.ADMIN
                    }
                }
                TeamMembersTable.deleteWhere { (TeamMembersTable.teamId eq team) and (TeamMembersTable.playerId eq placeholderId) }
            } else {
                TeamMembersTable.update({ (TeamMembersTable.teamId eq team) and (TeamMembersTable.playerId eq placeholderId) }) {
                    it[playerId] = userId
                }
            }
        }
        MatchPlayersTable.update({ MatchPlayersTable.profileId eq placeholderId }) { it[profileId] = userId }
        // Their stats come too. A user and their own placeholder in one match can't happen: squads pick each person once.
        PlayerMatchLinesTable.update({ PlayerMatchLinesTable.profileId eq placeholderId }) { it[profileId] = userId }
        mergeAuctions(placeholderId, userId)
        PlayersTable.deleteWhere { PlayersTable.id eq placeholder[PlayersTable.id] }
        true
    }

    /** Auction pools and logs follow the player too; a user already in the same pool keeps their own entry. */
    private fun JdbcTransaction.mergeAuctions(placeholderId: String, userId: String) {
        val userAuctions = AuctionPlayersTable.select(AuctionPlayersTable.auctionId)
            .where { AuctionPlayersTable.playerId eq userId }
            .map { it[AuctionPlayersTable.auctionId] }.toSet()
        for (auction in AuctionPlayersTable.select(AuctionPlayersTable.auctionId).where { AuctionPlayersTable.playerId eq placeholderId }.map { it[AuctionPlayersTable.auctionId] }) {
            val mine = (AuctionPlayersTable.auctionId eq auction) and (AuctionPlayersTable.playerId eq placeholderId)
            if (auction in userAuctions) AuctionPlayersTable.deleteWhere { mine } else AuctionPlayersTable.update({ mine }) { it[playerId] = userId }
        }
        // Events are JSON; placeholder IDs ("p-" + a UUID) can't appear by accident.
        for (row in AuctionEventsTable.selectAll().where { AuctionEventsTable.data like "%$placeholderId%" }.toList()) {
            AuctionEventsTable.update({ (AuctionEventsTable.auctionId eq row[AuctionEventsTable.auctionId]) and (AuctionEventsTable.seq eq row[AuctionEventsTable.seq]) }) {
                it[data] = row[AuctionEventsTable.data].replace(placeholderId, userId)
            }
        }
    }

    override suspend fun anonymise(uid: String, at: Long) {
        db.query {
            UsersTable.update({ UsersTable.uid eq uid }) {
                it[name] = DELETED_NAME
                it[phone] = ""
                it[email] = null
                it[photoUrl] = null
                it[city] = ""
                it[deletedAt] = at
            }
            // Old scorecards keep pointing at the row, now nobody's and unclaimable.
            PlayersTable.update({ PlayersTable.id eq uid }) {
                it[userUid] = null
                it[name] = DELETED_NAME
                it[phone] = ""
                it[claimCode] = null
            }
            TeamMembersTable.deleteWhere { TeamMembersTable.playerId eq uid }
            TeamJoinRequestsTable.deleteWhere { TeamJoinRequestsTable.playerId eq uid }
        }
    }

    private companion object {
        const val DELETED_NAME = "Former player"
    }
}

/** Placeholders and users alike, with a user's city and role from their profile. */
internal fun JdbcTransaction.playerSummaries(ids: Collection<String>): Map<String, PlayerSummary> =
    PlayersTable.join(UsersTable, JoinType.LEFT, PlayersTable.userUid, UsersTable.uid)
        .selectAll()
        .where { PlayersTable.id inList ids }
        .associate { it[PlayersTable.id] to it.toSummary() }

private fun ResultRow.toSummary(): PlayerSummary {
    val isUser = this[PlayersTable.userUid] != null
    return PlayerSummary(
        profileId = this[PlayersTable.id],
        name = this[PlayersTable.name],
        city = if (isUser) this[UsersTable.city] else "",
        role = if (isUser) this[UsersTable.role] else null,
        isPlaceholder = this[PlayersTable.claimCode] != null,
        photoUrl = if (isUser) this[UsersTable.photoUrl] else null,
        code = if (isUser) this[UsersTable.code] else null,
    )
}

private fun ResultRow.toPlaceholder() = PlayerSummary(
    profileId = this[PlayersTable.id],
    name = this[PlayersTable.name],
    isPlaceholder = true,
)

internal fun String.escapeLike() = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

class ExposedTeamStore(private val db: Database) : TeamStore {

    override suspend fun create(team: SavedTeam, adminId: String, at: Long, lineup: List<com.harsh.desicrics.api.SquadPlayer>): Boolean = try {
        db.query {
            TeamsTable.insert {
                it[id] = team.id
                it[name] = team.name
                it[city] = team.city
                it[joinCode] = team.joinCode!!
                it[code] = team.code
                it[createdBy] = team.createdBy
                it[createdAt] = at
            }
            for (member in lineup.distinctBy { it.profileId ?: it.name }) {
                val pid = member.profileId ?: ("p-" + java.util.UUID.randomUUID()).also { generated ->
                    PlayersTable.insert {
                        it[id] = generated
                        it[name] = member.name
                        it[phone] = ""
                        it[claimCode] = com.harsh.desicrics.service.newCode()
                        it[createdBy] = adminId
                        it[createdAt] = at
                    }
                }
                if (pid != adminId) TeamMembersTable.insert {
                    it[teamId] = team.id
                    it[playerId] = pid
                    it[role] = TeamRole.MEMBER
                    it[joinedAt] = at
                }
            }
            TeamMembersTable.insert {
                it[teamId] = team.id
                it[playerId] = adminId
                it[role] = TeamRole.ADMIN
                it[joinedAt] = at
            }
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun get(teamId: String): SavedTeam? = db.query {
        val row = TeamsTable.selectAll().where { TeamsTable.id eq teamId }.singleOrNull() ?: return@query null
        val members = TeamMembersTable.selectAll()
            .where { TeamMembersTable.teamId eq teamId }
            .orderBy(TeamMembersTable.joinedAt)
            .toList()
        val summaries = playerSummaries(members.map { it[TeamMembersTable.playerId] })
        val codes = PlayersTable.select(PlayersTable.id, PlayersTable.claimCode)
            .where { (PlayersTable.id inList members.map { it[TeamMembersTable.playerId] }) and PlayersTable.claimCode.isNotNull() }
            .associate { it[PlayersTable.id] to it[PlayersTable.claimCode] }
        SavedTeam(
            id = row[TeamsTable.id],
            name = row[TeamsTable.name],
            city = row[TeamsTable.city],
            createdBy = row[TeamsTable.createdBy],
            members = members.mapNotNull { member ->
                val id = member[TeamMembersTable.playerId]
                summaries[id]?.let { TeamMember(it, member[TeamMembersTable.role], codes[id]) }
            },
            joinCode = row[TeamsTable.joinCode],
            code = row[TeamsTable.code],
            logoUrl = row[TeamsTable.logoUrl],
        )
    }

    override suspend fun forPlayer(profileId: String): List<TeamSummary> = db.query {
        val mine = TeamMembersTable.selectAll().where { TeamMembersTable.playerId eq profileId }.associate {
            it[TeamMembersTable.teamId] to it[TeamMembersTable.role]
        }
        if (mine.isEmpty()) return@query emptyList()
        val count = TeamMembersTable.playerId.count()
        val sizes = TeamMembersTable.select(TeamMembersTable.teamId, count)
            .where { TeamMembersTable.teamId inList mine.keys }
            .groupBy(TeamMembersTable.teamId)
            .associate { it[TeamMembersTable.teamId] to it[count].toInt() }
        TeamsTable.selectAll().where { TeamsTable.id inList mine.keys }.orderBy(TeamsTable.name).map {
            val id = it[TeamsTable.id]
            TeamSummary(id, it[TeamsTable.name], it[TeamsTable.city], sizes[id] ?: 0, mine[id], it[TeamsTable.logoUrl], it[TeamsTable.code])
        }
    }

    override suspend fun rename(teamId: String, name: String, city: String) {
        db.query {
            TeamsTable.update({ TeamsTable.id eq teamId }) {
                it[this.name] = name
                it[this.city] = city
            }
        }
    }

    override suspend fun addMember(teamId: String, profileId: String, role: TeamRole, at: Long): Boolean = try {
        db.query {
            TeamMembersTable.insert {
                it[this.teamId] = teamId
                it[playerId] = profileId
                it[this.role] = role
                it[joinedAt] = at
            }
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun setRole(teamId: String, profileId: String, role: TeamRole) {
        db.query {
            TeamMembersTable.update({ (TeamMembersTable.teamId eq teamId) and (TeamMembersTable.playerId eq profileId) }) {
                it[this.role] = role
            }
        }
    }

    override suspend fun removeMember(teamId: String, profileId: String) {
        db.query { TeamMembersTable.deleteWhere { (TeamMembersTable.teamId eq teamId) and (TeamMembersTable.playerId eq profileId) } }
    }

    override suspend fun byJoinCode(code: String): String? = db.query {
        TeamsTable.select(TeamsTable.id).where { TeamsTable.joinCode eq code.uppercase() }.singleOrNull()?.get(TeamsTable.id)
    }

    override suspend fun addJoinRequest(teamId: String, profileId: String, at: Long): Boolean = try {
        db.query {
            TeamJoinRequestsTable.insert {
                it[this.teamId] = teamId
                it[playerId] = profileId
                it[requestedAt] = at
            }
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun joinRequests(teamId: String): List<String> = db.query {
        TeamJoinRequestsTable.select(TeamJoinRequestsTable.playerId)
            .where { TeamJoinRequestsTable.teamId eq teamId }
            .orderBy(TeamJoinRequestsTable.requestedAt)
            .map { it[TeamJoinRequestsTable.playerId] }
    }

    override suspend fun hasJoinRequest(teamId: String, profileId: String): Boolean = db.query {
        TeamJoinRequestsTable.selectAll()
            .where { (TeamJoinRequestsTable.teamId eq teamId) and (TeamJoinRequestsTable.playerId eq profileId) }
            .any()
    }

    override suspend fun deleteJoinRequest(teamId: String, profileId: String): Boolean = db.query {
        TeamJoinRequestsTable.deleteWhere { (TeamJoinRequestsTable.teamId eq teamId) and (TeamJoinRequestsTable.playerId eq profileId) } > 0
    }

    override suspend fun setJoinCode(teamId: String, code: String): Boolean = try {
        db.query { TeamsTable.update({ TeamsTable.id eq teamId }) { it[joinCode] = code } }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun search(query: String, limit: Int, offset: Long): List<TeamSummary> = db.query {
        val pattern = "%${query.trim().lowercase().escapeLike()}%"
        val byCode = com.harsh.desicrics.service.publicCodeIn(query)?.let { TeamsTable.code eq it }
        val byName = TeamsTable.name.lowerCase() like pattern
        // The id breaks ties between teams of the same name, so pages don't overlap.
        val rows = TeamsTable.selectAll().where { if (byCode != null) byName or byCode else byName }
            .orderBy(TeamsTable.name to SortOrder.ASC, TeamsTable.id to SortOrder.ASC).limit(limit).offset(offset).toList()
        val count = TeamMembersTable.playerId.count()
        val sizes = TeamMembersTable.select(TeamMembersTable.teamId, count)
            .where { TeamMembersTable.teamId inList rows.map { it[TeamsTable.id] } }
            .groupBy(TeamMembersTable.teamId)
            .associate { it[TeamMembersTable.teamId] to it[count].toInt() }
        rows.map { TeamSummary(it[TeamsTable.id], it[TeamsTable.name], it[TeamsTable.city], sizes[it[TeamsTable.id]] ?: 0, logoUrl = it[TeamsTable.logoUrl], code = it[TeamsTable.code]) }
    }

    override suspend fun setLogo(teamId: String, url: String?) {
        db.query { TeamsTable.update({ TeamsTable.id eq teamId }) { it[logoUrl] = url } }
    }

    override suspend fun teamIdsOf(profileId: String): List<String> = db.query {
        TeamMembersTable.select(TeamMembersTable.teamId).where { TeamMembersTable.playerId eq profileId }.map { it[TeamMembersTable.teamId] }
    }
}

class ExposedSupportStore(private val db: Database) : SupportStore {
    override suspend fun save(id: String, uid: String, request: SupportRequest, at: Long) {
        db.query {
            SupportRequestsTable.insert {
                it[this.id] = id
                it[this.uid] = uid
                it[category] = request.category
                it[message] = request.message.trim()
                it[appVersion] = request.appVersion.take(40)
                it[createdAt] = at
            }
        }
    }
}
