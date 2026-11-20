package com.harsh.desicrics.live

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Notification
import com.harsh.desicrics.model.NotificationKind
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.store.SocialStore
import org.slf4j.LoggerFactory
import java.util.UUID

/** Sends push messages to devices; each call returns the tokens the push service says are gone, so they can be dropped. */
interface PushSender {
    /** A message the phone shows as a notification. */
    suspend fun send(tokens: List<String>, title: String, body: String, data: Map<String, String>): List<String>

    /** A silent message the app handles itself, e.g. to update a live score it's already showing. */
    suspend fun sendData(tokens: List<String>, data: Map<String, String>): List<String>
}

/** Used when no push credentials are configured: the inbox still fills, nothing is pushed. */
object LoggingPushSender : PushSender {
    private val log = LoggerFactory.getLogger(LoggingPushSender::class.java)

    override suspend fun send(tokens: List<String>, title: String, body: String, data: Map<String, String>): List<String> {
        log.info("Push (not configured) to {} devices: {} - {}", tokens.size, title, body)
        return emptyList()
    }

    override suspend fun sendData(tokens: List<String>, data: Map<String, String>): List<String> = emptyList()
}

/**
 * Tells followers about the matches they care about: when one starts, when it ends, and when a followed
 * player passes 50 or 100 runs or takes 5 wickets. Each goes in the inbox and, where a device is
 * registered, out as a push. The scorer isn't told about their own match.
 */
class Notifier(
    private val social: SocialStore,
    private val push: PushSender,
    private val clock: () -> Long = System::currentTimeMillis,
) : MatchListener {

    override suspend fun changed(before: MatchDetails?, after: MatchDetails) {
        val match = after.match
        val title = "${match.teamA.name} vs ${match.teamB.name}"
        val matchFollowers = followersOf(match) - match.scorerUid
        // A fixture starts when its squads and toss are in, not when the tournament schedules it.
        val started = (before == null && match.status != MatchStatus.SCHEDULED) ||
            (before?.match?.status == MatchStatus.SCHEDULED && match.status == MatchStatus.IN_PROGRESS)
        when {
            started -> notify(matchFollowers, NotificationKind.MATCH_STARTED, match, title, message = "$title has started")
            before != null && before.match.status != MatchStatus.COMPLETED && match.status == MatchStatus.COMPLETED ->
                notify(matchFollowers, NotificationKind.MATCH_RESULT, match, title, detail = match.scoreSummary, message = "$title: ${match.scoreSummary}")
        }
        if (before != null) milestones(before, after, title)
        liveProgress(before, after, matchFollowers)
    }

    /**
     * Keeps followers' live score notification current: an update at the end of each over and at each
     * wicket (not every ball, to go easy on batteries), and an "ended" message when the match is over.
     * Silent data messages, not inbox entries; the app shows one quiet notification per match.
     */
    private suspend fun liveProgress(before: MatchDetails?, after: MatchDetails, followers: Set<String>) {
        val match = after.match
        val ended = before?.match?.status == MatchStatus.IN_PROGRESS && match.status == MatchStatus.COMPLETED
        val ball = after.deliveries.lastOrNull()?.takeIf { after.deliveries.size > (before?.deliveries?.size ?: 0) }
        val overDone = ball != null && ball.isLegal && (match.innings.lastOrNull()?.legalBalls ?: 0) % 6 == 0
        if (!ended && (match.status != MatchStatus.IN_PROGRESS || ball == null || (!overDone && ball.wicket == null))) return
        val tokens = social.pushTokens(followers).values.flatten()
        if (tokens.isEmpty()) return
        val data = mapOf(
            "type" to if (ended) "live_ended" else "live",
            "matchId" to match.id,
            "title" to "${match.teamA.name} vs ${match.teamB.name}",
            "score" to match.scoreSummary,
        )
        runCatching { push.sendData(tokens, data) }
            .onSuccess { gone -> social.deletePushTokens(gone) }
            .onFailure { log.warn("Live push failed", it) }
    }

    private suspend fun milestones(before: MatchDetails, after: MatchDetails, title: String) {
        val was = ScoringEngine.compute(before.match, before.deliveries, before.events)
        val now = ScoringEngine.compute(after.match, after.deliveries, after.events)
        fun profileOf(playerId: String) = (after.match.teamA.players + after.match.teamB.players).firstOrNull { it.id == playerId }?.profileId
        for (innings in now.innings) {
            val earlier = was.innings.firstOrNull { it.number == innings.number }
            for (card in innings.batters) {
                val previous = earlier?.batters?.firstOrNull { it.player.id == card.player.id }?.runs ?: 0
                val reached = RUN_MILESTONES.lastOrNull { previous < it && card.runs >= it } ?: continue
                val profile = profileOf(card.player.id) ?: continue
                val followers = social.followers(FollowType.PLAYER, listOf(profile)) - after.match.scorerUid
                notify(followers, NotificationKind.MILESTONE_RUNS, after.match, title, card.player.name, reached, message = "${card.player.name} reached $reached in $title")
            }
            for (card in innings.bowlers) {
                val previous = earlier?.bowlers?.firstOrNull { it.player.id == card.player.id }?.wickets ?: 0
                if (previous >= WICKET_MILESTONE || card.wickets < WICKET_MILESTONE) continue
                val profile = profileOf(card.player.id) ?: continue
                val followers = social.followers(FollowType.PLAYER, listOf(profile)) - after.match.scorerUid
                notify(followers, NotificationKind.MILESTONE_WICKETS, after.match, title, card.player.name, card.wickets, message = "${card.player.name} took ${card.wickets} wickets in $title")
            }
        }
    }

    /** A fixture's start time moved: everyone following either team or a player in it hears. */
    suspend fun rescheduled(match: Match) {
        val title = "${match.teamA.name} vs ${match.teamB.name}"
        notify(
            followersOf(match),
            NotificationKind.FIXTURE_RESCHEDULED,
            match,
            title,
            detail = match.scheduledAt?.toString().orEmpty(),
            message = "$title has been rescheduled",
        )
    }

    /**
     * Team news for [uids]: someone asking to join (to the admins) or being let in (to them). Tapping it
     * opens the team.
     */
    suspend fun teamNotice(uids: Set<String>, kind: NotificationKind, teamId: String, teamName: String, playerName: String) {
        if (uids.isEmpty()) return
        val at = clock()
        social.addNotifications(
            uids.associateWith { Notification(UUID.randomUUID().toString(), kind, matchId = "", matchTitle = teamName, playerName = playerName, createdAt = at, teamId = teamId) },
        )
        val tokens = social.pushTokens(uids).values.flatten()
        if (tokens.isEmpty()) return
        val message = when (kind) {
            NotificationKind.TEAM_JOIN_ACCEPTED -> "You're in $teamName"
            else -> "$playerName wants to join $teamName"
        }
        runCatching { push.send(tokens, "DesiCrics", message, mapOf("teamId" to teamId)) }
            .onSuccess { gone -> social.deletePushTokens(gone) }
            .onFailure { log.warn("Push failed", it) }
    }

    /**
     * Tournament entries: someone asking or joining (to the organisers), or what became of an entry (to the
     * team's admins or the player). [name] is the team or player; [detail] says which. Tapping it opens
     * the tournament.
     */
    suspend fun tournamentNotice(uids: Set<String>, kind: NotificationKind, tournamentId: String, tournamentName: String, name: String, detail: String) {
        if (uids.isEmpty()) return
        val at = clock()
        social.addNotifications(
            uids.associateWith {
                Notification(UUID.randomUUID().toString(), kind, matchId = "", matchTitle = tournamentName, playerName = name, detail = detail, createdAt = at, tournamentId = tournamentId)
            },
        )
        val tokens = social.pushTokens(uids).values.flatten()
        if (tokens.isEmpty()) return
        val team = detail == "team"
        val message = when (kind) {
            NotificationKind.ENTRY_REQUEST -> "$name wants to join $tournamentName"
            NotificationKind.ENTRY_JOINED -> "$name joined $tournamentName"
            NotificationKind.ENTRY_CONFIRMED -> if (team) "$name is in $tournamentName" else "You're in $tournamentName"
            NotificationKind.ENTRY_REJECTED -> if (team) "$tournamentName didn't take $name this time" else "$tournamentName didn't take your entry this time"
            NotificationKind.SQUAD_PLACED -> "You're in $name's squad for $tournamentName"
            NotificationKind.ENTRY_INVITED -> if (team) "$name is invited to $tournamentName" else "You're invited to play in $tournamentName"
            NotificationKind.ENTRY_WITHDRAWN -> "$name left $tournamentName"
            else -> tournamentName
        }
        runCatching { push.send(tokens, "DesiCrics", message, mapOf("tournamentId" to tournamentId)) }
            .onSuccess { gone -> social.deletePushTokens(gone) }
            .onFailure { log.warn("Push failed", it) }
    }

    private suspend fun followersOf(match: Match): Set<String> {
        val teams = listOfNotNull(match.teamA.teamId, match.teamB.teamId)
        val players = (match.teamA.players + match.teamB.players).mapNotNull { it.profileId }
        return social.followers(FollowType.TEAM, teams) + social.followers(FollowType.PLAYER, players)
    }

    private suspend fun notify(
        uids: Set<String>,
        kind: NotificationKind,
        match: Match,
        title: String,
        playerName: String? = null,
        value: Int? = null,
        detail: String? = null,
        message: String,
    ) {
        if (uids.isEmpty()) return
        val at = clock()
        social.addNotifications(
            uids.associateWith { Notification(UUID.randomUUID().toString(), kind, match.id, title, playerName, value, detail, at) },
        )
        val tokens = social.pushTokens(uids).values.flatten()
        if (tokens.isEmpty()) return
        // A push failure mustn't fail the scorer's ball; the inbox already has it.
        runCatching { push.send(tokens, "DesiCrics", message, mapOf("matchId" to match.id)) }
            .onSuccess { gone -> social.deletePushTokens(gone) }
            .onFailure { log.warn("Push failed", it) }
    }

    private companion object {
        val RUN_MILESTONES = listOf(50, 100)
        const val WICKET_MILESTONE = 5
        val log = LoggerFactory.getLogger(Notifier::class.java)
    }
}
