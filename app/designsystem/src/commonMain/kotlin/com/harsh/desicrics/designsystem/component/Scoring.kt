package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harsh.desicrics.designsystem.theme.DesiTheme

/** How a delivery is drawn. The caller decides which kind a ball is and what its label says. */
enum class BallKind { Dot, Runs, Four, Six, Wicket, Extra }

@Immutable
data class Ball(val label: String, val kind: BallKind)

/** One delivery as a coloured circle: "4", "W", "Wd", "•". */
@Composable
fun BallChip(ball: Ball, modifier: Modifier = Modifier) {
    val colors = DesiTheme.colors
    val event = colors.event
    val fill: Color
    val outline: Color
    val text: Color
    when (ball.kind) {
        BallKind.Four -> { fill = event.four; outline = event.four; text = event.onEvent }
        BallKind.Six -> { fill = event.six; outline = event.six; text = event.onEvent }
        BallKind.Wicket -> { fill = event.wicket; outline = event.wicket; text = event.onEvent }
        BallKind.Extra -> { fill = colors.surface; outline = event.extra; text = event.extra }
        BallKind.Dot -> { fill = colors.surface; outline = colors.line; text = event.dot }
        BallKind.Runs -> { fill = colors.surface; outline = colors.line; text = colors.ink }
    }
    Box(
        modifier
            .size(DesiTheme.size.ballChip)
            .clip(DesiTheme.radius.full)
            .background(fill)
            .border(1.5.dp, outline, DesiTheme.radius.full),
        contentAlignment = Alignment.Center,
    ) {
        // Two-letter extras ("Wd", "Nb") need a smaller size to fit the 32dp circle.
        val size = if (ball.label.length > 1) 12.sp else 15.sp
        Text(ball.label, style = DesiTheme.type.scoreMd.copy(fontSize = size, lineHeight = 16.sp), color = text, maxLines = 1)
    }
}

/** The balls of the current over, in order. */
@Composable
fun OverStrip(balls: List<Ball>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
    ) {
        balls.forEach { BallChip(it) }
    }
}

/**
 * The big score at the top of a match: a label such as "Andheri Strikers · 2nd inns", the score
 * "142/6", overs "16.4 ov", then an optional line such as "Need 27 off 20 · RRR 8.10".
 */
@Composable
fun ScoreHeader(
    label: String,
    score: String,
    overs: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val colors = DesiTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1)) {
        Text(label.uppercase(), style = DesiTheme.type.label, color = colors.muted, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            Text(score, style = DesiTheme.type.scoreXl, color = colors.ink, maxLines = 1)
            Text(overs, style = DesiTheme.type.data, color = colors.muted, modifier = Modifier.padding(bottom = DesiTheme.space.s2))
        }
        if (detail != null) Text(detail, style = DesiTheme.type.data, color = colors.ink)
    }
}

/** What a run pad key looks like; what it does stays with the caller. */
enum class PadKeyStyle { Runs, Four, Six, Wicket, Extra, Utility }

@Immutable
data class PadKey(val id: String, val label: String, val style: PadKeyStyle = PadKeyStyle.Runs)

/** The 4-column scoring keypad. Keys are 64dp tall, as the thumb-zone rule asks. */
@Composable
fun RunPad(keys: List<PadKey>, onKey: (PadKey) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        keys.chunked(PAD_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                row.forEach { key -> PadKeyButton(key, enabled, { onKey(key) }, Modifier.weight(1f)) }
                repeat(PAD_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

private const val PAD_COLUMNS = 4

@Composable
private fun PadKeyButton(key: PadKey, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = DesiTheme.colors
    val event = colors.event
    val isWicket = key.style == PadKeyStyle.Wicket
    val text = when (key.style) {
        PadKeyStyle.Runs -> colors.ink
        PadKeyStyle.Four -> event.four
        PadKeyStyle.Six -> event.six
        PadKeyStyle.Wicket -> event.onEvent
        PadKeyStyle.Extra -> event.extra
        PadKeyStyle.Utility -> colors.muted
    }
    val style = when (key.style) {
        PadKeyStyle.Runs, PadKeyStyle.Four, PadKeyStyle.Six, PadKeyStyle.Wicket -> DesiTheme.type.scoreMd
        PadKeyStyle.Extra, PadKeyStyle.Utility -> DesiTheme.type.bodyStrong
    }
    Box(
        modifier
            .height(DesiTheme.size.scoringTarget)
            .clip(DesiTheme.radius.lg)
            .background(if (isWicket) event.wicket else colors.surface)
            .border(1.dp, if (isWicket) event.wicket else colors.line, DesiTheme.radius.lg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(key.label, style = style, color = if (enabled) text else colors.muted, textAlign = TextAlign.Center)
    }
}
