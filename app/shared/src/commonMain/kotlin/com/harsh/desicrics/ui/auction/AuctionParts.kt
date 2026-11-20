package com.harsh.desicrics.ui.auction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.component.HeroButton
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.Tag
import com.harsh.desicrics.designsystem.component.TagTone
import com.harsh.desicrics.designsystem.component.heroBackground
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiMotion
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionError
import com.harsh.desicrics.model.AuctionEvent
import com.harsh.desicrics.model.AuctionLot
import com.harsh.desicrics.model.AuctionMode
import com.harsh.desicrics.model.AuctionOrder
import com.harsh.desicrics.model.AuctionPlayer
import com.harsh.desicrics.model.AuctionSale
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.model.AuctionStatus
import com.harsh.desicrics.model.BidStep
import com.harsh.desicrics.model.TeamPurse
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.text.label
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a poster from the auction would show. The share studio (ui/share/) adds the "Sold!" and "Team after
 * auction" templates later; until then nothing passes a share callback and the share buttons stay hidden.
 */
sealed interface AuctionPoster {
    val details: AuctionDetails

    /** One sale, for the sold moment's share button. */
    data class Sold(override val details: AuctionDetails, val sale: AuctionSale) : AuctionPoster

    /** A team's squad once the auction is over: retained players and everyone it bought. */
    data class TeamAfterAuction(override val details: AuctionDetails, val teamId: String) : AuctionPoster
}

/** [amount] in the auction's points, e.g. "120 pts" or "45 ₹ lakh". */
@Composable
internal fun points(amount: Int, settings: AuctionSettings): String = stringResource(Res.string.auction_points, amount, settings.purseLabel)

internal fun AuctionDetails.playerName(profileId: String): String = player(profileId)?.player?.name.orEmpty()

internal fun AuctionDetails.teamName(teamId: String): String = teams.firstOrNull { it.teamId == teamId }?.name.orEmpty()

/** "+5 up to 50, then +10". */
@Composable
internal fun stepsText(steps: List<BidStep>): String = steps.mapIndexed { i, step ->
    when {
        step.upTo != null -> stringResource(Res.string.auction_step_upto, step.step, step.upTo!!)
        i > 0 -> stringResource(Res.string.auction_step_then, step.step)
        else -> stringResource(Res.string.auction_step_always, step.step)
    }
}.joinToString(", ")

internal val AuctionMode.label: StringResource
    get() = when (this) {
        AuctionMode.HALL -> Res.string.auction_mode_hall
        AuctionMode.ONLINE -> Res.string.auction_mode_online
    }

internal val AuctionOrder.label: StringResource
    get() = when (this) {
        AuctionOrder.CATEGORY -> Res.string.auction_order_category
        AuctionOrder.RANDOM -> Res.string.auction_order_random
    }

internal val AuctionStatus.label: StringResource
    get() = when (this) {
        AuctionStatus.SETUP -> Res.string.auction_status_setup
        AuctionStatus.LIVE -> Res.string.auction_status_live
        AuctionStatus.PAUSED -> Res.string.auction_status_paused
        AuctionStatus.FINISHED -> Res.string.auction_status_finished
    }

@Composable
internal fun AuctionStatusTag(status: AuctionStatus, modifier: Modifier = Modifier) {
    val (tone, icon) = when (status) {
        AuctionStatus.SETUP -> TagTone.NEUTRAL to DesiIcons.Settings
        AuctionStatus.LIVE -> TagTone.DANGER to DesiIcons.Auction
        AuctionStatus.PAUSED -> TagTone.WARN to DesiIcons.Pause
        AuctionStatus.FINISHED -> TagTone.BRAND to DesiIcons.CheckCircle
    }
    Tag(stringResource(status.label), modifier, tone = tone, icon = icon)
}

/** The engine's reasons, in the app's words. */
internal val AuctionError.text: StringResource
    get() = when (this) {
        AuctionError.PURSE_INVALID -> Res.string.auction_error_purse
        AuctionError.LABEL_INVALID -> Res.string.auction_error_label
        AuctionError.BASE_PRICE_INVALID -> Res.string.auction_error_base_price
        AuctionError.SQUAD_INVALID -> Res.string.auction_error_squad
        AuctionError.PURSE_TOO_SMALL -> Res.string.auction_error_purse_too_small
        AuctionError.STEPS_INVALID -> Res.string.auction_error_steps
        AuctionError.CATEGORIES_INVALID -> Res.string.auction_error_categories
        AuctionError.TIMER_INVALID -> Res.string.auction_error_timer
        AuctionError.RETAINED_INVALID -> Res.string.auction_error_retained
        AuctionError.TOO_FEW_TEAMS -> Res.string.auction_error_too_few_teams
        AuctionError.NO_PLAYERS -> Res.string.auction_error_no_players
        AuctionError.NOT_STARTED -> Res.string.auction_error_not_started
        AuctionError.ALREADY_STARTED -> Res.string.auction_error_already_started
        AuctionError.FINISHED -> Res.string.auction_error_finished
        AuctionError.PAUSED -> Res.string.auction_error_paused
        AuctionError.NOT_PAUSED -> Res.string.auction_error_not_paused
        AuctionError.LOT_OPEN -> Res.string.auction_error_lot_open
        AuctionError.NO_LOT -> Res.string.auction_error_no_lot
        AuctionError.NOT_AVAILABLE -> Res.string.auction_error_not_available
        AuctionError.WRONG_PLAYER -> Res.string.auction_error_wrong_player
        AuctionError.TEAM_UNKNOWN -> Res.string.auction_error_team_unknown
        AuctionError.TEAM_FULL -> Res.string.auction_error_team_full
        AuctionError.ALREADY_LEADING -> Res.string.auction_error_already_leading
        AuctionError.BID_TOO_LOW -> Res.string.auction_error_bid_too_low
        AuctionError.BID_TOO_HIGH -> Res.string.auction_error_bid_too_high
        AuctionError.NO_BIDS -> Res.string.auction_error_no_bids
        AuctionError.NOT_HIGHEST_BID -> Res.string.auction_error_not_highest
        AuctionError.NOTHING_TO_UNDO -> Res.string.auction_error_nothing_to_undo
        AuctionError.NOT_SOLD -> Res.string.auction_error_not_sold
    }

/** A pool player's facts in one line: category, role (unless the category says it already), then [extra]. */
@Composable
internal fun playerLine(player: AuctionPlayer, vararg extra: String?): String =
    listOfNotNull(player.category, player.player.role?.let { stringResource(it.label) }, *extra)
        .filter { it.isNotBlank() }.distinctBy { it.lowercase() }.joinToString(" · ")

/**
 * The player up for bidding and where the bidding stands: the card at the centre of every room. [large] is
 * the hall's big-screen size. [clock] draws on the right when the auction runs one.
 */
@Composable
internal fun LotStage(
    details: AuctionDetails,
    lot: AuctionLot,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    clock: (@Composable () -> Unit)? = null,
) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    val settings = details.auction.settings
    val entry = details.player(lot.profileId)
    val name = entry?.player?.name.orEmpty()
    val onHero = colors.onBrandStrong
    Column(
        modifier.fillMaxWidth().clip(DesiTheme.shapes.large).heroBackground().padding(if (large) space.s5 else space.s5),
        verticalArrangement = if (large) Arrangement.SpaceBetween else Arrangement.spacedBy(space.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s4)) {
            PhotoAvatar(name, entry?.player?.photoUrl, size = 72)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.s1)) {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.auction_up_now).uppercase(), style = DesiTheme.type.label, color = onHero.copy(alpha = 0.75f))
                    if (lot.round > 1) HeroPill(stringResource(Res.string.auction_round_unsold))
                }
                Text(
                    name,
                    style = if (large) DesiTheme.type.scoreLg else DesiTheme.type.titleLg,
                    color = onHero,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val line = entry?.let { playerLine(it, it.player.city.ifBlank { null }) }.orEmpty()
                if (line.isNotEmpty()) Text(line, style = DesiTheme.type.body, color = onHero.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            clock?.invoke()
        }
        if (!large) HorizontalDivider(color = onHero.copy(alpha = 0.18f))
        val leading = lot.leading
        val leadText = if (leading == null) stringResource(Res.string.auction_no_bids) else stringResource(Res.string.auction_leading, details.teamName(leading.teamId))
        val nextText = stringResource(Res.string.auction_next_bid, points(lot.nextBid, settings))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (leading == null) Res.string.auction_base_price else Res.string.auction_current_bid).uppercase(),
                    style = DesiTheme.type.label,
                    color = onHero.copy(alpha = 0.75f),
                )
                Text(
                    points(leading?.amount ?: lot.basePrice, settings),
                    style = if (large) DesiTheme.type.scoreXl else DesiTheme.type.scoreLg,
                    color = if (leading == null) onHero else colors.share.gold,
                    maxLines = 1,
                )
                // The big screen is short on height, so who leads moves to the right of the bid there.
                if (!large) Text(leadText, style = DesiTheme.type.bodyStrong, color = onHero, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.padding(start = space.s2), horizontalAlignment = Alignment.End) {
                if (large) Text(leadText, style = DesiTheme.type.titleLg, color = onHero, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(nextText, style = if (large) DesiTheme.type.body else DesiTheme.type.caption, color = onHero.copy(alpha = 0.75f))
            }
        }
    }
}

/** A frosted pill on the hero: "Unsold round", "Paused". */
@Composable
internal fun HeroPill(text: String, modifier: Modifier = Modifier) {
    val colors = DesiTheme.colors
    Text(
        text,
        style = DesiTheme.type.label,
        color = colors.onBrandStrong,
        modifier = modifier.clip(DesiTheme.shapes.pill).background(colors.onBrandStrong.copy(alpha = 0.16f))
            .padding(horizontal = DesiTheme.space.s2, vertical = 2.dp),
    )
}

/**
 * The online clock: a ring that empties as the lot runs down, with the seconds left in the middle. It turns
 * red for the last few seconds. [onHero] draws it in the hero's colours.
 */
@Composable
internal fun CountdownRing(millisLeft: Long, totalMillis: Long, modifier: Modifier = Modifier, size: Dp = 64.dp, onHero: Boolean = true) {
    val colors = DesiTheme.colors
    val seconds = ((millisLeft + 999) / 1000).coerceAtLeast(0).toInt()
    val fraction = (millisLeft.toFloat() / totalMillis.coerceAtLeast(1)).coerceIn(0f, 1f)
    val urgent = seconds <= 5
    val track = if (onHero) colors.onBrandStrong.copy(alpha = 0.2f) else colors.line
    val progress = if (urgent) colors.event.wicket else if (onHero) colors.share.gold else colors.brand
    val label = stringResource(Res.string.auction_seconds_left, seconds)
    Box(modifier.size(size).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.1f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(progress, -90f, 360f * fraction, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$seconds", style = if (size >= 80.dp) DesiTheme.type.scoreLg else DesiTheme.type.scoreMd, color = if (urgent) colors.event.wicket else if (onHero) colors.onBrandStrong else colors.ink)
    }
}

/** How long the clock had when it last started: the lot's full time before any bid, the shorter reset after. */
internal fun clockTotalMillis(settings: AuctionSettings, lot: AuctionLot): Long =
    (if (lot.bids.isEmpty()) settings.lotSeconds else settings.bidSeconds) * 1000L

/**
 * Between lots: the sale or the miss that just happened, or who's coming up. Shown where the stage goes
 * while nobody is up for bidding.
 */
@Composable
internal fun BetweenLots(details: AuctionDetails, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    val settings = details.auction.settings
    val onHero = colors.onBrandStrong
    Column(
        modifier.fillMaxWidth().clip(DesiTheme.shapes.large).heroBackground().padding(if (large) space.s6 else space.s5),
        verticalArrangement = Arrangement.spacedBy(space.s2, if (large) Alignment.CenterVertically else Alignment.Top),
    ) {
        when (val latest = details.latest) {
            is AuctionEvent.Sold -> {
                Text(stringResource(Res.string.auction_sold_big), style = DesiTheme.type.scoreLg, color = colors.share.gold)
                Text(details.playerName(latest.profileId), style = if (large) DesiTheme.type.scoreLg else DesiTheme.type.titleLg, color = onHero)
                Text(
                    stringResource(Res.string.auction_sold_to_for, details.teamName(latest.teamId), points(latest.price, settings)),
                    style = DesiTheme.type.bodyLg,
                    color = onHero,
                )
            }
            is AuctionEvent.Unsold -> {
                Text(stringResource(Res.string.auction_unsold).uppercase(), style = DesiTheme.type.scoreLg, color = onHero.copy(alpha = 0.85f))
                Text(stringResource(Res.string.auction_unsold_body, details.playerName(latest.profileId)), style = DesiTheme.type.bodyLg, color = onHero)
            }
            else -> {
                Text(stringResource(Res.string.auction_waiting_next), style = if (large) DesiTheme.type.scoreLg else DesiTheme.type.titleLg, color = onHero)
            }
        }
        val upNext = details.state.upNext
        Text(
            if (upNext != null) stringResource(Res.string.auction_up_next, details.playerName(upNext)) else stringResource(Res.string.auction_nobody_left),
            style = DesiTheme.type.body,
            color = onHero.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = space.s2),
        )
    }
}

/** One team's purse, squad and the most it can bid on the player up now. */
@Composable
internal fun PurseRow(details: AuctionDetails, purse: TeamPurse, divider: Boolean, highlight: Boolean = false) {
    val settings = details.auction.settings
    val name = details.teamName(purse.teamId)
    val leading = details.state.lot?.leading?.teamId == purse.teamId
    ListRow(
        title = name,
        subtitle = stringResource(Res.string.auction_team_line, purse.squadSize, settings.maxSquad, points(purse.maxBid, settings)),
        leading = { com.harsh.desicrics.designsystem.component.Avatar(name, size = 40) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(points(purse.purseLeft, settings), style = DesiTheme.type.bodyStrong, color = if (highlight || leading) DesiTheme.colors.brand else DesiTheme.colors.ink)
                when {
                    leading -> Text(stringResource(Res.string.auction_team_leading), style = DesiTheme.type.caption, color = DesiTheme.colors.brand)
                    purse.full -> Text(stringResource(Res.string.auction_team_full), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
                }
            }
        },
        divider = divider,
    )
}

@Composable
internal fun Purses(details: AuctionDetails, mine: Set<String> = emptySet()) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        SectionHeader(stringResource(Res.string.auction_purses), icon = DesiIcons.Shield)
        ListCard {
            details.state.teams.forEachIndexed { i, purse -> PurseRow(details, purse, divider = i > 0, highlight = purse.teamId in mine) }
        }
    }
}

/**
 * The latest sales, newest first. Organisers get a Reverse button on each, which [onReverse] confirms.
 */
@Composable
internal fun RecentSales(details: AuctionDetails, limit: Int = 5, onReverse: ((AuctionSale) -> Unit)? = null) {
    val sales = details.state.sales.asReversed().take(limit)
    if (sales.isEmpty()) return
    val settings = details.auction.settings
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        SectionHeader(stringResource(Res.string.auction_recent_sales), icon = DesiIcons.History)
        ListCard {
            sales.forEachIndexed { i, sale ->
                val name = details.playerName(sale.profileId)
                ListRow(
                    title = name,
                    subtitle = details.teamName(sale.teamId),
                    leading = { PhotoAvatar(name, details.player(sale.profileId)?.player?.photoUrl) },
                    trailing = {
                        Text(points(sale.price, settings), style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
                        if (onReverse != null) {
                            TextButton(onClick = { onReverse(sale) }) {
                                Text(stringResource(Res.string.auction_reverse), style = DesiTheme.type.button, color = DesiTheme.colors.muted)
                            }
                        }
                    },
                    divider = i > 0,
                )
            }
        }
    }
}

/**
 * The full-screen sold moment: SOLD!, the player, the team and the price. It lands with a bounce, or simply
 * appears when the system says to reduce motion. A tap closes it; the screen closes it on its own after a
 * few seconds. [onShare] is the poster seam: null hides the button.
 */
@Composable
internal fun SoldMoment(details: AuctionDetails, sale: AuctionEvent.Sold, onDismiss: () -> Unit, onShare: (() -> Unit)? = null) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    val settings = details.auction.settings
    val reduceMotion = DesiTheme.reduceMotion
    val scale = remember(sale.seq) { Animatable(if (reduceMotion) 1f else 2.4f) }
    val fade = remember(sale.seq) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(sale.seq) {
        if (reduceMotion) return@LaunchedEffect
        coroutineScope {
            launch { fade.animateTo(1f, tween(DesiMotion.BASE_MS)) }
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    val name = details.playerName(sale.profileId)
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .heroBackground()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .padding(space.s6),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(space.s3)) {
            Icon(painterResource(DesiIcons.Auction), null, tint = colors.share.gold, modifier = Modifier.size(56.dp).graphicsLayer { rotationZ = -20f * (scale.value - 1f) })
            Text(
                stringResource(Res.string.auction_sold_big),
                style = DesiTheme.type.scoreXl.copy(fontSize = DesiTheme.type.scoreXl.fontSize * 1.6f, lineHeight = DesiTheme.type.scoreXl.lineHeight * 1.5f),
                color = colors.share.gold,
                modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            )
            PhotoAvatar(name, details.player(sale.profileId)?.player?.photoUrl, size = 104)
            Text(name, style = DesiTheme.type.titleLg, color = colors.onBrandStrong, textAlign = TextAlign.Center)
            Text(stringResource(Res.string.auction_sold_to, details.teamName(sale.teamId)), style = DesiTheme.type.bodyLg, color = colors.onBrandStrong.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            Text(points(sale.price, settings), style = DesiTheme.type.scoreLg, color = colors.share.gold)
            if (onShare != null) HeroButton(stringResource(Res.string.auction_share), onShare, icon = DesiIcons.Share)
            Text(stringResource(Res.string.auction_tap_to_close), style = DesiTheme.type.caption, color = colors.onBrandStrong.copy(alpha = 0.6f), modifier = Modifier.padding(top = space.s4))
        }
    }
}
