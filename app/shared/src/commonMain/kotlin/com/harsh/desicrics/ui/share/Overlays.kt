package com.harsh.desicrics.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.icon.DesiIllustrations
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A batter's innings for the batting overlay. [zones] are runs per sector in `FieldZone.Clockwise` order. */
@Immutable
data class BattingFigures(
    val name: String,
    val runs: Int,
    val balls: Int,
    val notOut: Boolean,
    val strikeRate: String,
    val fours: Int,
    val sixes: Int,
    val zones: List<Int>?,
    val caption: String,
)

/** A bowler's spell for the bowling overlay; [wickets] are "Rohan · c Esha b Dev" lines. */
@Immutable
data class BowlingFigures(
    val name: String,
    val figures: String,
    val overs: String,
    val economy: String,
    val maidens: Int,
    val wickets: List<String>,
    val caption: String,
)

/** A match for the result overlay. */
@Immutable
data class ResultFigures(
    val innings: List<ShareHeadline>,
    val result: String,
    val playerOfTheMatch: String?,
    val caption: String,
)

/** A season for the season overlay: the lead number (runs or wickets), stats and recent form, oldest first. */
@Immutable
data class SeasonFigures(
    val name: String,
    val lead: ShareStat,
    val stats: List<ShareStat>,
    val form: List<Int>,
    val caption: String,
)

@Composable
fun BattingOverlay(figures: BattingFigures, format: ShareFormat) = OverlayFrame(figures.caption, format) {
    val type = DesiTheme.type
    val post = format == ShareFormat.POST
    Text(figures.name, style = type.titleLg.sized(if (post) 28 else 34, if (post) 32 else 38).overlay(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${figures.runs}${if (figures.notOut) "*" else ""}", style = type.scoreXl.sized(if (post) 80 else 120).overlay(), modifier = Modifier.alignByBaseline())
                Text("(${figures.balls})", style = type.scoreMd.sized(30, 34).overlay(0.85f), modifier = Modifier.alignByBaseline())
            }
            OverlayStats(
                listOf(
                    ShareStat(figures.strikeRate, stringResource(Res.string.stats_strike_rate)),
                    ShareStat("${figures.fours}", stringResource(Res.string.stats_fours)),
                    ShareStat("${figures.sixes}", stringResource(Res.string.stats_sixes)),
                ),
            )
        }
        if (post) figures.zones?.let { OutlineWagonWheel(it, Modifier.size(120.dp)) }
    }
    if (!post) figures.zones?.let { OutlineWagonWheel(it, Modifier.padding(top = 8.dp).size(150.dp)) }
}

@Composable
fun BowlingOverlay(figures: BowlingFigures, format: ShareFormat) = OverlayFrame(figures.caption, format) {
    val type = DesiTheme.type
    val post = format == ShareFormat.POST
    Text(figures.name, style = type.titleLg.sized(if (post) 28 else 34, if (post) 32 else 38).overlay(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(figures.figures, style = type.scoreXl.sized(if (post) 80 else 120).overlay())
    OverlayStats(
        listOf(
            ShareStat(figures.overs, stringResource(Res.string.stats_overs)),
            ShareStat(figures.economy, stringResource(Res.string.stats_economy)),
            ShareStat("${figures.maidens}", stringResource(Res.string.share_maidens)),
        ),
    )
    if (figures.wickets.isNotEmpty()) {
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            figures.wickets.take(if (post) 3 else 8).forEach {
                Text(it, style = type.body.sized(16, 22).overlay(0.95f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ResultOverlay(figures: ResultFigures, format: ShareFormat) = OverlayFrame(figures.caption, format) {
    val type = DesiTheme.type
    val post = format == ShareFormat.POST
    figures.innings.forEach { line ->
        Column {
            Text(line.label, style = type.titleMd.sized(if (post) 20 else 24, if (post) 24 else 28).overlay(0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.value, style = type.scoreXl.sized(if (post) 48 else 76).overlay(), modifier = Modifier.alignByBaseline())
                line.detail?.let { Text(it, style = type.data.sized(16, 20).overlay(0.85f), modifier = Modifier.alignByBaseline()) }
            }
        }
    }
    OverlayRule()
    Text(figures.result, style = type.bodyStrong.sized(if (post) 18 else 22, if (post) 24 else 28).overlay(), maxLines = 2, overflow = TextOverflow.Ellipsis)
    figures.playerOfTheMatch?.let {
        Text(stringResource(Res.string.scorecard_player_of_the_match, it), style = type.body.sized(16, 22).overlay(0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SeasonOverlay(figures: SeasonFigures, format: ShareFormat) = OverlayFrame(figures.caption, format) {
    val type = DesiTheme.type
    val post = format == ShareFormat.POST
    Text(figures.name, style = type.titleLg.sized(if (post) 28 else 34, if (post) 32 else 38).overlay(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(figures.lead.value, style = type.scoreXl.sized(if (post) 80 else 120).overlay(), modifier = Modifier.alignByBaseline())
        Text(figures.lead.label.uppercase(), style = type.label.sized(16, 20).overlay(0.85f), modifier = Modifier.alignByBaseline())
    }
    OverlayStats(figures.stats)
    if (figures.form.size >= 2) {
        if (!post) Text(stringResource(Res.string.share_form).uppercase(), style = type.label.overlay(0.85f), modifier = Modifier.padding(top = 8.dp))
        FormSparkline(figures.form, Modifier.fillMaxWidth().height(if (post) 36.dp else 64.dp))
    }
}

/**
 * Every overlay's frame: [content] centred on the canvas over nothing at all, then the DesiCrics mark and
 * the match or season name in small type.
 */
@Composable
internal fun OverlayFrame(caption: String, format: ShareFormat, content: @Composable ColumnScope.() -> Unit) {
    val type = DesiTheme.type
    val share = DesiTheme.colors.share
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = if (format == ShareFormat.POST) 24.dp else 48.dp),
        verticalArrangement = Arrangement.spacedBy(if (format == ShareFormat.POST) 4.dp else 8.dp, Alignment.CenterVertically),
    ) {
        content()
        Row(Modifier.padding(top = if (format == ShareFormat.POST) 8.dp else 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(DesiIllustrations.BallMark), contentDescription = null, tint = share.overlayInk, modifier = Modifier.size(22.dp))
            Text(stringResource(Res.string.app_name).uppercase(), style = type.label.sized(13, 16).overlay())
            Text(caption, style = type.caption.overlay(0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun OverlayStats(stats: List<ShareStat>) {
    val type = DesiTheme.type
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        stats.forEach { stat ->
            Column {
                Text(stat.label.uppercase(), style = type.label.overlay(0.85f))
                Text(stat.value, style = type.scoreMd.sized(30, 34).overlay())
            }
        }
    }
}

/** A short white line between the scores and the result. */
@Composable
private fun OverlayRule(width: Dp = 48.dp) {
    val share = DesiTheme.colors.share
    Canvas(Modifier.padding(vertical = 4.dp).size(width, 3.dp)) {
        val line = Path().apply { moveTo(0f, size.height / 2); lineTo(size.width, size.height / 2) }
        overlayStroke(line, share.overlayInk, share.overlayShadow, size.height)
    }
}

/**
 * The wagon wheel as line art: the boundary, the pitch and a spoke between each sector, with the sectors
 * that got runs filled in white (busier, more opaque) and their runs written in.
 */
@Composable
fun OutlineWagonWheel(values: List<Int>, modifier: Modifier = Modifier) {
    val share = DesiTheme.colors.share
    val measurer = rememberTextMeasurer()
    val countStyle = DesiTheme.type.scoreMd.sized(18, 20).overlay()
    val busiest = values.maxOrNull()?.takeIf { it > 0 } ?: 1
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        val radius = size.minDimension / 2f - stroke * 2
        val center = Offset(size.width / 2f, size.height / 2f)
        val sweep = 360f / values.size
        val bounds = Rect(center, radius)
        values.forEachIndexed { i, runs ->
            if (runs == 0) return@forEachIndexed
            drawArc(share.overlayInk.copy(alpha = 0.12f + 0.4f * runs / busiest), -90f + i * sweep, sweep, useCenter = true, topLeft = bounds.topLeft, size = bounds.size)
        }
        val lines = Path().apply {
            addOval(bounds)
            for (i in values.indices) {
                val angle = (-90f + i * sweep) * PI.toFloat() / 180f
                moveTo(center.x + cos(angle) * radius * 0.16f, center.y + sin(angle) * radius * 0.16f)
                lineTo(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
            }
            // The pitch.
            val pitch = Size(radius * 0.08f, radius * 0.3f)
            addRect(Rect(Offset(center.x - pitch.width / 2, center.y - pitch.height / 2), pitch))
        }
        overlayStroke(lines, share.overlayInk, share.overlayShadow, stroke)
        values.forEachIndexed { i, runs ->
            if (runs == 0) return@forEachIndexed
            val mid = (-90f + (i + 0.5f) * sweep) * PI.toFloat() / 180f
            val text = measurer.measure("$runs", countStyle)
            val at = Offset(center.x + cos(mid) * radius * 0.64f, center.y + sin(mid) * radius * 0.64f)
            drawText(text, topLeft = Offset(at.x - text.size.width / 2f, at.y - text.size.height / 2f))
        }
    }
}

/** Recent scores as a white line with a dot on each, highest at the top; oldest on the left. */
@Composable
fun FormSparkline(values: List<Int>, modifier: Modifier = Modifier) {
    val share = DesiTheme.colors.share
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        val most = values.max().coerceAtLeast(1)
        val step = (size.width - stroke * 4) / (values.size - 1)
        val points = values.mapIndexed { i, v ->
            Offset(stroke * 2 + i * step, size.height - stroke * 2 - (size.height - stroke * 4) * v / most)
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        overlayStroke(line, share.overlayInk, share.overlayShadow, stroke)
        points.forEach {
            drawCircle(share.overlayShadow, stroke * 2.2f, it.copy(y = it.y + stroke * 0.6f))
            drawCircle(share.overlayInk, stroke * 1.6f, it)
        }
    }
}
