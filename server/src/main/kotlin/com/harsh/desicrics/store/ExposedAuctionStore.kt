package com.harsh.desicrics.store

import com.harsh.desicrics.db.AuctionEventsTable
import com.harsh.desicrics.db.AuctionPlayersTable
import com.harsh.desicrics.db.AuctionsTable
import com.harsh.desicrics.db.TournamentsTable
import com.harsh.desicrics.db.query
import com.harsh.desicrics.model.AuctionEvent
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.model.AuctionStatus
import com.harsh.desicrics.store.AuctionStore.AuctionRow
import com.harsh.desicrics.store.AuctionStore.PlayerRow
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ExposedAuctionStore(private val db: Database) : AuctionStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(auction: AuctionRow): Boolean = try {
        db.query {
            AuctionsTable.insert {
                it[id] = auction.id
                it[tournamentId] = auction.tournamentId
                it[settings] = json.encodeToString(auction.settings)
                it[status] = auction.status
                it[createdBy] = auction.createdBy
                it[createdAt] = auction.createdAt
            }
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun get(auctionId: String): AuctionRow? = db.query {
        AuctionsTable.join(TournamentsTable, JoinType.INNER, AuctionsTable.tournamentId, TournamentsTable.id)
            .selectAll().where { AuctionsTable.id eq auctionId }
            .singleOrNull()?.let {
                AuctionRow(
                    id = it[AuctionsTable.id],
                    tournamentId = it[AuctionsTable.tournamentId],
                    tournamentName = it[TournamentsTable.name],
                    settings = json.decodeFromString<AuctionSettings>(it[AuctionsTable.settings]),
                    status = it[AuctionsTable.status],
                    createdBy = it[AuctionsTable.createdBy],
                    createdAt = it[AuctionsTable.createdAt],
                )
            }
    }

    override suspend fun idForTournament(tournamentId: String): String? = db.query {
        AuctionsTable.select(AuctionsTable.id).where { AuctionsTable.tournamentId eq tournamentId }.singleOrNull()?.get(AuctionsTable.id)
    }

    override suspend fun updateSettings(auctionId: String, settings: AuctionSettings) {
        db.query { AuctionsTable.update({ AuctionsTable.id eq auctionId }) { it[this.settings] = json.encodeToString(settings) } }
    }

    override suspend fun idsWithStatus(statuses: Collection<AuctionStatus>): List<String> = db.query {
        AuctionsTable.select(AuctionsTable.id).where { AuctionsTable.status inList statuses }.map { it[AuctionsTable.id] }
    }

    override suspend fun players(auctionId: String): List<PlayerRow> = db.query {
        AuctionPlayersTable.selectAll().where { AuctionPlayersTable.auctionId eq auctionId }
            .orderBy(AuctionPlayersTable.addedAt)
            .map { it.toPlayerRow() }
    }

    override suspend fun addPlayer(auctionId: String, player: PlayerRow): Boolean = try {
        db.query {
            AuctionPlayersTable.insert {
                it[this.auctionId] = auctionId
                it[playerId] = player.profileId
                it[basePrice] = player.basePrice
                it[category] = player.category
                it[requested] = player.requested
                it[retainedBy] = player.retainedBy
                it[retainedPrice] = player.retainedPrice
                it[addedAt] = player.addedAt
            }
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    override suspend fun updatePlayer(auctionId: String, player: PlayerRow) {
        db.query {
            AuctionPlayersTable.update({ (AuctionPlayersTable.auctionId eq auctionId) and (AuctionPlayersTable.playerId eq player.profileId) }) {
                it[basePrice] = player.basePrice
                it[category] = player.category
                it[requested] = player.requested
                it[retainedBy] = player.retainedBy
                it[retainedPrice] = player.retainedPrice
            }
        }
    }

    override suspend fun removePlayer(auctionId: String, profileId: String): Boolean = db.query {
        AuctionPlayersTable.deleteWhere { (AuctionPlayersTable.auctionId eq auctionId) and (playerId eq profileId) } > 0
    }

    override suspend fun events(auctionId: String): List<AuctionEvent> = db.query {
        AuctionEventsTable.select(AuctionEventsTable.data).where { AuctionEventsTable.auctionId eq auctionId }
            .orderBy(AuctionEventsTable.seq)
            .map { json.decodeFromString<AuctionEvent>(it[AuctionEventsTable.data]) }
    }

    override suspend fun addEvent(auctionId: String, event: AuctionEvent, status: AuctionStatus, by: String?, at: Long): Boolean = try {
        db.query {
            AuctionEventsTable.insert {
                it[this.auctionId] = auctionId
                it[seq] = event.seq
                it[type] = event.typeName
                it[data] = json.encodeToString(event)
                it[createdBy] = by
                it[createdAt] = at
            }
            AuctionsTable.update({ AuctionsTable.id eq auctionId }) { it[this.status] = status }
        }
        true
    } catch (e: Exception) {
        if (e.isUniqueViolation()) false else throw e
    }

    private fun ResultRow.toPlayerRow() = PlayerRow(
        profileId = this[AuctionPlayersTable.playerId],
        basePrice = this[AuctionPlayersTable.basePrice],
        category = this[AuctionPlayersTable.category],
        requested = this[AuctionPlayersTable.requested],
        retainedBy = this[AuctionPlayersTable.retainedBy],
        retainedPrice = this[AuctionPlayersTable.retainedPrice],
        addedAt = this[AuctionPlayersTable.addedAt],
    )

    /** The serial name, for reading the log by eye. */
    private val AuctionEvent.typeName: String
        get() = when (this) {
            is AuctionEvent.Start -> "start"
            is AuctionEvent.NextPlayer -> "next_player"
            is AuctionEvent.Bid -> "bid"
            is AuctionEvent.Sold -> "sold"
            is AuctionEvent.Unsold -> "unsold"
            is AuctionEvent.Undo -> "undo"
            is AuctionEvent.Pause -> "pause"
            is AuctionEvent.Resume -> "resume"
            is AuctionEvent.ReverseSale -> "reverse_sale"
            is AuctionEvent.Finish -> "finish"
        }
}
