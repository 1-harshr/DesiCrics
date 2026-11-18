package com.harsh.desicrics.service

import com.harsh.desicrics.api.DirectoryRequest
import com.harsh.desicrics.api.LookingForRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.RespondRequest
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.model.DirectoryEntry
import com.harsh.desicrics.model.DirectoryKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.LookingForKind
import com.harsh.desicrics.model.LookingForPost
import com.harsh.desicrics.model.LookingForResponse
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.OverlayScore
import com.harsh.desicrics.model.PlayerRanking
import com.harsh.desicrics.model.TeamRanking
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.stats.Rankings
import com.harsh.desicrics.store.CommunityStore
import com.harsh.desicrics.store.CommunityStore.PostRow
import com.harsh.desicrics.store.CommunityStore.ResponseRow
import com.harsh.desicrics.store.MatchStore
import com.harsh.desicrics.store.PlayerStore
import com.harsh.desicrics.store.StatsStore
import com.harsh.desicrics.store.TeamStore
import com.harsh.desicrics.store.UserStore
import io.ktor.http.HttpStatusCode
import java.util.UUID

/** Phase 6: getting matches organised (looking for, directory) and city rankings. */
class CommunityService(
    private val community: CommunityStore,
    private val players: PlayerStore,
    private val teams: TeamStore,
    private val users: UserStore,
    private val matches: MatchStore,
    private val stats: StatsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // Looking for.

    suspend fun post(user: FirebasePrincipal, request: LookingForRequest): LookingForPost {
        request.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it) }
        requireProfile(user)
        request.teamId?.let { teamId ->
            val team = teams.get(teamId) ?: throw ApiException(HttpStatusCode.NotFound, "Team not found")
            if (team.roleOf(user.uid) != TeamRole.ADMIN) throw ApiException(HttpStatusCode.Forbidden, "Only a team's admins can post for it")
        }
        val row = PostRow(UUID.randomUUID().toString(), user.uid, request.kind, request.city.trim(), request.date, request.note.trim(), request.teamId, open = true)
        community.addPost(row, clock())
        return view(row, withResponses = true)
    }

    /** Open posts in a city, from the start of today on (yesterday's are history). */
    suspend fun open(city: String, kind: LookingForKind?): List<LookingForPost> {
        if (city.isBlank()) return emptyList()
        val today = clock() - clock() % DAY
        return views(community.openPosts(city, kind, today), withResponses = false)
    }

    suspend fun mine(user: FirebasePrincipal): List<LookingForPost> = views(community.postsBy(user.uid), withResponses = true)

    /** One response per person per post; the author can't answer their own. */
    suspend fun respond(user: FirebasePrincipal, postId: String, request: RespondRequest): LookingForPost {
        request.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it) }
        requireProfile(user)
        val post = community.post(postId)?.takeIf { it.open } ?: throw ApiException(HttpStatusCode.NotFound, "That post is closed")
        if (post.authorUid == user.uid) throw ApiException(HttpStatusCode.BadRequest, "That's your own post")
        if (!community.respond(postId, ResponseRow(user.uid, request.message.trim(), request.phone.trim(), clock()))) {
            throw ApiException(HttpStatusCode.Conflict, "You've already answered this one")
        }
        return view(post, withResponses = false)
    }

    suspend fun close(user: FirebasePrincipal, postId: String): LookingForPost {
        val post = community.post(postId) ?: throw ApiException(HttpStatusCode.NotFound, "Post not found")
        if (post.authorUid != user.uid) throw ApiException(HttpStatusCode.Forbidden, "Only whoever posted it can close it")
        community.close(postId)
        return view(post.copy(open = false), withResponses = true)
    }

    // Directory.

    suspend fun directory(city: String, kind: DirectoryKind?): List<DirectoryEntry> = if (city.isBlank()) emptyList() else community.entries(city, kind)

    suspend fun addEntry(user: FirebasePrincipal, request: DirectoryRequest): DirectoryEntry {
        request.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it) }
        requireProfile(user)
        val entry = DirectoryEntry(UUID.randomUUID().toString(), request.kind, request.name.trim(), request.city.trim(), request.phone.trim(), request.notes.trim(), user.uid)
        community.addEntry(entry, clock())
        return entry
    }

    suspend fun removeEntry(user: FirebasePrincipal, id: String) {
        val entry = community.entry(id) ?: throw ApiException(HttpStatusCode.NotFound, "Not found")
        if (entry.addedBy != user.uid) throw ApiException(HttpStatusCode.Forbidden, "Only whoever added it can remove it")
        community.removeEntry(id)
    }

    // Rankings.

    suspend fun teamRankings(city: String): List<TeamRanking> {
        val local = community.teamIdsInCity(city).toSet()
        if (local.isEmpty()) return emptyList()
        // Ratings come from every match between saved teams, so a visiting side's strength counts too.
        val ratings = Rankings.teams(matches.completedBetweenSavedTeams()).filterKeys { it in local }
        return ratings.entries.sortedByDescending { it.value.rating }.take(50).mapNotNull { (id, rating) ->
            teams.get(id)?.let { TeamRanking(TeamSummary(it.id, it.name, it.city, it.members.size, logoUrl = it.logoUrl), rating.rating, rating.played) }
        }
    }

    suspend fun playerRankings(city: String): List<PlayerRanking> {
        val profiles = stats.profilesInCity(city)
        if (profiles.isEmpty()) return emptyList()
        val ratings = Rankings.teams(matches.completedBetweenSavedTeams())
        val scored = stats.forProfiles(profiles).groupBy { it.profileId }
            .map { (profile, lines) -> Triple(profile, Rankings.playerScore(lines, ratings), lines.size) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(50)
        val summaries = players.summaries(scored.map { it.first })
        return scored.mapNotNull { (profile, score, count) -> summaries[profile]?.let { PlayerRanking(it, score, count) } }
    }

    // Stream overlay.

    /** The score bug: who's batting, the score, the chase or result, the batters and bowler, and this over. */
    fun overlay(details: MatchDetails): OverlayScore {
        val match = details.match
        val state = ScoringEngine.compute(match, details.deliveries, details.events)
        val innings = state.current
        fun batter(id: String?) = innings.batters.firstOrNull { it.player.id == id }?.let { "${it.player.name} ${it.runs} (${it.balls})" }
        val bowler = innings.bowlers.firstOrNull { it.player.id == innings.bowlerId }
            ?.let { "${it.player.name} ${it.wickets}-${it.runs} (${it.oversText})" }
        val detail = when {
            state.isComplete -> resultLine(match)
            innings.target != null -> "Need ${innings.runsNeeded} off ${innings.ballsRemaining} balls"
            else -> "Run rate ${"%.2f".format(innings.runRate)}"
        }
        return OverlayScore(
            title = "${match.teamA.name} vs ${match.teamB.name}",
            battingTeam = innings.battingTeam.name,
            score = innings.scoreText,
            overs = innings.oversText,
            detail = detail,
            striker = batter(innings.strikerId),
            nonStriker = batter(innings.nonStrikerId),
            bowler = bowler,
            thisOver = innings.currentOver.map { d ->
                when {
                    d.wicket != null -> "W"
                    d.extra == ExtraType.WIDE -> "wd"
                    d.extra == ExtraType.NO_BALL -> "nb"
                    d.extra != null -> "${d.extraRuns}${d.extra!!.shortName}"
                    d.runsOffBat == 0 -> "•"
                    else -> "${d.runsOffBat}"
                }
            },
        )
    }

    private fun resultLine(match: Match): String {
        val result = match.result ?: return ""
        val winner = result.winner ?: return "Match tied"
        val unit = if (result.marginUnit == MarginUnit.WICKETS) "wicket" else "run"
        return "${match.team(winner).name} won by ${result.margin} $unit${if (result.margin == 1) "" else "s"}"
    }

    private suspend fun views(rows: List<PostRow>, withResponses: Boolean): List<LookingForPost> {
        val counts = community.responseCounts(rows.map { it.id })
        val authors = players.summaries(rows.map { it.authorUid })
        return rows.mapNotNull { row ->
            val author = authors[row.authorUid] ?: return@mapNotNull null
            val team = row.teamId?.let { teams.get(it) }?.let { TeamSummary(it.id, it.name, it.city, it.members.size, logoUrl = it.logoUrl) }
            val responses = if (withResponses) responsesOf(row.id) else emptyList()
            LookingForPost(row.id, row.kind, row.city, row.date, row.note, author, team, counts[row.id] ?: 0, row.open, responses)
        }
    }

    private suspend fun view(row: PostRow, withResponses: Boolean) = views(listOf(row), withResponses).single()

    private suspend fun responsesOf(postId: String): List<LookingForResponse> {
        val rows = community.responses(postId)
        val people = players.summaries(rows.map { it.uid })
        return rows.mapNotNull { r -> people[r.uid]?.let { LookingForResponse(it, r.message, r.phone, r.createdAt) } }
    }

    private suspend fun requireProfile(user: FirebasePrincipal) {
        if (users.get(user.uid) == null) throw ApiException(HttpStatusCode.Conflict, "Set up your profile first")
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
