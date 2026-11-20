package com.harsh.desicrics.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.icon.DesiIllustrations
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------------------------------------
// Points table

/** The table's columns after the team: played, won, lost, points, net run rate. */
private val TableColumns = listOf(Res.string.table_played, Res.string.table_won, Res.string.table_lost, Res.string.table_points, Res.string.table_nrr)

private fun TableLine.cells() = listOf("$played", "$won", "$lost", "$points", nrr)

/** Narrow figures, a wider NRR. */
private fun columnWidth(i: Int, post: Boolean) = if (i == 4) (if (post) 44.dp else 48.dp) else (if (post) 24.dp else 26.dp)

/**
 * Table: the broadcast look. Brand green, POINTS TABLE in big type, a dark strip per team with points in
 * gold and a gold edge on the places that go through.
 */
@Composable
fun TablePosterCard(poster: TablePoster, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Box(Modifier.fillMaxSize().background(colors.brandStrong)) {
        Stripes(onGreen)
        Column(Modifier.fillMaxSize().padding(if (post) 18.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (post) 6.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(poster.tournament, share.gold, modifier = Modifier.weight(1f))
                PageBadge(poster.page, share.onGold, share.gold)
            }
            Text(stringResource(Res.string.poster_content_table).uppercase(), style = type.scoreXl.sized(if (post) 34 else 56), color = onGreen, maxLines = 1)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (post) 3.dp else 6.dp, Alignment.CenterVertically)) {
                TableHeader(share.gold, post, Modifier.padding(start = if (post) 30.dp else 36.dp))
                poster.lines.forEach { line ->
                    if (line.group != null) {
                        Eyebrow(line.group, share.gold, type.label.sized(if (post) 11 else 13, 16), Modifier.padding(top = if (post) 2.dp else 6.dp))
                    } else {
                        Row(
                            Modifier.fillMaxWidth().height(if (post) 26.dp else 44.dp).clip(DesiTheme.radius.sm).background(colors.ink.copy(alpha = 0.45f)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(4.dp).fillMaxHeight().background(if (line.qualifies) share.gold else Color.Transparent))
                            Text(
                                "${line.rank}",
                                style = type.scoreMd.sized(if (post) 14 else 20),
                                color = onGreen,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(if (post) 26.dp else 32.dp),
                            )
                            Text(
                                line.team.uppercase(),
                                style = type.titleMd.sized(if (post) 13 else 16, if (post) 16 else 20),
                                color = onGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            line.cells().forEachIndexed { i, cell ->
                                val points = i == 3
                                Box(
                                    Modifier.width(columnWidth(i, post)).fillMaxHeight().background(if (points) share.gold else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        cell,
                                        style = if (points) type.scoreMd.sized(if (post) 14 else 20) else type.data.sized(if (post) 11 else 13, 16),
                                        color = if (points) share.onGold else onGreen.copy(alpha = 0.9f),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
                if (poster.advance > 0) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(4.dp).height(14.dp).background(share.gold))
                        Text(stringResource(Res.string.table_qualify), style = type.caption, color = onGreen.copy(alpha = 0.8f))
                    }
                }
            }
            PosterFooter(onGreen)
        }
    }
}

/** TEAM, then the figures' headings over their columns. */
@Composable
private fun TableHeader(color: Color, post: Boolean, modifier: Modifier = Modifier, style: TextStyle = DesiTheme.type.label.sized(if (post) 10 else 12, 16)) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.table_team).uppercase(), style = style, color = color, modifier = Modifier.weight(1f))
        TableColumns.forEachIndexed { i, column ->
            Text(stringResource(column).uppercase(), style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.width(columnWidth(i, post)))
        }
    }
}

/** Minimal table: paper and ink, a rule under the last place that goes through, nothing else. */
@Composable
fun MinimalTablePoster(poster: TablePoster, format: ShareFormat) {
    val share = DesiTheme.colors.share
    val type = DesiTheme.type
    val ink = share.paperInk
    val quiet = ink.copy(alpha = 0.6f)
    val post = format == ShareFormat.POST
    Column(
        Modifier.fillMaxSize().background(share.paper).padding(horizontal = if (post) 24.dp else 28.dp, vertical = if (post) 22.dp else 40.dp),
        verticalArrangement = Arrangement.spacedBy(if (post) 6.dp else 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(poster.tournament, quiet, modifier = Modifier.weight(1f))
            poster.page?.let { Text(stringResource(Res.string.share_page, it.number, it.count), style = type.label, color = quiet) }
        }
        Text(stringResource(Res.string.poster_content_table), style = type.titleLg.sized(if (post) 28 else 52, if (post) 32 else 56), color = ink)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            TableHeader(quiet, post, Modifier.padding(start = if (post) 22.dp else 26.dp, bottom = 4.dp))
            poster.lines.forEachIndexed { index, line ->
                if (line.group != null) {
                    Text(line.group, style = type.bodyStrong.sized(if (post) 12 else 14, if (post) 16 else 20), color = ink, modifier = Modifier.padding(top = if (post) 0.dp else 10.dp, bottom = 2.dp))
                    return@forEachIndexed
                }
                val next = poster.lines.getOrNull(index + 1)
                // A firm rule under the last qualifier, hairlines elsewhere.
                val cutoff = line.qualifies && next?.qualifies != true && poster.advance > 0
                Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.12f)))
                Row(Modifier.fillMaxWidth().padding(vertical = if (post) 3.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${line.rank}", style = type.data.sized(if (post) 12 else 14, 18), color = quiet, modifier = Modifier.width(if (post) 22.dp else 26.dp))
                    Text(line.team, style = type.bodyStrong.sized(if (post) 13 else 16, 20), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    line.cells().forEachIndexed { i, cell ->
                        Text(
                            cell,
                            style = if (i == 3) type.scoreMd.sized(if (post) 14 else 18) else type.data.sized(if (post) 11 else 13, 18),
                            color = if (i == 3) ink else quiet,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.width(columnWidth(i, post)),
                        )
                    }
                }
                if (cutoff) Box(Modifier.fillMaxWidth().height(2.dp).background(ink))
            }
        }
        if (poster.advance > 0 && !post) Text(stringResource(Res.string.poster_table_cutoff), style = type.caption, color = quiet)
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.2f)))
        Text(stringResource(Res.string.app_name), style = type.titleMd, color = ink)
    }
}

// ---------------------------------------------------------------------------------------------------------
// Player of the tournament

/**
 * Spotlight: the player's initials on a big gold disc under a faint trophy, their name in large type and
 * their tournament numbers in gold.
 */
@Composable
fun SpotlightPoster(poster: PlayerPoster, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Box(Modifier.fillMaxSize().background(colors.brandStrong)) {
        Icon(
            painterResource(DesiIcons.TrophyFilled),
            contentDescription = null,
            tint = onGreen.copy(alpha = 0.06f),
            modifier = Modifier.size(if (post) 300.dp else 420.dp).align(Alignment.TopEnd).offset(x = if (post) 110.dp else 150.dp, y = if (post) (-40).dp else 20.dp),
        )
        Column(Modifier.fillMaxSize().padding(if (post) 22.dp else 28.dp), verticalArrangement = Arrangement.spacedBy(if (post) 10.dp else 18.dp)) {
            Eyebrow(poster.tournament, share.gold)
            if (post) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PosterAvatar(poster.name, null, 104.dp, share.gold, share.onGold, ring = onGreen)
                    Column(Modifier.weight(1f)) { SpotlightName(poster, post) }
                }
            } else {
                Spacer(Modifier.weight(0.4f))
                PosterAvatar(poster.name, null, 168.dp, share.gold, share.onGold, ring = onGreen)
                Spacer(Modifier.height(4.dp))
                SpotlightName(poster, post)
                Spacer(Modifier.weight(0.6f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(if (post) 22.dp else 28.dp)) {
                poster.stats.forEach { stat ->
                    Column {
                        Text(stat.value, style = type.scoreXl.sized(if (post) 34 else 48), color = share.gold)
                        Text(stat.label.uppercase(), style = type.label.sized(11, 14), color = onGreen.copy(alpha = 0.8f))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(onGreen.copy(alpha = 0.2f)))
            PosterFooter(onGreen)
        }
    }
}

@Composable
private fun SpotlightName(poster: PlayerPoster, post: Boolean) {
    val colors = DesiTheme.colors
    val type = DesiTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.tournament_player_of_tournament_title).uppercase(),
            style = type.label.sized(if (post) 11 else 14, if (post) 14 else 18),
            color = colors.share.gold,
            maxLines = 2,
        )
        Text(poster.name.uppercase(), style = type.scoreXl.sized(if (post) 38 else 60, if (post) 40 else 60), color = colors.onBrandStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (poster.team.isNotBlank()) Text(poster.team, style = type.titleMd.sized(if (post) 16 else 20, 24), color = colors.onBrandStrong.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Overlay: white type for the user's own photo of the player, MVP points as the big number. Unlike the
 * match overlays it sits at the foot of the picture, leaving the top for the player's face.
 */
@Composable
fun PlayerOverlayPoster(poster: PlayerPoster, format: ShareFormat) {
    val type = DesiTheme.type
    val share = DesiTheme.colors.share
    val post = format == ShareFormat.POST
    val lead = poster.stats.first()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = if (post) 24.dp else 48.dp),
        verticalArrangement = Arrangement.spacedBy(if (post) 4.dp else 8.dp, Alignment.Bottom),
    ) {
        Text(stringResource(Res.string.tournament_player_of_tournament_title).uppercase(), style = type.label.sized(14, 18).overlay(0.9f))
        Text(poster.name, style = type.titleLg.sized(if (post) 30 else 40, if (post) 34 else 44).overlay(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (poster.team.isNotBlank()) Text(poster.team, style = type.bodyStrong.sized(16, 22).overlay(0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(lead.value, style = type.scoreXl.sized(if (post) 72 else 110).overlay(), maxLines = 1)
        Text(lead.label.uppercase(), style = type.label.sized(16, 20).overlay(0.85f))
        if (poster.stats.size > 1) OverlayStats(poster.stats.drop(1))
        Row(Modifier.padding(top = if (post) 8.dp else 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(DesiIllustrations.BallMark), contentDescription = null, tint = share.overlayInk, modifier = Modifier.size(22.dp))
            Text(stringResource(Res.string.app_name).uppercase(), style = type.label.sized(13, 16).overlay())
            Text(poster.tournament, style = type.caption.overlay(0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Announcement

/** What, when, where and how, as label and value pairs; the format's name is the long one. */
@Composable
private fun AnnouncementPoster.facts(): List<ShareStat> = listOfNotNull(
    ShareStat(starts ?: stringResource(Res.string.poster_dates_soon), stringResource(Res.string.poster_starts)),
    city.takeIf { it.isNotBlank() }?.let { ShareStat(it, stringResource(Res.string.poster_where)) },
    ShareStat(overs, stringResource(Res.string.stats_overs)),
    if (teams > 0) ShareStat("$teams", stringResource(Res.string.tournament_tab_teams)) else null,
    ShareStat(format, stringResource(Res.string.tournament_format)),
)

/** Lays [facts] out [columns] to a row, the last (the format) taking the rest of its row, at least two slots. */
private fun factRows(facts: List<ShareStat>, columns: Int): List<List<Pair<ShareStat, Int>>> {
    val short = facts.dropLast(1).chunked(columns).map { row -> row.map { it to 1 } }.toMutableList()
    val last = short.lastOrNull()
    return if (last != null && columns - last.size >= 2) {
        short.dropLast(1) + listOf(last + (facts.last() to columns - last.size))
    } else short + listOf(listOf(facts.last() to columns))
}

/**
 * Hero: the tournament's name as big as it'll go on brand green, a gold ENTRIES OPEN stamp while teams can
 * still register, and the facts in a grid at the foot.
 */
@Composable
fun HeroAnnouncementPoster(poster: AnnouncementPoster, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Box(Modifier.fillMaxSize().background(colors.brandStrong)) {
        Stripes(onGreen)
        Icon(
            painterResource(DesiIcons.TrophyFilled),
            contentDescription = null,
            tint = share.gold.copy(alpha = 0.12f),
            modifier = Modifier.size(if (post) 220.dp else 300.dp).align(Alignment.TopEnd).offset(x = if (post) 70.dp else 120.dp, y = if (post) (-30).dp else (-10).dp),
        )
        Column(Modifier.fillMaxSize().padding(if (post) 22.dp else 28.dp), verticalArrangement = Arrangement.spacedBy(if (post) 8.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(stringResource(Res.string.tournament_eyebrow), share.gold, modifier = Modifier.weight(1f))
                if (poster.entriesOpen) {
                    Box(Modifier.clip(DesiTheme.radius.sm).background(share.gold).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(stringResource(Res.string.poster_entries_open).uppercase(), style = type.label.sized(13, 16), color = share.onGold)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(poster.name.uppercase(), style = type.scoreXl.sized(if (post) 46 else 72, if (post) 46 else 70), color = onGreen, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Box(Modifier.width(64.dp).height(5.dp).background(share.gold))
            poster.organisers?.let {
                Text(stringResource(Res.string.poster_organised_by, it), style = type.bodyStrong.sized(if (post) 13 else 15, 20), color = onGreen.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(if (post) 0.3f else 0.6f))
            val columns = if (post) 3 else 2
            Column(Modifier.padding(bottom = if (post) 4.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(if (post) 6.dp else 10.dp)) {
                factRows(poster.facts(), columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (fact, span) ->
                            Column(Modifier.weight(span.toFloat()).clip(DesiTheme.radius.sm).background(colors.ink.copy(alpha = 0.35f)).padding(horizontal = 12.dp, vertical = if (post) 6.dp else 10.dp)) {
                                Text(fact.label.uppercase(), style = type.label.sized(11, 14), color = share.gold)
                                Text(fact.value, style = type.titleMd.sized(if (post) 15 else 19, if (post) 18 else 24), color = onGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val left = columns - row.sumOf { it.second }
                        if (left > 0) Spacer(Modifier.weight(left.toFloat()))
                    }
                }
            }
            PosterFooter(onGreen)
        }
    }
}

/** Minimal: paper, the name in large quiet type, and the facts as a short list of lines. */
@Composable
fun MinimalAnnouncementPoster(poster: AnnouncementPoster, format: ShareFormat) {
    val share = DesiTheme.colors.share
    val type = DesiTheme.type
    val ink = share.paperInk
    val quiet = ink.copy(alpha = 0.6f)
    val post = format == ShareFormat.POST
    Column(
        Modifier.fillMaxSize().background(share.paper).padding(horizontal = if (post) 28.dp else 36.dp, vertical = if (post) 24.dp else 40.dp),
        verticalArrangement = Arrangement.spacedBy(if (post) 6.dp else 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(Res.string.tournament_eyebrow), quiet, modifier = Modifier.weight(1f))
            if (poster.entriesOpen) Eyebrow(stringResource(Res.string.poster_entries_open), ink)
        }
        Spacer(Modifier.weight(1f))
        Text(poster.name, style = type.titleLg.sized(if (post) 32 else 54, if (post) 36 else 58), color = ink, maxLines = if (post) 2 else 3, overflow = TextOverflow.Ellipsis)
        poster.organisers?.let { Text(stringResource(Res.string.poster_organised_by, it), style = type.body.sized(15, 20), color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.weight(if (post) 0.4f else 0.8f))
        Column {
            poster.facts().forEach { fact ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.15f)))
                Row(Modifier.padding(vertical = if (post) 2.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fact.label.uppercase(), style = type.label, color = quiet, modifier = Modifier.width(96.dp))
                    Text(fact.value, style = type.bodyStrong.sized(if (post) 14 else 17, if (post) 20 else 22), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.2f)))
        Text(stringResource(Res.string.app_name), style = type.titleMd, color = ink)
    }
}
