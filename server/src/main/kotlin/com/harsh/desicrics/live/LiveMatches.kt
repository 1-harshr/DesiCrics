package com.harsh.desicrics.live

import com.harsh.desicrics.api.MatchDetails
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap

/** Told about every accepted change to a match: [before] is null for a new match. */
fun interface MatchListener {
    suspend fun changed(before: MatchDetails?, after: MatchDetails)
}

/**
 * The live feed: every change to a match goes out to whoever is watching it over a WebSocket.
 *
 * Kept in this process's memory, which is right for one server instance. With more than one, changes
 * need to travel between instances first (Postgres LISTEN/NOTIFY would do), or followers only see the
 * changes made through the instance they're connected to.
 */
class LiveMatches : MatchListener {
    private val channels = ConcurrentHashMap<String, MutableSharedFlow<MatchDetails>>()

    override suspend fun changed(before: MatchDetails?, after: MatchDetails) {
        channels[after.match.id]?.tryEmit(after)
    }

    /** Each change to [matchId] from now on. A slow watcher skips to the newest scorecard rather than queueing. */
    fun updates(matchId: String): SharedFlow<MatchDetails> = channels.getOrPut(matchId) {
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
}
