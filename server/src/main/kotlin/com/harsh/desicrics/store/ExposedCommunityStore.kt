package com.harsh.desicrics.store

import com.harsh.desicrics.db.DirectoryEntriesTable
import com.harsh.desicrics.db.LookingForPostsTable
import com.harsh.desicrics.db.LookingForResponsesTable
import com.harsh.desicrics.db.TeamsTable
import com.harsh.desicrics.db.query
import com.harsh.desicrics.model.DirectoryEntry
import com.harsh.desicrics.model.DirectoryKind
import com.harsh.desicrics.model.LookingForKind
import com.harsh.desicrics.store.CommunityStore.PostRow
import com.harsh.desicrics.store.CommunityStore.ResponseRow
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ExposedCommunityStore(private val db: Database) : CommunityStore {

    override suspend fun addPost(post: PostRow, at: Long) {
        db.query {
            LookingForPostsTable.insert {
                it[id] = post.id
                it[authorUid] = post.authorUid
                it[kind] = post.kind
                it[city] = post.city
                it[forDate] = post.date
                it[note] = post.note
                it[teamId] = post.teamId
                it[isOpen] = true
                it[createdAt] = at
            }
        }
    }

    override suspend fun post(id: String): PostRow? = db.query {
        LookingForPostsTable.selectAll().where { LookingForPostsTable.id eq id }.singleOrNull()?.toPost()
    }

    override suspend fun openPosts(city: String, kind: LookingForKind?, fromDate: Long): List<PostRow> = db.query {
        var where: Op<Boolean> = (LookingForPostsTable.city.lowerCase() eq city.trim().lowercase()) and
            (LookingForPostsTable.isOpen eq true) and (LookingForPostsTable.forDate greaterEq fromDate)
        if (kind != null) where = where and (LookingForPostsTable.kind eq kind)
        LookingForPostsTable.selectAll().where { where }.orderBy(LookingForPostsTable.forDate).limit(100).map { it.toPost() }
    }

    override suspend fun postsBy(uid: String): List<PostRow> = db.query {
        LookingForPostsTable.selectAll().where { LookingForPostsTable.authorUid eq uid }
            .orderBy(LookingForPostsTable.createdAt, SortOrder.DESC).limit(50).map { it.toPost() }
    }

    override suspend fun close(id: String) {
        db.query { LookingForPostsTable.update({ LookingForPostsTable.id eq id }) { it[isOpen] = false } }
    }

    override suspend fun respond(postId: String, response: ResponseRow): Boolean = try {
        db.query {
            LookingForResponsesTable.insert {
                it[this.postId] = postId
                it[uid] = response.uid
                it[message] = response.message
                it[phone] = response.phone
                it[createdAt] = response.createdAt
            }
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun responses(postId: String): List<ResponseRow> = db.query {
        LookingForResponsesTable.selectAll().where { LookingForResponsesTable.postId eq postId }
            .orderBy(LookingForResponsesTable.createdAt)
            .map { ResponseRow(it[LookingForResponsesTable.uid], it[LookingForResponsesTable.message], it[LookingForResponsesTable.phone], it[LookingForResponsesTable.createdAt]) }
    }

    override suspend fun responseCounts(postIds: Collection<String>): Map<String, Int> = if (postIds.isEmpty()) emptyMap() else db.query {
        val count = LookingForResponsesTable.uid.count()
        LookingForResponsesTable.select(LookingForResponsesTable.postId, count)
            .where { LookingForResponsesTable.postId inList postIds }
            .groupBy(LookingForResponsesTable.postId)
            .associate { it[LookingForResponsesTable.postId] to it[count].toInt() }
    }

    override suspend fun addEntry(entry: DirectoryEntry, at: Long) {
        db.query {
            DirectoryEntriesTable.insert {
                it[id] = entry.id
                it[kind] = entry.kind
                it[name] = entry.name
                it[city] = entry.city
                it[phone] = entry.phone
                it[notes] = entry.notes
                it[addedBy] = entry.addedBy
                it[createdAt] = at
            }
        }
    }

    override suspend fun entries(city: String, kind: DirectoryKind?): List<DirectoryEntry> = db.query {
        var where: Op<Boolean> = DirectoryEntriesTable.city.lowerCase() eq city.trim().lowercase()
        if (kind != null) where = where and (DirectoryEntriesTable.kind eq kind)
        DirectoryEntriesTable.selectAll().where { where }.orderBy(DirectoryEntriesTable.name).limit(200).map { it.toEntry() }
    }

    override suspend fun entry(id: String): DirectoryEntry? = db.query {
        DirectoryEntriesTable.selectAll().where { DirectoryEntriesTable.id eq id }.singleOrNull()?.toEntry()
    }

    override suspend fun removeEntry(id: String) {
        db.query { DirectoryEntriesTable.deleteWhere { DirectoryEntriesTable.id eq id } }
    }

    override suspend fun teamIdsInCity(city: String): List<String> = db.query {
        TeamsTable.select(TeamsTable.id).where { TeamsTable.city.lowerCase() eq city.trim().lowercase() }.map { it[TeamsTable.id] }
    }

    private fun ResultRow.toPost() = PostRow(
        this[LookingForPostsTable.id], this[LookingForPostsTable.authorUid], this[LookingForPostsTable.kind], this[LookingForPostsTable.city],
        this[LookingForPostsTable.forDate], this[LookingForPostsTable.note], this[LookingForPostsTable.teamId], this[LookingForPostsTable.isOpen],
    )

    private fun ResultRow.toEntry() = DirectoryEntry(
        this[DirectoryEntriesTable.id], this[DirectoryEntriesTable.kind], this[DirectoryEntriesTable.name], this[DirectoryEntriesTable.city],
        this[DirectoryEntriesTable.phone], this[DirectoryEntriesTable.notes], this[DirectoryEntriesTable.addedBy],
    )
}
