package com.harsh.desicrics.store

import com.harsh.desicrics.db.FollowsTable
import com.harsh.desicrics.db.NotificationsTable
import com.harsh.desicrics.db.PushTokensTable
import com.harsh.desicrics.db.query
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.Notification
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

class ExposedSocialStore(private val db: Database) : SocialStore {

    override suspend fun follow(uid: String, type: FollowType, targetId: String, at: Long) {
        db.query {
            FollowsTable.upsert {
                it[this.uid] = uid
                it[targetType] = type
                it[this.targetId] = targetId
                it[createdAt] = at
            }
        }
    }

    override suspend fun unfollow(uid: String, type: FollowType, targetId: String) {
        db.query {
            FollowsTable.deleteWhere { (FollowsTable.uid eq uid) and (targetType eq type) and (FollowsTable.targetId eq targetId) }
        }
    }

    override suspend fun follows(uid: String): List<Pair<FollowType, String>> = db.query {
        FollowsTable.selectAll().where { FollowsTable.uid eq uid }.orderBy(FollowsTable.createdAt, SortOrder.DESC)
            .map { it[FollowsTable.targetType] to it[FollowsTable.targetId] }
    }

    override suspend fun followers(type: FollowType, targetIds: Collection<String>): Set<String> =
        if (targetIds.isEmpty()) emptySet() else db.query {
            FollowsTable.select(FollowsTable.uid)
                .where { (FollowsTable.targetType eq type) and (FollowsTable.targetId inList targetIds) }
                .map { it[FollowsTable.uid] }.toSet()
        }

    override suspend fun followerCount(type: FollowType, targetId: String): Int = db.query {
        FollowsTable.selectAll().where { (FollowsTable.targetType eq type) and (FollowsTable.targetId eq targetId) }.count().toInt()
    }

    override suspend fun addNotifications(notifications: Map<String, Notification>) {
        if (notifications.isEmpty()) return
        db.query {
            NotificationsTable.batchInsert(notifications.entries) { (uid, n) ->
                this[NotificationsTable.id] = n.id
                this[NotificationsTable.uid] = uid
                this[NotificationsTable.kind] = n.kind
                this[NotificationsTable.matchId] = n.matchId
                this[NotificationsTable.matchTitle] = n.matchTitle.take(130)
                this[NotificationsTable.playerName] = n.playerName
                this[NotificationsTable.amount] = n.value
                this[NotificationsTable.detail] = n.detail?.take(200)
                this[NotificationsTable.createdAt] = n.createdAt
                this[NotificationsTable.readAt] = null
                this[NotificationsTable.teamId] = n.teamId
            }
        }
    }

    override suspend fun notifications(uid: String, limit: Int): List<Notification> = db.query {
        NotificationsTable.selectAll().where { NotificationsTable.uid eq uid }
            .orderBy(NotificationsTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map {
                Notification(
                    id = it[NotificationsTable.id],
                    kind = it[NotificationsTable.kind],
                    matchId = it[NotificationsTable.matchId],
                    matchTitle = it[NotificationsTable.matchTitle],
                    playerName = it[NotificationsTable.playerName],
                    value = it[NotificationsTable.amount],
                    detail = it[NotificationsTable.detail],
                    createdAt = it[NotificationsTable.createdAt],
                    read = it[NotificationsTable.readAt] != null,
                    teamId = it[NotificationsTable.teamId],
                )
            }
    }

    override suspend fun unreadCount(uid: String): Int = db.query {
        NotificationsTable.selectAll().where { (NotificationsTable.uid eq uid) and NotificationsTable.readAt.isNull() }.count().toInt()
    }

    override suspend fun markAllRead(uid: String, at: Long) {
        db.query {
            NotificationsTable.update({ (NotificationsTable.uid eq uid) and NotificationsTable.readAt.isNull() }) { it[readAt] = at }
        }
    }

    override suspend fun savePushToken(uid: String, token: String, platform: String, at: Long) {
        db.query {
            PushTokensTable.upsert {
                it[this.token] = token
                it[this.uid] = uid
                it[this.platform] = platform
                it[updatedAt] = at
            }
        }
    }

    override suspend fun pushTokens(uids: Collection<String>): Map<String, List<String>> = if (uids.isEmpty()) emptyMap() else db.query {
        PushTokensTable.selectAll().where { PushTokensTable.uid inList uids }
            .groupBy({ it[PushTokensTable.uid] }, { it[PushTokensTable.token] })
    }

    override suspend fun deletePushTokens(tokens: Collection<String>) {
        if (tokens.isEmpty()) return
        db.query { PushTokensTable.deleteWhere { PushTokensTable.token inList tokens } }
    }
}
