package com.harsh.desicrics.data

import com.harsh.desicrics.api.AddAuctionPlayerRequest
import com.harsh.desicrics.api.AuctionPlayerRequest
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionSettings
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A tournament's player auction: its setup, the live room, and every action the organiser and owners take. */
class AuctionRepository(private val api: DesiCricsApi) {

    /** Null when the tournament has no auction yet. */
    suspend fun forTournament(tournamentId: String): AuctionDetails? = api.tournamentAuction(tournamentId)

    suspend fun create(tournamentId: String, settings: AuctionSettings = AuctionSettings()): AuctionDetails =
        api.createAuction(tournamentId, settings)

    /**
     * The auction as it changes: a fetch first, then every change over the live WebSocket. When the socket
     * drops, one fetch keeps the room current and the socket is retried after a short wait, like a match.
     */
    fun observe(auctionId: String): Flow<Result<AuctionDetails>> = flow {
        emit(fetch { api.auction(auctionId) })
        while (true) {
            try {
                api.liveAuction(auctionId).collect { emit(Result.success(it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(fetch { api.auction(auctionId) })
            }
            delay(RECONNECT_DELAY)
        }
    }

    suspend fun updateSettings(auctionId: String, settings: AuctionSettings) = api.updateAuctionSettings(auctionId, settings)

    suspend fun addPlayer(auctionId: String, profileId: String) =
        api.addAuctionPlayer(auctionId, AddAuctionPlayerRequest(profileId = profileId))

    suspend fun addPlaceholder(auctionId: String, name: String, phone: String) = api.addAuctionPlayer(
        auctionId,
        AddAuctionPlayerRequest(placeholderName = name.trim(), placeholderPhone = phone.trim().ifEmpty { null }),
    )

    /** Base price, category and retention; also accepts a request to join. */
    suspend fun updatePlayer(auctionId: String, profileId: String, request: AuctionPlayerRequest) =
        api.updateAuctionPlayer(auctionId, profileId, request)

    /** Takes a player out of the pool, or declines their request. */
    suspend fun removePlayer(auctionId: String, profileId: String) = api.removeAuctionPlayer(auctionId, profileId)

    suspend fun setRequest(auctionId: String, requested: Boolean) = api.setAuctionRequest(auctionId, requested)

    suspend fun start(auctionId: String) = api.startAuction(auctionId)

    suspend fun next(auctionId: String, profileId: String? = null) = api.nextAuctionPlayer(auctionId, profileId)

    suspend fun bid(auctionId: String, teamId: String, amount: Int? = null) = api.bid(auctionId, teamId, amount)

    suspend fun sold(auctionId: String) = api.sellAuctionPlayer(auctionId)

    suspend fun unsold(auctionId: String) = api.passAuctionPlayer(auctionId)

    suspend fun undo(auctionId: String) = api.undoAuction(auctionId)

    suspend fun pause(auctionId: String) = api.pauseAuction(auctionId)

    suspend fun resume(auctionId: String) = api.resumeAuction(auctionId)

    suspend fun reverseSale(auctionId: String, profileId: String) = api.reverseSale(auctionId, profileId)

    suspend fun finish(auctionId: String) = api.finishAuction(auctionId)

    private suspend fun <T> fetch(call: suspend () -> T): Result<T> = try {
        Result.success(call())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        val RECONNECT_DELAY = 3.seconds
    }
}
