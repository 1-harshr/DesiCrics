package com.harsh.desicrics.ui.auction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.ErrorState
import com.harsh.desicrics.designsystem.component.FullScreenLoading
import com.harsh.desicrics.model.AuctionMode
import com.harsh.desicrics.model.AuctionSale
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.model.AuctionStatus
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.system.HallDisplay
import com.harsh.desicrics.util.nowMillis
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Everything the auction screens can ask for. Defaults do nothing, so previews and tests pass only what they need. */
interface AuctionActions {
    fun back() {}
    fun retry() {}
    fun clearError() {}
    fun openPlayer(profileId: String) {}

    // Setup.
    fun updateSettings(settings: AuctionSettings) {}
    fun search(query: String) {}
    fun addPlayer(profileId: String) {}
    fun addPlaceholder(name: String, phone: String) {}
    fun updatePlayer(profileId: String, basePrice: Int, category: String?, retainedBy: String?, retainedPrice: Int?) {}
    fun accept(profileId: String) {}
    fun remove(profileId: String) {}
    fun setRequest(requested: Boolean) {}
    fun start() {}

    // The room.
    fun next(profileId: String? = null) {}
    fun bid(teamId: String, amount: Int?) {}
    fun sold() {}
    fun unsold() {}
    fun closeLot() {}
    fun undo() {}
    fun pause() {}
    fun resume() {}
    fun reverseSale(profileId: String) {}
    fun finish() {}
    fun dismissSoldMoment() {}
}

/**
 * A tournament's player auction, from setup to the results. Which screen shows follows the auction's
 * status and the caller's part in it: organisers set it up and run it, team owners bid online, everyone
 * else watches. [onSharePoster] is where the share studio's auction posters plug in; null hides every
 * share button until they exist.
 */
@Composable
fun AuctionScreen(
    auctionId: String,
    onBack: () -> Unit,
    onOpenPlayer: (String) -> Unit = {},
    onSharePoster: ((AuctionPoster) -> Unit)? = null,
    viewModel: AuctionViewModel = koinViewModel { parametersOf(auctionId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        object : AuctionActions {
            override fun back() = onBack()
            override fun retry() = viewModel.retry()
            override fun clearError() = viewModel.clearError()
            override fun openPlayer(profileId: String) = onOpenPlayer(profileId)
            override fun updateSettings(settings: AuctionSettings) = viewModel.updateSettings(settings)
            override fun search(query: String) = viewModel.search(query)
            override fun addPlayer(profileId: String) = viewModel.addPlayer(profileId)
            override fun addPlaceholder(name: String, phone: String) = viewModel.addPlaceholder(name, phone)
            override fun updatePlayer(profileId: String, basePrice: Int, category: String?, retainedBy: String?, retainedPrice: Int?) =
                viewModel.updatePlayer(profileId, basePrice, category, retainedBy, retainedPrice)
            override fun accept(profileId: String) = viewModel.accept(profileId)
            override fun remove(profileId: String) = viewModel.remove(profileId)
            override fun setRequest(requested: Boolean) = viewModel.setRequest(requested)
            override fun start() = viewModel.start()
            override fun next(profileId: String?) = viewModel.next(profileId)
            override fun bid(teamId: String, amount: Int?) = viewModel.bid(teamId, amount)
            override fun sold() = viewModel.sold()
            override fun unsold() = viewModel.unsold()
            override fun closeLot() = viewModel.closeLot()
            override fun undo() = viewModel.undo()
            override fun pause() = viewModel.pause()
            override fun resume() = viewModel.resume()
            override fun reverseSale(profileId: String) = viewModel.reverseSale(profileId)
            override fun finish() = viewModel.finish()
            override fun dismissSoldMoment() = viewModel.dismissSoldMoment()
        }
    }
    val details = state.details
    val status = details?.state?.status
    val live = status == AuctionStatus.LIVE || status == AuctionStatus.PAUSED
    // The hall's one screen faces the room: sideways and always on. Anyone watching a live room keeps theirs on too.
    if (live && details != null) HallDisplay(landscape = state.isOrganiser && details.auction.settings.mode == AuctionMode.HALL)
    // The sold moment closes itself after a few seconds, so the auctioneer can bring up the next player.
    state.soldMoment?.let { sale -> LaunchedEffect(sale.seq) { delay(SOLD_MOMENT_MS); viewModel.dismissSoldMoment() } }
    AuctionContent(state, actions, millisLeft = rememberMillisLeft(state), onSharePoster = onSharePoster)
}

/** Ticks down to the lot's end by the server's clock; null when there's no clock running. */
@Composable
private fun rememberMillisLeft(state: AuctionUiState): Long? {
    val details = state.details ?: return null
    val endsAt = details.lotEndsAt.takeIf { details.auction.settings.mode == AuctionMode.ONLINE && details.state.status == AuctionStatus.LIVE } ?: return null
    var left by remember(endsAt, state.clockSkew) { mutableStateOf(endsAt - (nowMillis() + state.clockSkew)) }
    LaunchedEffect(endsAt, state.clockSkew) {
        while (left > 0) {
            delay(TICK_MS)
            left = endsAt - (nowMillis() + state.clockSkew)
        }
    }
    return left.coerceAtLeast(0)
}

/** The auction for [state]: setup, the live room (hall or online) or the results, with the sold moment on top. */
@Composable
internal fun AuctionContent(
    state: AuctionUiState,
    actions: AuctionActions,
    millisLeft: Long? = null,
    onSharePoster: ((AuctionPoster) -> Unit)? = null,
) {
    val details = state.details
    if (details == null) {
        DesiScaffold(title = stringResource(Res.string.auction_title), onBack = actions::back) { padding ->
            if (state.loading) FullScreenLoading(Modifier.padding(padding))
            else ErrorState(
                stringResource(Res.string.auction_load_failed_title),
                stringResource(Res.string.error_connection_body),
                Modifier.padding(padding),
                onRetry = actions::retry,
            )
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        when (details.state.status) {
            AuctionStatus.SETUP -> AuctionSetupContent(state, actions)
            AuctionStatus.LIVE, AuctionStatus.PAUSED ->
                if (state.isOrganiser && details.auction.settings.mode == AuctionMode.HALL) AuctionHallContent(state, actions)
                else AuctionRoomContent(state, actions, millisLeft)
            AuctionStatus.FINISHED -> AuctionSummaryContent(state, actions, onSharePoster)
        }
        state.soldMoment?.let { sale ->
            SoldMoment(
                details,
                sale,
                onDismiss = actions::dismissSoldMoment,
                onShare = onSharePoster?.let { share -> { share(AuctionPoster.Sold(details, AuctionSale(sale.profileId, sale.teamId, sale.price))) } },
            )
        }
    }
}

private const val SOLD_MOMENT_MS = 3_500L
private const val TICK_MS = 200L
