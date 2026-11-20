package com.harsh.desicrics.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.ui.text.dismissalText
import com.harsh.desicrics.util.format2
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ---- Result · Big score ----

/** Both scores as big as they'll go on brand green, the winner bright and the loser dimmed, then the result. */
@Composable
fun BigScoreTemplate(figures: BigScoreFigures, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val on = colors.onBrandStrong
    val post = format == ShareFormat.POST
    val many = figures.scores.size > 2
    Box(Modifier.fillMaxSize()) {
        PosterBackdrop()
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (post) 22.dp else 28.dp, vertical = if (post) 20.dp else 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            PosterTag(stringResource(if (figures.finished) Res.string.poster_result else Res.string.share_content_score))
            Column(verticalArrangement = Arrangement.spacedBy(if (post) 6.dp else 16.dp)) {
                figures.scores.forEachIndexed { i, score ->
                    if (i > 0) PosterRule(on.copy(alpha = 0.18f))
                    val dim = figures.finished && !score.won
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PosterAvatar(score.team, null, if (post) 26.dp else 34.dp, on.copy(alpha = 0.12f), if (score.won) share.gold else on)
                            Text(
                                score.team.uppercase(),
                                style = type.titleMd.sized(if (post) 15 else 18, if (post) 18 else 22),
                                color = if (dim) on.copy(alpha = 0.7f) else on,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (score.won) Icon(painterResource(DesiIcons.TrophyFilled), null, tint = share.gold, modifier = Modifier.size(if (post) 16.dp else 20.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val size = when {
                                post -> if (many) 34 else 50
                                many -> 60
                                score.won || !figures.finished -> 104
                                else -> 84
                            }
                            Text(score.score, style = type.scoreXl.sized(size), color = if (dim) on.copy(alpha = 0.55f) else on, modifier = Modifier.alignByBaseline())
                            Text(score.overs, style = type.data.sized(if (post) 13 else 16, 20), color = on.copy(alpha = 0.7f), modifier = Modifier.alignByBaseline())
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(if (post) 8.dp else 12.dp)) {
                PosterResultStrip(figures.result, post)
                figures.playerOfTheMatch?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(DesiIcons.Star), null, tint = share.gold, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.scorecard_player_of_the_match, it), style = type.bodyStrong.sized(if (post) 13 else 15, 20), color = on, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                PosterFooter(on.copy(alpha = 0.85f), caption = if (post) null else figures.caption)
            }
        }
    }
}

// ---- Scorecard · Full card ----

/**
 * The scorecard as a printed sheet: a green header with the match and result, then [rows] (one page's
 * worth, see [paginateCard]) on paper, and the page number at the foot.
 */
@Composable
fun FullCardTemplate(title: String, result: String, rows: List<CardRow>, page: Int, pages: Int) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val on = colors.onBrandStrong
    Column(Modifier.fillMaxSize().background(share.paper)) {
        Box(Modifier.fillMaxWidth().height(FULL_CARD_HEADER.dp)) {
            PosterBackdrop()
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PosterTag(stringResource(Res.string.poster_scorecard))
                Text(title, style = type.titleLg.sized(22, 26), color = on, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(result, style = type.bodyStrong.sized(13, 16), color = share.gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 20.dp).padding(top = 12.dp)) {
            rows.forEach { CardRowView(it) }
        }
        PosterFooter(share.paperInk, page, pages, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
    }
}

/** Column widths shared by the headings and the rows under them. */
private val NumberColumn = 30.dp
private val RateColumn = 50.dp

@Composable
private fun CardRowView(row: CardRow) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val ink = share.paperInk
    val quiet = ink.copy(alpha = 0.6f)
    val heading = type.label.sized(10, 12)
    val number = type.data.sized(12, 14)
    val strong = number.copy(fontWeight = type.bodyStrong.fontWeight)
    val height = row.height.dp
    when (row) {
        is CardRow.Head -> Row(
            Modifier.fillMaxWidth().height(height).padding(vertical = 4.dp).clip(DesiTheme.radius.sm).background(ink.copy(alpha = 0.07f)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val name = stringResource(Res.string.scorecard_innings, row.innings.battingTeam.name)
            Text(
                if (row.continued) stringResource(Res.string.poster_continued, name) else name,
                style = type.titleMd.sized(14, 18),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(row.innings.scoreText, style = type.scoreMd.sized(20, 22), color = colors.brand, modifier = Modifier.alignByBaseline())
            Text(stringResource(Res.string.share_overs_detail, row.innings.oversText), style = type.data.sized(11, 14), color = quiet, modifier = Modifier.alignByBaseline())
        }
        is CardRow.BattingHead -> CardColumns(height, stringResource(Res.string.scorecard_batter), heading, quiet, Alignment.Bottom) {
            listOf(Res.string.scorecard_runs, Res.string.scorecard_balls, Res.string.scorecard_fours, Res.string.scorecard_sixes).forEach {
                Cell(stringResource(it), heading, quiet, NumberColumn)
            }
            Cell(stringResource(Res.string.scorecard_strike_rate), heading, quiet, RateColumn)
        }
        is CardRow.Batter -> Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
            val card = row.card
            Column(Modifier.weight(1f)) {
                Text(card.player.name, style = type.bodyStrong.sized(13, 16), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dismissalText(card.dismissal), style = type.caption.sized(10, 12), color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Cell("${card.runs}${if (card.isOut) "" else "*"}", strong, ink, NumberColumn)
            Cell("${card.balls}", number, quiet, NumberColumn)
            Cell("${card.fours}", number, quiet, NumberColumn)
            Cell("${card.sixes}", number, quiet, NumberColumn)
            Cell(card.strikeRate.format2(), number, quiet, RateColumn)
        }
        is CardRow.Extras -> Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
            val extras = row.innings.extras
            Text(
                stringResource(Res.string.scorecard_extras, extras.total, extras.wides, extras.noBalls, extras.byes, extras.legByes, extras.penalties),
                style = type.caption.sized(11, 14),
                color = quiet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        is CardRow.BowlingHead -> CardColumns(height, stringResource(Res.string.scorecard_bowler), heading, quiet, Alignment.Bottom) {
            listOf(Res.string.scorecard_overs, Res.string.scorecard_maidens, Res.string.scorecard_runs, Res.string.scorecard_wickets).forEach {
                Cell(stringResource(it), heading, quiet, NumberColumn)
            }
            Cell(stringResource(Res.string.scorecard_economy), heading, quiet, RateColumn)
        }
        is CardRow.Bowler -> CardColumns(height, row.card.player.name, type.body.sized(13, 16), ink, Alignment.CenterVertically) {
            val card = row.card
            Cell(card.oversText, number, quiet, NumberColumn)
            Cell("${card.maidens}", number, quiet, NumberColumn)
            Cell("${card.runs}", number, quiet, NumberColumn)
            Cell("${card.wickets}", strong, ink, NumberColumn)
            Cell(card.economy.format2(), number, quiet, RateColumn)
        }
        is CardRow.Gap -> Spacer(Modifier.height(height))
    }
}

/** A row with a name filling the left and right-aligned [cells]. */
@Composable
private fun CardColumns(height: Dp, name: String, style: TextStyle, color: Color, align: Alignment.Vertical, cells: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(height).padding(bottom = if (align == Alignment.Bottom) 3.dp else 0.dp), verticalAlignment = align) {
        Text(name.uppercase().takeIf { align == Alignment.Bottom } ?: name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        cells()
    }
}

@Composable
private fun Cell(text: String, style: TextStyle, color: Color, width: Dp) {
    Text(text, style = style, color = color, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(width))
}

// ---- Scorecard · Top performers ----

/** Each innings' total with its top batters and the bowlers who did best against them, TV-summary style. */
@Composable
fun TopPerformersTemplate(title: String, result: String, innings: List<InningsState>, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val on = colors.onBrandStrong
    val post = format == ShareFormat.POST
    val shown = if (post || innings.size > 2) 2 else 3
    Box(Modifier.fillMaxSize()) {
        PosterBackdrop()
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (post) 18.dp else 22.dp, vertical = if (post) 18.dp else 30.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(if (post) 6.dp else 10.dp)) {
                PosterTag(stringResource(Res.string.poster_top_performers))
                Text(title, style = type.titleLg.sized(if (post) 20 else 28, if (post) 24 else 32), color = on, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(verticalArrangement = Arrangement.spacedBy(if (post) 6.dp else 12.dp)) {
                innings.forEach { each ->
                    Column(
                        Modifier.fillMaxWidth().clip(DesiTheme.radius.md).background(colors.ink.copy(alpha = 0.35f))
                            .padding(horizontal = 14.dp, vertical = if (post) 8.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (post) 4.dp else 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(each.battingTeam.name.uppercase(), style = type.titleMd.sized(if (post) 13 else 15, 18), color = on, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(bottom = 4.dp))
                            Text(each.scoreText, style = type.scoreLg.sized(if (post) 22 else 30), color = share.gold)
                            Text(stringResource(Res.string.share_overs_detail, each.oversText), style = type.data.sized(11, 14), color = on.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
                        }
                        PosterRule(on.copy(alpha = 0.15f))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            PerformerColumn(
                                stringResource(Res.string.stats_batting),
                                each.batters.filter { it.balls > 0 || it.runs > 0 }.sortedWith(compareByDescending<com.harsh.desicrics.scoring.BatterCard> { it.runs }.thenBy { it.balls })
                                    .take(shown).map { it.player.name to "${it.runs}${if (it.isOut) "" else "*"} (${it.balls})" },
                                post,
                                Modifier.weight(1f),
                            )
                            PerformerColumn(
                                stringResource(Res.string.stats_bowling),
                                each.bowlers.filter { it.legalBalls > 0 }.sortedWith(compareByDescending<com.harsh.desicrics.scoring.BowlerCard> { it.wickets }.thenBy { it.runs })
                                    .take(shown).map { it.player.name to "${it.wickets}/${it.runs}" },
                                post,
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(if (post) 8.dp else 12.dp)) {
                PosterResultStrip(result, post)
                PosterFooter(on.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun PerformerColumn(heading: String, lines: List<Pair<String, String>>, post: Boolean, modifier: Modifier) {
    val type = DesiTheme.type
    val on = DesiTheme.colors.onBrandStrong
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (post) 1.dp else 3.dp)) {
        Text(heading.uppercase(), style = type.label.sized(10, 12), color = on.copy(alpha = 0.65f))
        lines.forEach { (name, figures) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = type.body.sized(if (post) 12 else 13, 18), color = on, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(figures, style = type.data.sized(if (post) 12 else 13, 18).copy(fontWeight = type.bodyStrong.fontWeight), color = on)
            }
        }
    }
}

// ---- Player of the match · Spotlight ----

/** The player in a gold spotlight: their photo or initials, name and team, and what they did. */
@Composable
fun SpotlightTemplate(figures: SpotlightFigures, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val on = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Box(Modifier.fillMaxSize()) {
        // The spotlight: a gold glow behind the player, and rings spreading out from it.
        Canvas(Modifier.fillMaxSize().background(colors.brandStrong)) {
            val center = if (post) Offset(size.width * 0.24f, size.height * 0.45f) else Offset(size.width / 2f, size.height * 0.36f)
            val glow = size.width * if (post) 0.42f else 0.62f
            drawCircle(Brush.radialGradient(listOf(share.gold.copy(alpha = 0.42f), share.gold.copy(alpha = 0f)), center, glow), glow, center)
            for (ring in 1..4) drawCircle(on.copy(alpha = 0.07f), glow * (0.5f + ring * 0.22f), center, style = Stroke(1.5.dp.toPx()))
        }
        if (post) {
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                SpotlightHeading()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PosterAvatar(figures.name, figures.photoUrl, 108.dp, colors.brandStrong, on, ring = share.gold)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(figures.name, style = type.titleLg.sized(26, 30), color = on, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(figures.team.uppercase(), style = type.label.sized(11, 14), color = on.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                SpotlightStats(figures.stats, post = true)
                PosterFooter(on.copy(alpha = 0.85f))
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                SpotlightHeading()
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PosterAvatar(figures.name, figures.photoUrl, 150.dp, colors.brandStrong, on, ring = share.gold)
                    Spacer(Modifier.height(4.dp))
                    Text(figures.name, style = type.titleLg.sized(38, 42), color = on, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(figures.team.uppercase(), style = type.label.sized(13, 16), color = on.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SpotlightStats(figures.stats, post = false)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(figures.result, style = type.bodyStrong.sized(14, 19), color = share.gold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    PosterFooter(on.copy(alpha = 0.85f), caption = figures.caption)
                }
            }
        }
    }
}

@Composable
private fun SpotlightHeading() {
    val share = DesiTheme.colors.share
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(DesiIcons.TrophyFilled), null, tint = share.gold, modifier = Modifier.size(22.dp))
        Text(stringResource(Res.string.poster_player_of_the_match).uppercase(), style = DesiTheme.type.label.sized(15, 18), color = share.gold)
    }
}

/** The player's figures as tiles, best first; the first is in gold. */
@Composable
private fun SpotlightStats(stats: List<ShareStat>, post: Boolean) {
    val colors = DesiTheme.colors
    val type = DesiTheme.type
    val on = colors.onBrandStrong
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEachIndexed { i, stat ->
            Column(
                Modifier.weight(1f).clip(DesiTheme.radius.md).background(if (i == 0) colors.share.gold else colors.ink.copy(alpha = 0.35f))
                    .padding(vertical = if (post) 8.dp else 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ink = if (i == 0) colors.share.onGold else on
                Text(stat.value, style = type.scoreLg.sized(if (post) 22 else 28, if (post) 26 else 32), color = ink, maxLines = 1)
                Text(stat.label.uppercase(), style = type.label.sized(10, 12), color = ink.copy(alpha = 0.75f), maxLines = 1)
            }
        }
    }
}

// ---- Player of the match · Overlay ----

/** White text for the user's own photo of the player: the title, the name, and the figures. */
@Composable
fun PlayerOfTheMatchOverlay(figures: SpotlightFigures, format: ShareFormat) = OverlayFrame(figures.caption, format) {
    val type = DesiTheme.type
    val post = format == ShareFormat.POST
    Text(stringResource(Res.string.poster_player_of_the_match).uppercase(), style = type.label.sized(if (post) 13 else 15, 18).overlay(0.9f))
    Text(figures.name, style = type.titleLg.sized(if (post) 30 else 40, if (post) 34 else 44).overlay(), maxLines = 2, overflow = TextOverflow.Ellipsis)
    figures.stats.firstOrNull()?.let { lead ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(lead.value, style = type.scoreXl.sized(if (post) 64 else 96).overlay(), modifier = Modifier.alignByBaseline())
            Text(lead.label.uppercase(), style = type.label.sized(14, 18).overlay(0.85f), modifier = Modifier.alignByBaseline())
        }
    }
    if (figures.stats.size > 1) OverlayStats(figures.stats.drop(1))
    Text(figures.result, style = type.bodyStrong.sized(if (post) 15 else 18, if (post) 20 else 24).overlay(), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
}
