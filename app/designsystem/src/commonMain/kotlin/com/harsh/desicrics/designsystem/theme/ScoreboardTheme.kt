package com.harsh.desicrics.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.resources.Res
import com.harsh.desicrics.designsystem.resources.antonio_variable
import com.harsh.desicrics.designsystem.resources.ds_theme_scoreboard
import org.jetbrains.compose.resources.Font

/** Square corners on everything, like the tins hung on a manual scoreboard. */
private val Square = RoundedCornerShape(0.dp)

/**
 * Scoreboard, the heritage manual board: black-green painted boards, white numerals on square tins and
 * gold lettering, with square corners everywhere. Light mode is the same board painted white, in green ink.
 */
val ScoreboardTheme: DesiThemeSpec = DesiThemeSpec(
    id = "scoreboard",
    name = Res.string.ds_theme_scoreboard,
    light = DesiColors(
        bg = Color(0xFFF1F0E8),
        surface = Color(0xFFFBFAF4),
        sunk = Color(0xFFE7E6DB),
        ink = Color(0xFF0F2A1F),
        muted = Color(0xFF4C5F55),
        line = Color(0xFFD3D5C8),
        brand = Color(0xFF174A33),
        brandSoft = Color(0xFFDCE7DD),
        onBrand = Color(0xFFFBFAF4),
        brandStrong = Color(0xFF10291F),
        onBrandStrong = Color(0xFFFBF8EE),
        event = EventColors(
            four = Color(0xFF1A44A6),
            six = Color(0xFF5A2A9C),
            wicket = Color(0xFFA3141E),
            extra = Color(0xFF0E5E63),
            dot = Color(0xFF879089),
            onEvent = Color(0xFFFFFFFF),
        ),
        isDark = false,
    ),
    dark = DesiColors(
        bg = Color(0xFF0C1813),
        surface = Color(0xFF13221B),
        sunk = Color(0xFF1A2C23),
        ink = Color(0xFFF3F1E6),
        muted = Color(0xFFA8B7AD),
        line = Color(0xFF2A3E33),
        // Gold lettering.
        brand = Color(0xFFE2BC62),
        brandSoft = Color(0xFF1F3B2D),
        onBrand = Color(0xFF11201A),
        brandStrong = Color(0xFF123425),
        onBrandStrong = Color(0xFFF6F2E4),
        // Light fills with dark text, so outcome chips and outcome-coloured keys both read on the board.
        event = EventColors(
            four = Color(0xFF8DB5FF),
            six = Color(0xFFCDB0FF),
            wicket = Color(0xFFFF8E86),
            extra = Color(0xFF6FD8CC),
            dot = Color(0xFF6C7D73),
            onEvent = Color(0xFF0C1813),
        ),
        isDark = true,
    ),
    typography = { desiTypography(antonioFamily(), figtreeFamily(), jetBrainsMonoFamily()) },
    shapes = DesiShapes(
        small = Square,
        medium = Square,
        large = Square,
        pill = Square,
        tile = Square,
        border = 1.5.dp,
        borderControl = 2.dp,
        borderFocus = 2.5.dp,
    ),
    motifs = DesiMotifs(
        stumps = { drawScoreboardStumps(it) },
        ball = { drawScoreboardBall(it) },
        bails = { drawScoreboardBails(it) },
    ),
    surface = { area, colors -> drawBoardPanels(area, colors) },
)

/** Antonio: Scoreboard's display face, tall condensed numerals like painted tins, tabular as drawn. */
@Composable
fun antonioFamily(): FontFamily = FontFamily(
    Font(Res.font.antonio_variable, FontWeight.SemiBold),
    Font(Res.font.antonio_variable, FontWeight.Bold),
)

/**
 * Painted boards: horizontal plank seams and a thin frame inset from the edge. Behind the live score the
 * seams are closer, with upright joints, so the score sits on a grid of tins.
 */
private fun DrawScope.drawBoardPanels(area: SurfaceArea, colors: DesiColors) {
    val shadow = Color.Black.copy(alpha = 0.22f)
    val catch = colors.onBrandStrong.copy(alpha = 0.06f)
    val seam = 1.dp.toPx()
    val plank = (if (area == SurfaceArea.Scoreboard) 36 else 28).dp.toPx()
    var y = plank
    while (y < size.height - seam) {
        drawLine(shadow, Offset(0f, y), Offset(size.width, y), seam)
        drawLine(catch, Offset(0f, y + seam), Offset(size.width, y + seam), seam)
        y += plank
    }
    if (area == SurfaceArea.Scoreboard) {
        val joint = 48.dp.toPx()
        var x = joint
        while (x < size.width) {
            drawLine(shadow.copy(alpha = 0.14f), Offset(x, 0f), Offset(x, size.height), seam)
            x += joint
        }
    }
    val inset = 5.dp.toPx()
    drawRect(
        colors.onBrandStrong.copy(alpha = 0.12f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        style = Stroke(width = seam * 1.5f),
    )
}

/** Three square-cut stumps in [DesiColors.ink] under two flat bails in the brand colour. */
private fun DrawScope.drawScoreboardStumps(colors: DesiColors) {
    val w = size.minDimension * 0.1f
    val top = size.height * 0.24f
    val bottom = size.height * 0.92f
    val xs = listOf(0.28f, 0.5f, 0.72f).map { it * size.width }
    xs.forEach { x -> drawRect(colors.ink, Offset(x - w / 2, top), Size(w, bottom - top)) }
    val bailTop = size.height * 0.12f
    val bailH = w * 0.7f
    val gap = w * 0.25f
    drawRect(colors.brand, Offset(xs[0] - w / 2, bailTop), Size(xs[1] - xs[0] - gap / 2, bailH))
    drawRect(colors.brand, Offset(xs[1] + gap / 2, bailTop), Size(xs[2] - xs[1] + w / 2 - gap / 2, bailH))
}

/** A ball painted on a square tin: the board's green with a framed edge, a white ball and its seam. */
private fun DrawScope.drawScoreboardBall(colors: DesiColors) {
    val side = size.minDimension * 0.9f
    val topLeft = Offset(center.x - side / 2, center.y - side / 2)
    drawRect(colors.brandStrong, topLeft, Size(side, side))
    val edge = side * 0.05f
    drawRect(
        colors.onBrandStrong.copy(alpha = 0.35f),
        Offset(topLeft.x + edge, topLeft.y + edge),
        Size(side - edge * 2, side - edge * 2),
        style = Stroke(width = edge * 0.6f),
    )
    val r = side * 0.3f
    drawCircle(colors.onBrandStrong, r, center)
    drawLine(colors.brandStrong, Offset(center.x, center.y - r), Offset(center.x, center.y + r), r * 0.14f, StrokeCap.Butt)
}

/** Two flat bails with square spigots, in the brand colour. */
private fun DrawScope.drawScoreboardBails(colors: DesiColors) {
    val h = size.minDimension * 0.14f
    val y = center.y - h / 2
    listOf(0.12f to 0.46f, 0.54f to 0.88f).forEach { (a, b) ->
        val start = size.width * a
        val end = size.width * b
        val spigot = (end - start) * 0.15f
        drawRect(colors.brand, Offset(start, y + h * 0.3f), Size(end - start, h * 0.4f))
        drawRect(colors.brand, Offset(start + spigot, y), Size(end - start - spigot * 2, h))
    }
}
