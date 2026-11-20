package com.harsh.desicrics.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bracket: the knockouts on brand green, a gold stage label over each round's matches and the final in a
 * gold frame. Stories run top to bottom; posts run left to right like a printed draw. Rounds still to be
 * made show as empty boxes.
 */
@Composable
fun BracketTreePoster(poster: BracketPoster, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Box(Modifier.fillMaxSize().background(colors.brandStrong)) {
        Stripes(onGreen)
        Column(Modifier.fillMaxSize().padding(if (post) 18.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (post) 8.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(poster.tournament, share.gold, modifier = Modifier.weight(1f))
                PageBadge(poster.page, share.onGold, share.gold)
            }
            Text(stringResource(Res.string.poster_content_bracket).uppercase(), style = type.scoreXl.sized(if (post) 34 else 48), color = onGreen, maxLines = 1)
            if (post) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    poster.stages.forEach { stage ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            StageLabel(stage.name, small = true)
                            Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                                stage.slots.forEach { BracketBox(it, small = true, final = stage.slots.size == 1 && stage === poster.stages.last()) }
                            }
                        }
                    }
                }
                poster.champion?.let { ChampionLine(it, small = true) }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    poster.stages.forEachIndexed { i, stage ->
                        if (i > 0) StageJoin()
                        StageLabel(stage.name, small = false)
                        Spacer(Modifier.height(2.dp))
                        val final = stage.slots.size == 1 && i == poster.stages.lastIndex
                        stage.slots.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(if (final) 0.6f else 1f).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { BracketBox(it, small = false, final = final, modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                    poster.champion?.let {
                        Spacer(Modifier.height(6.dp))
                        ChampionLine(it, small = false)
                    }
                }
            }
            PosterFooter(onGreen)
        }
    }
}

/** A gold pill naming the round. */
@Composable
private fun StageLabel(name: String, small: Boolean) {
    val share = DesiTheme.colors.share
    Box(Modifier.clip(DesiTheme.radius.full).background(share.gold).padding(horizontal = if (small) 8.dp else 12.dp, vertical = if (small) 2.dp else 4.dp)) {
        // Tighter letters on posts, so "Quarter-final" fits its column.
        val style = DesiTheme.type.label.sized(if (small) 10 else 12, if (small) 13 else 16).let { if (small) it.copy(letterSpacing = 0.2.sp) else it }
        Text(name.uppercase(), style = style, color = share.onGold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A short gold line from one round down to the next. */
@Composable
private fun StageJoin() {
    val share = DesiTheme.colors.share
    Box(Modifier.width(2.dp).height(12.dp).background(share.gold.copy(alpha = 0.7f)))
}

/** One match in the bracket: both teams with scores once it's played, the winner bright and marked in gold. */
@Composable
private fun BracketBox(slot: BracketSlot, small: Boolean, final: Boolean, modifier: Modifier = Modifier) {
    val colors = DesiTheme.colors
    val share = colors.share
    Column(
        modifier.fillMaxWidth().clip(DesiTheme.radius.sm).background(colors.ink.copy(alpha = 0.45f))
            .then(if (final) Modifier.border(2.dp, share.gold, DesiTheme.radius.sm) else Modifier)
            .padding(vertical = if (small) 3.dp else 5.dp),
    ) {
        BracketTeam(slot.teamA, slot.scoreA, slot.winner?.let { it == 0 }, small)
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.onBrandStrong.copy(alpha = 0.12f)))
        BracketTeam(slot.teamB, slot.scoreB, slot.winner?.let { it == 1 }, small)
        slot.time?.let {
            Text(
                it.uppercase(),
                style = DesiTheme.type.label.sized(if (small) 8 else 10, if (small) 10 else 13),
                color = share.gold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = if (small) 6.dp else 10.dp, vertical = 1.dp),
            )
        }
    }
}

/** A team's line in a [BracketBox]. [won] is null before the match is played. */
@Composable
private fun BracketTeam(name: String?, score: String?, won: Boolean?, small: Boolean) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val ink = when {
        name == null -> colors.onBrandStrong.copy(alpha = 0.45f)
        won == false -> colors.onBrandStrong.copy(alpha = 0.55f)
        else -> colors.onBrandStrong
    }
    Row(Modifier.fillMaxWidth().height(if (small) 18.dp else 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (won == true) share.gold else Color.Transparent))
        Text(
            name ?: stringResource(Res.string.poster_tbd),
            style = (if (won == true) type.bodyStrong else type.body).sized(if (small) 10 else 13, if (small) 12 else 16),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (small) 5.dp else 8.dp),
        )
        score?.let {
            Text(it, style = type.scoreMd.sized(if (small) 11 else 14), color = if (won == true) share.gold else ink, modifier = Modifier.padding(start = 4.dp, end = if (small) 5.dp else 8.dp))
        }
    }
}

@Composable
private fun ChampionLine(name: String, small: Boolean) {
    val colors = DesiTheme.colors
    val share = colors.share
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(DesiIcons.TrophyFilled), null, tint = share.gold, modifier = Modifier.size(if (small) 18.dp else 26.dp))
        Text(
            stringResource(Res.string.tournament_champion, name).uppercase(),
            style = DesiTheme.type.titleMd.sized(if (small) 14 else 20, if (small) 18 else 24),
            color = colors.onBrandStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Road to the final: both finalists side by side on dark, each with their mark and the knockout wins that
 * got them there, the two paths meeting at the final at the foot.
 */
@Composable
fun RoadToFinalPoster(poster: RoadPoster, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onDark = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Column(
        Modifier.fillMaxSize().background(colors.ink).padding(if (post) 18.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (post) 6.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Eyebrow(poster.tournament, share.gold)
        Text(
            stringResource(Res.string.poster_template_road).uppercase(),
            style = type.scoreXl.sized(if (post) 28 else 40, if (post) 30 else 42),
            color = onDark,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (post) 10.dp else 14.dp)) {
            poster.sides.forEach { side -> RoadColumn(side, post, Modifier.weight(1f)) }
        }
        // The two paths meet at the final.
        Canvas(Modifier.fillMaxWidth().height(if (post) 10.dp else 24.dp)) {
            val stroke = 2.dp.toPx()
            val path = Path().apply {
                moveTo(size.width * 0.25f, 0f)
                lineTo(size.width * 0.25f, size.height * 0.5f)
                lineTo(size.width * 0.75f, size.height * 0.5f)
                lineTo(size.width * 0.75f, 0f)
                moveTo(size.width * 0.5f, size.height * 0.5f)
                lineTo(size.width * 0.5f, size.height)
            }
            drawPath(path, share.gold, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(
            Modifier.fillMaxWidth().clip(DesiTheme.radius.md).background(share.gold).padding(horizontal = 16.dp, vertical = if (post) 6.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.stage_final).uppercase(), style = type.scoreXl.sized(if (post) 18 else 32), color = share.onGold)
            Text(
                poster.sides.joinToString(" ${stringResource(Res.string.poster_vs_short)} ") { it.team },
                style = type.titleMd.sized(if (post) 14 else 17, if (post) 18 else 22),
                color = share.onGold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            (poster.champion?.let { stringResource(Res.string.tournament_champion, it) } ?: poster.finalLine)?.let {
                Text(it.uppercase(), style = type.label.sized(if (post) 11 else 13, 16), color = share.onGold.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        PosterFooter(onDark)
    }
}

@Composable
private fun RoadColumn(side: RoadSide, post: Boolean, modifier: Modifier) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onDark = colors.onBrandStrong
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (post) 4.dp else 8.dp)) {
        TeamMark(side.team, if (post) 36.dp else 68.dp, colors.brandStrong, onDark, ring = share.gold)
        Text(side.team.uppercase(), style = type.titleMd.sized(if (post) 14 else 18, if (post) 17 else 22), color = onDark, maxLines = if (post) 1 else 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        side.league?.let { Text(it, style = type.caption, color = onDark.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        RoadSteps(side.steps.takeLast(if (post) 2 else 4), post)
    }
}

@Composable
private fun ColumnScope.RoadSteps(steps: List<RoadStep>, post: Boolean) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onDark = colors.onBrandStrong
    Column(Modifier.weight(1f).fillMaxWidth().padding(top = if (post) 0.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(if (post) 4.dp else 8.dp)) {
        steps.forEach { step ->
            Column(
                Modifier.fillMaxWidth().clip(DesiTheme.radius.sm).background(onDark.copy(alpha = 0.07f)).padding(horizontal = 10.dp, vertical = if (post) 3.dp else 8.dp),
            ) {
                Text(step.stage.uppercase(), style = type.label.sized(if (post) 9 else 11, if (post) 11 else 14), color = share.gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(step.line, style = type.bodyStrong.sized(if (post) 11 else 14, if (post) 14 else 18), color = onDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!post) step.score?.let { Text(it, style = type.data.sized(12, 16), color = onDark.copy(alpha = 0.7f), maxLines = 1) }
            }
        }
    }
}
