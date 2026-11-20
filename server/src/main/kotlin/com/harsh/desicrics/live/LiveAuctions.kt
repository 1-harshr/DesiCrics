package com.harsh.desicrics.live

import com.harsh.desicrics.model.AuctionDetails
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * The auction room's live feed: every change goes out to whoever is watching over a WebSocket. In memory, like
 * [LiveMatches], so it's right for one server instance; the online clock runs in the same process.
 */
class LiveAuctions {
    private val channels = ConcurrentHashMap<String, MutableSharedFlow<AuctionDetails>>()

    fun publish(details: AuctionDetails) {
        channels[details.auction.id]?.tryEmit(details)
    }

    /** Each change to [auctionId] from now on. A slow watcher skips to the newest state rather than queueing. */
    fun updates(auctionId: String): SharedFlow<AuctionDetails> = channels.getOrPut(auctionId) {
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
}
