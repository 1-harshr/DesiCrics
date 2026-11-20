package com.harsh.desicrics.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.resources.Res
import com.harsh.desicrics.designsystem.resources.ds_theme_classic

/** Crease, the app's original look: turf green, a scoreboard condensed face and soft rounded cards. */
val ClassicTheme: DesiThemeSpec = DesiThemeSpec(
    id = "classic",
    name = Res.string.ds_theme_classic,
    light = LightDesiColors,
    dark = DarkDesiColors,
    typography = { desiTypography() },
    shapes = DesiShapes(
        small = DesiRadius.sm,
        medium = DesiRadius.md,
        large = DesiRadius.lg,
        pill = CircleShape,
        // A third of the tile's side, so small and large tiles keep the same look.
        tile = RoundedCornerShape(percent = 30),
        border = 1.dp,
        borderControl = 1.5.dp,
        borderFocus = 2.dp,
    ),
    motifs = DesiMotifs(
        stumps = { drawClassicStumps(it) },
        ball = { drawClassicBall(it) },
        bails = { drawClassicBails(it) },
    ),
    surface = { area, colors -> if (area == SurfaceArea.Hero) drawPitchCircles(colors) },
)

/** Faint fielding circles in the hero's top corner. */
private fun DrawScope.drawPitchCircles(colors: DesiColors) {
    val ring = colors.onBrandStrong.copy(alpha = 0.07f)
    val centre = Offset(size.width * 0.92f, size.height * 0.05f)
    drawCircle(ring, radius = size.width * 0.42f, center = centre, style = Stroke(width = 2.dp.toPx()))
    drawCircle(ring, radius = size.width * 0.28f, center = centre, style = Stroke(width = 2.dp.toPx()))
    drawCircle(ring.copy(alpha = 0.05f), radius = size.width * 0.14f, center = centre)
}

/** Three stumps in [DesiColors.muted] topped by brand-green bails. */
private fun DrawScope.drawClassicStumps(colors: DesiColors) {
    val stroke = size.minDimension * 0.08f
    val top = size.height * 0.22f
    val bottom = size.height * 0.92f
    val xs = listOf(0.3f, 0.5f, 0.7f).map { it * size.width }
    xs.forEach { x -> drawLine(colors.muted, Offset(x, top), Offset(x, bottom), stroke, StrokeCap.Round) }
    val bailY = size.height * 0.13f
    drawLine(colors.brand, Offset(xs[0], bailY), Offset(xs[1] - stroke * 0.4f, bailY), stroke * 0.8f, StrokeCap.Round)
    drawLine(colors.brand, Offset(xs[1] + stroke * 0.4f, bailY), Offset(xs[2], bailY), stroke * 0.8f, StrokeCap.Round)
}

/** A brand-green ball with two seams bowing the same way in [DesiColors.onBrand], as on the app icon. */
private fun DrawScope.drawClassicBall(colors: DesiColors) {
    val r = size.minDimension * 0.42f
    val c = center
    drawCircle(colors.brand, r, c)
    val seam = Stroke(width = r * 0.11f, cap = StrokeCap.Round)
    for (x in floatArrayOf(-0.17f, 0.17f)) {
        val path = Path().apply {
            moveTo(c.x + x * r, c.y - 0.92f * r)
            quadraticTo(c.x + (x - 0.3f) * r, c.y, c.x + x * r, c.y + 0.92f * r)
        }
        drawPath(path, colors.onBrand, style = seam)
    }
}

/** Two bails lying side by side, in brand green. */
private fun DrawScope.drawClassicBails(colors: DesiColors) {
    val stroke = size.minDimension * 0.12f
    val y = center.y
    drawLine(colors.brand, Offset(size.width * 0.15f, y), Offset(size.width * 0.42f, y), stroke, StrokeCap.Round)
    drawLine(colors.brand, Offset(size.width * 0.58f, y), Offset(size.width * 0.85f, y), stroke, StrokeCap.Round)
}
