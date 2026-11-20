package com.harsh.desicrics.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionSale
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.auction.AuctionPoster
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What the share studio offers for an auction poster the auction screen asked for. */
fun auctionShareContents(poster: AuctionPoster): List<ShareContent> = when (poster) {
    is AuctionPoster.Sold -> listOf(SoldShare(SoldFigures.of(poster.details, poster.sale)))
    is AuctionPoster.TeamAfterAuction -> listOf(TeamAfterAuctionShare(TeamAfterAuctionFigures.of(poster.details, poster.teamId)))
}

// ---- Sold! ----

/** One sale: who went, to whom, for how much, at which tournament. Prices are in the auction's own points. */
@Immutable
data class SoldFigures(
    val player: String,
    val photoUrl: String?,
    /** Category and role, e.g. "Marquee · Batter"; may be blank. */
    val detail: String?,
    val team: String,
    val teamLogoUrl: String?,
    val price: Int,
    val basePrice: Int,
    val pointsLabel: String,
    val tournament: String,
) {
    companion object {
        fun of(details: AuctionDetails, sale: AuctionSale): SoldFigures {
            val entry = details.player(sale.profileId)
            val team = details.teams.firstOrNull { it.teamId == sale.teamId }
            return SoldFigures(
                player = entry?.player?.name.orEmpty(),
                photoUrl = entry?.player?.photoUrl,
                detail = entry?.category,
                team = team?.name.orEmpty(),
                teamLogoUrl = team?.logoUrl,
                price = sale.price,
                basePrice = entry?.basePrice ?: details.auction.settings.basePrice,
                pointsLabel = details.auction.settings.purseLabel,
                tournament = details.auction.tournamentName,
            )
        }
    }
}

/** "Sold!" as a stamped card, a paper card, or white text over the user's own photo. */
class SoldShare(private val sold: SoldFigures) : ShareContent {
    override val key = KEY

    @Composable
    override fun label() = stringResource(Res.string.share_content_sold)

    override val templates: List<ShareTemplate> = listOf(
        ShareTemplate("stamp", Res.string.share_template_stamp, TemplateKind.OPAQUE) { SoldStampTemplate(sold, it) },
        ShareTemplate("minimal", Res.string.share_template_minimal, TemplateKind.OPAQUE) { SoldPaperTemplate(sold, it) },
        ShareTemplate("overlay", Res.string.share_template_overlay, TemplateKind.OVERLAY) { SoldOverlay(sold, it) },
    )

    companion object {
        const val KEY = "auction-sold"
    }
}

@Composable
private fun pointsText(amount: Int, label: String) = stringResource(Res.string.auction_points, amount, label)

/**
 * A rubber stamp: SOLD! in a frame, knocked slightly askew. [ink] is the stamp's colour; [fill] covers
 * whatever it lands on, like the photo's ring.
 */
@Composable
internal fun SoldStamp(ink: Color, size: Int, modifier: Modifier = Modifier, fill: Color = Color.Transparent) {
    val type = DesiTheme.type
    Box(
        modifier.rotate(-10f).background(fill, DesiTheme.radius.sm).border((size / 14).coerceAtLeast(3).dp, ink, DesiTheme.radius.sm)
            .padding(horizontal = (size / 4).dp, vertical = (size / 14).dp),
    ) {
        Text(stringResource(Res.string.poster_auction_sold), style = type.scoreXl.sized(size, size + 4), color = ink, maxLines = 1)
    }
}

/** The player in a gold ring under the auction tag, the SOLD! stamp across them, then the team and the price. */
@Composable
fun SoldStampTemplate(sold: SoldFigures, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val on = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Box(Modifier.fillMaxSize()) {
        PosterBackdrop()
        Stripes(on, alpha = 0.03f)
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (post) 22.dp else 26.dp, vertical = if (post) 18.dp else 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PosterTag(stringResource(Res.string.poster_auction_tag))
                Text(sold.tournament, style = type.label.sized(12, 15), color = on.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (post) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    PosterAvatar(sold.player, sold.photoUrl, 120.dp, colors.brandStrong, on, ring = share.gold)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sold.player, style = type.titleLg.sized(28, 32), color = on, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        sold.detail?.takeIf { it.isNotBlank() }?.let { Text(it.uppercase(), style = type.label.sized(11, 14), color = on.copy(alpha = 0.7f), maxLines = 1) }
                        SoldStamp(share.gold, 34, Modifier.padding(top = 6.dp))
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        PosterAvatar(sold.player, sold.photoUrl, 168.dp, colors.brandStrong, on, ring = share.gold)
                    }
                    SoldStamp(share.gold, 58, Modifier.padding(top = 4.dp).offsetUp(), fill = colors.brandStrong)
                    Text(sold.player, style = type.titleLg.sized(36, 40), color = on, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp))
                    sold.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it.uppercase(), style = type.label.sized(13, 16), color = on.copy(alpha = 0.7f), maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            SoldDeal(sold, post)
            PosterFooter(on.copy(alpha = 0.85f), caption = if (post) sold.tournament else null)
        }
    }
}

/** Pulls the stamp half its height up over the bottom of the photo, the way a stamp lands across a card. */
private fun Modifier.offsetUp() = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val lift = placeable.height / 2
        layout(placeable.width, placeable.height - lift) { placeable.place(0, -lift) }
    },
)

/** "Sold to" the team with its badge, and the price in a gold block. */
@Composable
private fun SoldDeal(sold: SoldFigures, post: Boolean) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val on = colors.onBrandStrong
    Row(
        Modifier.fillMaxWidth().clip(DesiTheme.radius.md).background(colors.ink.copy(alpha = 0.3f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).padding(if (post) 10.dp else 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PosterAvatar(sold.team, sold.teamLogoUrl, if (post) 38.dp else 48.dp, on.copy(alpha = 0.12f), share.gold)
            Column {
                Text(stringResource(Res.string.poster_auction_sold_to).uppercase(), style = type.label.sized(10, 12), color = on.copy(alpha = 0.7f))
                Text(sold.team, style = type.titleMd.sized(if (post) 16 else 20, if (post) 20 else 24), color = on, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(
            Modifier.background(share.gold).padding(horizontal = if (post) 14.dp else 18.dp, vertical = if (post) 10.dp else 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(stringResource(Res.string.poster_auction_price).uppercase(), style = type.label.sized(10, 12), color = share.onGold.copy(alpha = 0.75f))
            Text("${sold.price}", style = type.scoreXl.sized(if (post) 34 else 46, if (post) 36 else 48), color = share.onGold, maxLines = 1)
            Text(sold.pointsLabel.uppercase(), style = type.label.sized(11, 13), color = share.onGold, maxLines = 1)
        }
    }
}

/** Off-white paper and big numerals: the stamp in brand green, the price, and the team underneath. */
@Composable
fun SoldPaperTemplate(sold: SoldFigures, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val ink = share.paperInk
    val post = format == ShareFormat.POST
    Column(
        Modifier.fillMaxSize().background(share.paper).padding(horizontal = if (post) 24.dp else 28.dp, vertical = if (post) 20.dp else 34.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.poster_auction_tag).uppercase(), style = type.label.sized(12, 15), color = colors.brand)
            Text(sold.tournament, style = type.bodyStrong.sized(14, 18), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PosterRule(ink.copy(alpha = 0.15f), Modifier.padding(top = 10.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(if (post) 6.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PosterAvatar(sold.player, sold.photoUrl, if (post) 64.dp else 84.dp, colors.brandStrong, colors.onBrandStrong)
                Column(Modifier.weight(1f)) {
                    Text(sold.player, style = type.titleLg.sized(if (post) 26 else 32, if (post) 30 else 36), color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    sold.detail?.takeIf { it.isNotBlank() }?.let { Text(it, style = type.body.sized(13, 16), color = ink.copy(alpha = 0.65f), maxLines = 1) }
                }
            }
            SoldStamp(colors.brand, if (post) 40 else 56, Modifier.padding(vertical = if (post) 4.dp else 12.dp, horizontal = 8.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${sold.price}", style = type.scoreXl.sized(if (post) 72 else 110, if (post) 72 else 108), color = ink, maxLines = 1)
                Text(sold.pointsLabel, style = type.titleMd.sized(if (post) 18 else 22, 26), color = ink.copy(alpha = 0.7f), maxLines = 1, modifier = Modifier.padding(bottom = if (post) 10.dp else 16.dp))
            }
            Text(
                stringResource(Res.string.poster_auction_base, pointsText(sold.basePrice, sold.pointsLabel)),
                style = type.caption.sized(12, 15),
                color = ink.copy(alpha = 0.6f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(if (post) 10.dp else 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterAvatar(sold.team, sold.teamLogoUrl, if (post) 36.dp else 44.dp, colors.brandStrong, colors.onBrandStrong)
                Text(
                    stringResource(Res.string.poster_auction_to_team, sold.team),
                    style = type.titleMd.sized(if (post) 17 else 20, 24),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PosterFooter(ink)
        }
    }
}

/** White text for the user's own photo of the player: SOLD!, the name, the team and the price. */
@Composable
fun SoldOverlay(sold: SoldFigures, format: ShareFormat) = OverlayFrame(sold.tournament, format) {
    val type = DesiTheme.type
    val post = format == ShareFormat.POST
    Text(stringResource(Res.string.poster_auction_sold), style = type.scoreXl.sized(if (post) 64 else 92, if (post) 68 else 96).overlay())
    Text(sold.player, style = type.titleLg.sized(if (post) 30 else 40, if (post) 34 else 44).overlay(), maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(
        stringResource(Res.string.poster_auction_to_team, sold.team).uppercase(),
        style = type.label.sized(if (post) 13 else 15, 18).overlay(0.9f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text("${sold.price}", style = type.scoreXl.sized(if (post) 56 else 80).overlay(), modifier = Modifier.alignByBaseline())
        Text(sold.pointsLabel.uppercase(), style = type.label.sized(14, 18).overlay(0.85f), modifier = Modifier.alignByBaseline())
    }
}

// ---- Team after auction ----

/** One player in a team's squad after the auction, at what they cost. */
@Immutable
data class SquadBuy(val name: String, val detail: String?, val price: Int, val retained: Boolean)

/** A team's squad once the auction is over: retained players first, then buys in the order they came. */
@Immutable
data class TeamAfterAuctionFigures(
    val team: String,
    val logoUrl: String?,
    val players: List<SquadBuy>,
    val spent: Int,
    val left: Int,
    val pointsLabel: String,
    val tournament: String,
) {
    companion object {
        fun of(details: AuctionDetails, teamId: String): TeamAfterAuctionFigures {
            val team = details.teams.firstOrNull { it.teamId == teamId }
            val purse = details.state.team(teamId)
            val prices = details.state.sales.filter { it.teamId == teamId }.associate { it.profileId to it.price }
            fun buy(id: String, retained: Boolean): SquadBuy? {
                val entry = details.player(id) ?: return null
                return SquadBuy(entry.player.name, entry.category, if (retained) entry.retainedPrice ?: 0 else prices[id] ?: 0, retained)
            }
            return TeamAfterAuctionFigures(
                team = team?.name.orEmpty(),
                logoUrl = team?.logoUrl,
                players = purse?.retained.orEmpty().mapNotNull { buy(it, true) } + purse?.bought.orEmpty().mapNotNull { buy(it, false) },
                spent = purse?.spent ?: 0,
                left = purse?.purseLeft ?: details.auction.settings.purse,
                pointsLabel = details.auction.settings.purseLabel,
                tournament = details.auction.tournamentName,
            )
        }
    }
}

/** The team's haul as a priced team sheet, split into pages for a big squad. */
class TeamAfterAuctionShare(private val team: TeamAfterAuctionFigures) : ShareContent {
    override val key = KEY

    @Composable
    override fun label() = stringResource(Res.string.share_content_team_after_auction)

    private fun pages(format: ShareFormat) = team.players.chunked(if (format == ShareFormat.POST) PER_POST else PER_STORY).ifEmpty { listOf(emptyList()) }

    override val templates: List<ShareTemplate> = listOf(
        ShareTemplate.paged("sheet", Res.string.share_template_team_sheet, TemplateKind.OPAQUE, pages = { pages(it).size }) { format, page ->
            val all = pages(format)
            val index = page.coerceIn(all.indices)
            TeamAfterAuctionTemplate(team, all[index], firstNumber = index * (if (format == ShareFormat.POST) PER_POST else PER_STORY) + 1, index, all.size, format)
        },
    )

    companion object {
        const val KEY = "auction-team"
        private const val PER_STORY = 12
        private const val PER_POST = 10
    }
}

/**
 * The team's badge and name, what it spent and has left, then every player with their price; retained
 * players carry a tag. Posts set the list in two columns.
 */
@Composable
fun TeamAfterAuctionTemplate(team: TeamAfterAuctionFigures, players: List<SquadBuy>, firstNumber: Int, page: Int, pages: Int, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val on = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Box(Modifier.fillMaxSize()) {
        PosterBackdrop()
        Column(Modifier.fillMaxSize().padding(horizontal = if (post) 20.dp else 26.dp, vertical = if (post) 18.dp else 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PosterAvatar(team.team, team.logoUrl, if (post) 52.dp else 76.dp, on.copy(alpha = 0.12f), share.gold, ring = share.gold)
                Column(verticalArrangement = Arrangement.spacedBy(if (post) 2.dp else 6.dp)) {
                    PosterTag(stringResource(Res.string.poster_auction_after))
                    Text(team.team, style = type.titleLg.sized(if (post) 22 else 30, if (post) 26 else 34), color = on, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(team.tournament, style = type.body.sized(13, 16), color = on.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(if (post) 10.dp else 20.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HaulStat(pluralStringResource(Res.plurals.poster_player_count, team.players.size, team.players.size), null, highlight = false, post, Modifier.weight(1f))
                HaulStat("${team.spent}", stringResource(Res.string.poster_auction_spent, team.pointsLabel), highlight = true, post, Modifier.weight(1f))
                HaulStat("${team.left}", stringResource(Res.string.poster_auction_left, team.pointsLabel), highlight = false, post, Modifier.weight(1f))
            }
            Spacer(Modifier.height(if (post) 10.dp else 20.dp))
            val columns = if (post && players.size > 5) players.chunked((players.size + 1) / 2) else listOf(players)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                var number = firstNumber
                columns.forEach { column ->
                    Column(Modifier.weight(1f)) { column.forEach { BuyRow(number++, it, team.pointsLabel, post) } }
                }
            }
            PosterFooter(on.copy(alpha = 0.85f), page, pages)
        }
    }
}

/** A figure tile under the team's name; the spend is in gold. A null [label] means [value] says it all. */
@Composable
private fun HaulStat(value: String, label: String?, highlight: Boolean, post: Boolean, modifier: Modifier) {
    val colors = DesiTheme.colors
    val type = DesiTheme.type
    val ink = if (highlight) colors.share.onGold else colors.onBrandStrong
    Column(
        modifier.fillMaxHeight().clip(DesiTheme.radius.md).background(if (highlight) colors.share.gold else colors.ink.copy(alpha = 0.3f))
            .padding(vertical = if (post) 6.dp else 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, style = (if (label == null) type.titleMd.sized(if (post) 15 else 18, 24) else type.scoreLg.sized(if (post) 20 else 26, if (post) 24 else 30)), color = ink, maxLines = 1)
        if (label != null) Text(label.uppercase(), style = type.label.sized(10, 12), color = ink.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
private fun BuyRow(number: Int, buy: SquadBuy, pointsLabel: String, post: Boolean) {
    val colors = DesiTheme.colors
    val type = DesiTheme.type
    val on = colors.onBrandStrong
    Column {
        Row(Modifier.fillMaxWidth().height(if (post) 28.dp else 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(number.toString().padStart(2, '0'), style = type.scoreMd.sized(if (post) 13 else 16, 20), color = colors.share.gold, modifier = Modifier.width(if (post) 22.dp else 30.dp))
            Text(buy.name, style = type.titleMd.sized(if (post) 12 else 16, 20), color = on, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (buy.retained) {
                Text(
                    // Two columns leave little room, so a post says it in a word.
                    stringResource(if (post) Res.string.poster_auction_retained_short else Res.string.poster_auction_retained).uppercase(),
                    style = type.label.sized(if (post) 8 else 9, 11),
                    color = on.copy(alpha = 0.75f),
                    maxLines = 1,
                    modifier = Modifier.clip(DesiTheme.radius.full).background(on.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                if (post) "${buy.price}" else pointsText(buy.price, pointsLabel),
                style = type.bodyStrong.sized(if (post) 12 else 15, 20),
                color = colors.share.gold,
                maxLines = 1,
            )
        }
        PosterRule(on.copy(alpha = 0.12f))
    }
}
