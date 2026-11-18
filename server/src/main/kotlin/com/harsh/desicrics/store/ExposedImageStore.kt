package com.harsh.desicrics.store

import com.harsh.desicrics.db.ImagesTable
import com.harsh.desicrics.db.query
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class ExposedImageStore(private val db: Database) : ImageStore {

    override suspend fun save(id: String, ownerUid: String, contentType: String, bytes: ByteArray, at: Long) {
        db.query {
            ImagesTable.insert {
                it[this.id] = id
                it[this.ownerUid] = ownerUid
                it[this.contentType] = contentType
                it[this.bytes] = bytes
                it[createdAt] = at
            }
        }
    }

    override suspend fun get(id: String): Pair<String, ByteArray>? = db.query {
        ImagesTable.selectAll().where { ImagesTable.id eq id }.singleOrNull()?.let { it[ImagesTable.contentType] to it[ImagesTable.bytes] }
    }
}
