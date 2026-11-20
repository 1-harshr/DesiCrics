package com.harsh.desicrics.ui.auction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.Tag
import com.harsh.desicrics.designsystem.component.TagTone
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionStatus
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The auction on the tournament screen. Before there is one, organisers get a button to set it up; once it
 * exists everyone can open it (to watch, bid or see the results), and players can ask to be put up while
 * it's being set up.
 */
@Composable
fun AuctionCard(
    auction: AuctionDetails?,
    myId: String?,
    isOrganiser: Boolean,
    saving: Boolean,
    onOpen: () -> Unit,
    onAsk: (requested: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** False while the tournament takes players itself: joining it puts them up in the auction. */
    canAsk: Boolean = true,
) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    DesiCard(modifier.fillMaxWidth(), padding = space.s4) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s3)) {
            IconTile(DesiIcons.Auction)
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.auction_title), style = DesiTheme.type.bodyStrong, color = colors.ink)
                val line = if (auction == null) {
                    stringResource(Res.string.auction_card_create_hint)
                } else {
                    val pool = auction.players.count { !it.requested }
                    stringResource(Res.string.auction_card_summary, stringResource(auction.auction.settings.mode.label), pluralStringResource(Res.plurals.auction_players_count, pool, pool), auction.teams.size)
                }
                Text(line, style = DesiTheme.type.caption, color = colors.muted)
            }
            auction?.let { AuctionStatusTag(it.state.status) }
        }
        if (auction == null) {
            PrimaryButton(stringResource(Res.string.auction_card_create), onClick = onOpen, loading = saving, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Auction)
            return@DesiCard
        }
        val status = auction.state.status
        val open = when {
            status == AuctionStatus.FINISHED -> Res.string.auction_card_results
            status != AuctionStatus.SETUP -> Res.string.auction_card_watch
            else -> Res.string.auction_card_open
        }
        TonalButton(stringResource(open), onClick = onOpen, modifier = Modifier.fillMaxWidth(), leadingIcon = if (status == AuctionStatus.LIVE) DesiIcons.Play else DesiIcons.Auction)
        if (isOrganiser || myId == null || status == AuctionStatus.FINISHED || !canAsk) return@DesiCard
        val me = auction.player(myId)
        when {
            me == null && status == AuctionStatus.SETUP -> TextButton(onClick = { onAsk(true) }, enabled = !saving) {
                Text(stringResource(Res.string.auction_card_ask), style = DesiTheme.type.button, color = colors.brand)
            }
            me?.requested == true -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.auction_card_requested), style = DesiTheme.type.caption, color = colors.muted, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAsk(false) }, enabled = !saving) {
                    Text(stringResource(Res.string.auction_card_withdraw), style = DesiTheme.type.button, color = colors.muted)
                }
            }
            me != null -> Tag(stringResource(Res.string.auction_card_in_pool), tone = TagTone.BRAND, icon = DesiIcons.CheckCircle)
        }
    }
}
