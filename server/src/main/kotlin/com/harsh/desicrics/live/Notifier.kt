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

/** Sends push messages to devices; returns the tokens the push service says are gone, so they can be dropped. */
fun interface PushSender {
    suspend fun send(tokens: List<String>, title: String, body: String, data: Map<String, String>): List<String>
}

/** Used when no push credentials are configured: the inbox still fills, nothing is pushed. */
object LoggingPushSender : PushSender {
    private val log = LoggerFactory.getLogger(LoggingPushSender::class.java)

    override suspend fun send(tokens: List<String>, title: String, body: String, data: Map<String, String>): List<String> {
        log.info("Push (not configured) to {} devices: {} - {}", tokens.size, title, body)
        return emptyList()
    }
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
        when {
            before == null -> notify(matchFollowers, NotificationKind.MATCH_STARTED, match, title, message = "$title has started")
            before.match.status != MatchStatus.COMPLETED && match.status == MatchStatus.COMPLETED ->
                notify(matchFollowers, NotificationKind.MATCH_RESULT, match, title, detail = match.scoreSummary, message = "$title: ${match.scoreSummary}")
        }
        if (before != null) milestones(before, after, title)
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
