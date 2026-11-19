package com.harsh.desicrics.service

import com.harsh.desicrics.model.TeamInsights
import com.harsh.desicrics.stats.TeamInsightsEngine
import com.harsh.desicrics.api.AddMemberRequest
import com.harsh.desicrics.api.ClaimRequest
import com.harsh.desicrics.api.SupportRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.model.HeadToHead
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.NotificationKind
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.store.MatchStore
import com.harsh.desicrics.store.PlayerStore
import com.harsh.desicrics.store.SupportStore
import com.harsh.desicrics.store.TeamStore
import com.harsh.desicrics.store.UserStore
import io.ktor.http.HttpStatusCode
import java.util.UUID

/** Short codes people read out or type: no 0/O or 1/I to mix up. */
private val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

internal fun newCode(length: Int = 6): String = (1..length).map { CODE_ALPHABET.random() }.joinToString("")

/** A public code for a player or team: 8 digits, easy to read out and type on a number pad. */
internal fun newPublicCode(): String = (1..8).map { ('0'..'9').random() }.joinToString("")

/** [query] as a public code, if it is one: 8 digits, ignoring spaces and dashes. */
internal fun publicCodeIn(query: String): String? =
    query.filterNot { it == ' ' || it == '-' }.takeIf { it.length == 8 && it.all(Char::isDigit) }

/** Tries fresh codes until [save] accepts one; codes are random, so a clash is rare and retrying is enough. */
internal suspend fun <T> withFreshCode(save: suspend (code: String) -> T?): T {
    repeat(5) { save(newCode())?.let { return it } }
    throw ApiException(HttpStatusCode.ServiceUnavailable, "Couldn't make a code. Try again")
}

/** Saved teams: who's in them, who runs them, and how people join. */
/** How many recent matches team insights look at. */
const val INSIGHT_MATCHES = 50

/** Teams per page when browsing them. */
const val TEAM_PAGE = 30

class TeamService(
    private val teams: TeamStore,
    private val players: PlayerStore,
    private val matches: MatchStore,
    private val users: UserStore,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Tells [uids] about a join request or acceptance; see [com.harsh.desicrics.live.Notifier.teamNotice]. */
    private val notify: suspend (uids: Set<String>, kind: NotificationKind, teamId: String, teamName: String, playerName: String) -> Unit =
        { _, _, _, _, _ -> },
) {
    suspend fun mine(user: FirebasePrincipal): List<TeamSummary> = teams.forPlayer(user.uid)

    /** Every team, or those matching [query] by name or code, a page of [TEAM_PAGE] at a time. */
    suspend fun browse(query: String, offset: Long): List<TeamSummary> =
        teams.search(query.trim().take(60), TEAM_PAGE, offset.coerceAtLeast(0))

    suspend fun create(user: FirebasePrincipal, request: TeamRequest): SavedTeam {
        request.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it.message) }
        requireProfile(user)
        val id = UUID.randomUUID().toString()
        withFreshCode { code ->
            val team = SavedTeam(id, request.name.trim(), request.city.trim(), user.uid, members = emptyList(), joinCode = code, code = newPublicCode())
            teams.create(team, adminId = user.uid, at = clock(), lineup = request.lineup).takeIf { it }
        }
        return get(user, id)
    }

    /**
     * Admins see the join code, placeholders' claim codes and who's asking to join; everyone else doesn't.
     * Someone outside the team sees whether they've asked.
     */
    suspend fun get(user: FirebasePrincipal, teamId: String): SavedTeam {
        val team = teams.get(teamId) ?: throw ApiException(HttpStatusCode.NotFound, "Team not found")
        val lastSquad = matches.lastSquad(teamId)
        return when (team.roleOf(user.uid)) {
            TeamRole.ADMIN -> {
                val asking = teams.joinRequests(teamId)
                val summaries = players.summaries(asking)
                team.copy(lastSquad = lastSquad, joinRequests = asking.mapNotNull { summaries[it] })
            }
            else -> team.copy(
                joinCode = null,
                members = team.members.map { it.copy(claimCode = null) },
                lastSquad = lastSquad,
                requested = team.roleOf(user.uid) == null && teams.hasJoinRequest(teamId, user.uid),
            )
        }
    }

    suspend fun update(user: FirebasePrincipal, teamId: String, request: TeamRequest): SavedTeam {
        request.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it.message) }
        requireAdmin(user, teamId)
        teams.rename(teamId, request.name.trim(), request.city.trim())
        return get(user, teamId)
    }

    suspend fun addMember(user: FirebasePrincipal, teamId: String, request: AddMemberRequest): SavedTeam {
        request.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it.message) }
        requireAdmin(user, teamId)
        val profileId = request.profileId ?: withFreshCode { code ->
            val id = "p-" + UUID.randomUUID()
            id.takeIf { players.createPlaceholder(id, request.placeholderName!!.trim(), request.placeholderPhone.orEmpty(), code, user.uid, clock()) }
        }
        if (players.summaries(listOf(profileId)).isEmpty()) throw ApiException(HttpStatusCode.NotFound, "Player not found")
        if (!teams.addMember(teamId, profileId, TeamRole.MEMBER, clock())) {
            throw ApiException(HttpStatusCode.Conflict, "They're already in the team")
        }
        return get(user, teamId)
    }

    suspend fun setRole(user: FirebasePrincipal, teamId: String, profileId: String, role: TeamRole): SavedTeam {
        val team = requireAdmin(user, teamId)
        if (team.roleOf(profileId) == null) throw ApiException(HttpStatusCode.NotFound, "They're not in the team")
        if (role == TeamRole.MEMBER && team.isLastAdmin(profileId)) {
            throw ApiException(HttpStatusCode.Conflict, "Make someone else an admin first")
        }
        teams.setRole(teamId, profileId, role)
        return get(user, teamId)
    }

    /** Admins remove anyone; members can remove themselves (leave). */
    suspend fun removeMember(user: FirebasePrincipal, teamId: String, profileId: String): SavedTeam {
        val team = teams.get(teamId) ?: throw ApiException(HttpStatusCode.NotFound, "Team not found")
        val leaving = profileId == user.uid
        if (!leaving && team.roleOf(user.uid) != TeamRole.ADMIN) throw ApiException(HttpStatusCode.Forbidden, "Only team admins can do that")
        if (team.roleOf(profileId) == null) throw ApiException(HttpStatusCode.NotFound, "They're not in the team")
        if (team.isLastAdmin(profileId) && team.members.size > 1) {
            throw ApiException(HttpStatusCode.Conflict, "Make someone else an admin first")
        }
        teams.removeMember(teamId, profileId)
        return get(user, teamId)
    }

    suspend fun newJoinCode(user: FirebasePrincipal, teamId: String): SavedTeam {
        requireAdmin(user, teamId)
        withFreshCode { code -> teams.setJoinCode(teamId, code).takeIf { it } }
        return get(user, teamId)
    }

    /** Joining twice is harmless: it just returns the team. */
    suspend fun join(user: FirebasePrincipal, code: String): SavedTeam {
        requireProfile(user)
        val teamId = teams.byJoinCode(code.trim()) ?: throw ApiException(HttpStatusCode.NotFound, "That team code is wrong")
        teams.addMember(teamId, user.uid, TeamRole.MEMBER, clock())
        teams.deleteJoinRequest(teamId, user.uid)
        return get(user, teamId)
    }

    /**
     * For a player who found the team by name but has no code: the admins are told and decide. Asking
     * twice, or as a member, is harmless.
     */
    suspend fun requestToJoin(user: FirebasePrincipal, teamId: String): SavedTeam {
        requireProfile(user)
        val team = teams.get(teamId) ?: throw ApiException(HttpStatusCode.NotFound, "Team not found")
        if (team.roleOf(user.uid) == null && teams.addJoinRequest(teamId, user.uid, clock())) {
            val name = players.summaries(listOf(user.uid))[user.uid]?.name.orEmpty()
            val admins = team.members.filter { it.role == TeamRole.ADMIN }.map { it.player.profileId }.toSet()
            notify(admins, NotificationKind.TEAM_JOIN_REQUEST, teamId, team.name, name)
        }
        return get(user, teamId)
    }

    suspend fun cancelJoinRequest(user: FirebasePrincipal, teamId: String): SavedTeam {
        teams.deleteJoinRequest(teamId, user.uid)
        return get(user, teamId)
    }

    /** An admin lets [profileId] in, and they're told. */
    suspend fun acceptJoinRequest(user: FirebasePrincipal, teamId: String, profileId: String): SavedTeam {
        val team = requireAdmin(user, teamId)
        if (!teams.deleteJoinRequest(teamId, profileId)) throw ApiException(HttpStatusCode.NotFound, "They're not asking to join")
        teams.addMember(teamId, profileId, TeamRole.MEMBER, clock())
        val name = players.summaries(listOf(profileId))[profileId]?.name.orEmpty()
        notify(setOf(profileId), NotificationKind.TEAM_JOIN_ACCEPTED, teamId, team.name, name)
        return get(user, teamId)
    }

    /** Quietly: the player isn't told, and can ask again. */
    suspend fun declineJoinRequest(user: FirebasePrincipal, teamId: String, profileId: String): SavedTeam {
        requireAdmin(user, teamId)
        teams.deleteJoinRequest(teamId, profileId)
        return get(user, teamId)
    }

    /** Completed matches between two saved teams, from [teamId]'s side. */
    suspend fun headToHead(teamId: String, otherId: String): HeadToHead {
        val played = matches.between(teamId, otherId).filter { it.status == MatchStatus.COMPLETED && it.result != null }
        fun sideOf(match: com.harsh.desicrics.model.Match) = if (match.teamA.teamId == teamId) TeamSide.A else TeamSide.B
        return HeadToHead(
            teamId = teamId,
            opponentId = otherId,
            played = played.size,
            won = played.count { it.result!!.winner == sideOf(it) },
            lost = played.count { it.result!!.winner == sideOf(it).other },
            tied = played.count { it.result!!.isTie },
        )
    }

    /**
     * The organiser view of a team's last [INSIGHT_MATCHES] completed matches, for its admins. Free for
     * now: when the organiser plan has a price, this is where it's checked.
     */
    suspend fun insights(user: FirebasePrincipal, teamId: String): TeamInsights {
        requireAdmin(user, teamId)
        return TeamInsightsEngine.compute(teamId, matches.completedForTeam(teamId, INSIGHT_MATCHES))
    }

    private suspend fun requireProfile(user: FirebasePrincipal) {
        if (users.get(user.uid) == null) throw ApiException(HttpStatusCode.Conflict, "Set up your profile first")
    }

    private suspend fun requireAdmin(user: FirebasePrincipal, teamId: String): SavedTeam {
        val team = teams.get(teamId) ?: throw ApiException(HttpStatusCode.NotFound, "Team not found")
        if (team.roleOf(user.uid) != TeamRole.ADMIN) throw ApiException(HttpStatusCode.Forbidden, "Only team admins can do that")
        return team
    }

    private fun SavedTeam.isLastAdmin(profileId: String) =
        roleOf(profileId) == TeamRole.ADMIN && members.count { it.role == TeamRole.ADMIN } == 1
}

/** Finding players, and claiming the placeholder someone made for you. */
class PlayerService(
    private val players: PlayerStore,
    private val users: UserStore,
) {
    suspend fun placeholder(user: FirebasePrincipal, request: AddMemberRequest): PlayerSummary {
        val name = request.placeholderName?.trim()
        val phone = request.placeholderPhone.orEmpty().trim()
        if (request.validate() != null || name.isNullOrEmpty() || (!phone.matches(Regex("[+]?[0-9 ]{10,18}")) || phone.count { it.isDigit() } !in 10..15))
            throw ApiException(HttpStatusCode.BadRequest, "Enter a name and phone number")
        val id = withFreshCode { code ->
            val id = "p-" + UUID.randomUUID()
            id.takeIf { players.createPlaceholder(id, name, phone, code, user.uid, System.currentTimeMillis()) }
        }
        return PlayerSummary(id, name, isPlaceholder = true)
    }

    suspend fun search(query: String): List<PlayerSummary> =
        if (query.trim().length < 2) emptyList() else players.search(query, limit = 20)

    /** Placeholders created with the caller's phone number, as verified by their sign-in. */
    suspend fun claimable(user: FirebasePrincipal): List<PlayerSummary> =
        user.phone?.let { players.placeholdersWithPhone(it) }.orEmpty()

    suspend fun claim(user: FirebasePrincipal, request: ClaimRequest): PlayerSummary {
        if (users.get(user.uid) == null) throw ApiException(HttpStatusCode.Conflict, "Set up your profile first")
        val code = request.code
        val profileId = request.profileId
        val placeholder = when {
            code != null -> players.placeholderByCode(code.trim())
            profileId != null -> claimable(user).firstOrNull { it.profileId == profileId }
            else -> null
        } ?: throw ApiException(HttpStatusCode.NotFound, "That claim code is wrong or already used")
        if (!players.merge(placeholder.profileId, user.uid)) {
            throw ApiException(HttpStatusCode.Conflict, "Someone already claimed that player")
        }
        return players.summaries(listOf(user.uid)).getValue(user.uid)
    }
}

/** The account itself: support messages and deletion. */
class AccountService(
    private val players: PlayerStore,
    private val teams: TeamStore,
    private val support: SupportStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Clears the caller's personal data. Teams they ran alone get a new admin (whoever joined next), so no
     * team is left without one. Scorecards keep their rows under "Former player".
     */
    suspend fun delete(user: FirebasePrincipal) {
        for (teamId in teams.teamIdsOf(user.uid)) {
            val team = teams.get(teamId) ?: continue
            val admins = team.members.filter { it.role == TeamRole.ADMIN }
            if (admins.singleOrNull()?.player?.profileId == user.uid) {
                team.members.firstOrNull { it.player.profileId != user.uid && !it.player.isPlaceholder }
                    ?.let { teams.setRole(teamId, it.player.profileId, TeamRole.ADMIN) }
            }
        }
        players.anonymise(user.uid, clock())
    }

    suspend fun contactSupport(user: FirebasePrincipal, request: SupportRequest) {
        request.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it) }
        support.save(UUID.randomUUID().toString(), user.uid, request, clock())
    }
}
