package com.harsh.desicrics.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.resources.Res
import com.harsh.desicrics.designsystem.resources.doto_variable
import com.harsh.desicrics.designsystem.resources.ds_theme_floodlights
import com.harsh.desicrics.designsystem.resources.oxanium_variable
import org.jetbrains.compose.resources.Font

/**
 * Floodlights, T20 under lights: a stadium navy-black, fluoro-yellow LEDs, a dot-matrix face for the big
 * scores and broadcast-style cut corners. Dark is the home mode; light is the same ground on a day game,
 * navy boards with yellow lettering on white.
 */
val FloodlightsTheme: DesiThemeSpec = DesiThemeSpec(
    id = "floodlights",
    name = Res.string.ds_theme_floodlights,
    light = DesiColors(
        bg = Color(0xFFF3F5FB),
        surface = Color(0xFFFFFFFF),
        sunk = Color(0xFFE8ECF6),
        ink = Color(0xFF0A1024),
        muted = Color(0xFF4A5574),
        line = Color(0xFFD9DFEE),
        brand = Color(0xFF141B4D),
        brandSoft = Color(0xFFFBF7C2),
        onBrand = Color(0xFFF2EC2A),
        brandStrong = Color(0xFF0A0F2A),
        onBrandStrong = Color(0xFFF2EC2A),
        event = EventColors(
            four = Color(0xFF0050AD),
            six = Color(0xFFA3127F),
            wicket = Color(0xFFAD0F2B),
            extra = Color(0xFF8F4300),
            dot = Color(0xFF7E88A6),
            onEvent = Color(0xFFFFFFFF),
        ),
        isDark = false,
    ),
    dark = DesiColors(
        bg = Color(0xFF060912),
        surface = Color(0xFF0E1427),
        sunk = Color(0xFF172038),
        ink = Color(0xFFF4F6FF),
        muted = Color(0xFFA3ADCB),
        line = Color(0xFF243052),
        brand = Color(0xFFF5EF2E),
        brandSoft = Color(0xFF2B2C12),
        onBrand = Color(0xFF0A0D18),
        brandStrong = Color(0xFF03050C),
        onBrandStrong = Color(0xFFF5EF2E),
        // Bright LED fills under lights, so their text is the stadium's near-black.
        event = EventColors(
            four = Color(0xFF3FCBFF),
            six = Color(0xFFFF78DC),
            wicket = Color(0xFFFF7A6E),
            extra = Color(0xFFFFA43A),
            dot = Color(0xFF5E6A8E),
            onEvent = Color(0xFF060912),
        ),
        isDark = true,
    ),
    typography = { floodlightsTypography() },
    shapes = DesiShapes(
        small = CutCornerShape(4.dp),
        medium = CutCornerShape(topEnd = 10.dp, bottomStart = 10.dp),
        large = CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp),
        // Ball chips and avatars stay round: they're balls and faces.
        pill = CircleShape,
        tile = CutCornerShape(percent = 22),
        border = 1.dp,
        borderControl = 1.5.dp,
        borderFocus = 2.dp,
    ),
    motifs = DesiMotifs(
        stumps = { drawLedStumps(it) },
        ball = { drawWhiteBall(it) },
        bails = { drawLitBails(it) },
    ),
    surface = { area, colors ->
        when (area) {
            SurfaceArea.Hero -> drawFloodlightBeams(colors)
            SurfaceArea.Scoreboard -> drawLedGrid(colors)
        }
    },
)

/** Doto: a dot-matrix face with fixed-width digits, used only for the score header's numerals. Round dots, like LEDs. */
@Composable
fun dotoFamily(): FontFamily = FontFamily(
    Font(Res.font.doto_variable, FontWeight.SemiBold, variationSettings = dotoSettings(700)),
    // The heaviest dots, so the score stays solid from across the ground.
    Font(Res.font.doto_variable, FontWeight.Bold, variationSettings = dotoSettings(900)),
)

private fun dotoSettings(weight: Int) = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("ROND", 100f))

/** Oxanium: Floodlights' squared, broadcast-graphics face for titles and smaller scores. Its digits are tabular. */
@Composable
fun oxaniumFamily(): FontFamily = FontFamily(
    Font(Res.font.oxanium_variable, FontWeight.SemiBold),
    Font(Res.font.oxanium_variable, FontWeight.Bold),
)

/**
 * Oxanium throughout, with the biggest score in Doto's LED dots. Only that one: Doto's decimal point is a
 * cluster that reads as a plus at smaller sizes, and averages and rates use the smaller styles.
 */
@Composable
private fun floodlightsTypography(): DesiTypography {
    val base = desiTypography(oxaniumFamily(), figtreeFamily(), jetBrainsMonoFamily())
    val dots = dotoFamily()
    return base.copy(
        scoreXl = base.scoreXl.copy(fontFamily = dots),
    )
}

/** The yellow that glows: the brand on a dark ground, the yellow lettering on a light one. */
private val DesiColors.led: Color get() = if (isDark) brand else onBrand

/** Two light towers in the top corners throwing faint cones across the hero. */
private fun DrawScope.drawFloodlightBeams(colors: DesiColors) {
    val light = colors.onBrandStrong
    val w = size.width
    val h = size.height
    listOf(Offset(w * 0.06f, 0f) to 1f, Offset(w * 0.94f, 0f) to -1f).forEach { (lamp, dir) ->
        val beam = Path().apply {
            moveTo(lamp.x, lamp.y)
            lineTo(lamp.x + dir * w * 0.62f, h)
            lineTo(lamp.x + dir * w * 0.22f, h)
            close()
        }
        drawPath(beam, Brush.radialGradient(listOf(light.copy(alpha = 0.10f), Color.Transparent), center = lamp, radius = h * 1.1f))
        // The lamp bank: a small grid of bulbs.
        val bulb = 2.dp.toPx()
        val gap = 6.dp.toPx()
        for (row in 0..1) for (col in -1..1) {
            drawCircle(light.copy(alpha = 0.35f), bulb, Offset(lamp.x + col * gap + dir * gap * 1.5f, gap * (row + 1)))
        }
    }
}

/** Unlit LEDs: a faint grid of dots behind the score, as on a stadium screen. */
private fun DrawScope.drawLedGrid(colors: DesiColors) {
    val dot = colors.onBrandStrong.copy(alpha = 0.07f)
    val pitch = 6.dp.toPx()
    val r = 1.2.dp.toPx()
    var y = pitch / 2
    while (y < size.height) {
        var x = pitch / 2
        while (x < size.width) {
            drawCircle(dot, r, Offset(x, y))
            x += pitch
        }
        y += pitch
    }
}

/** Three stumps built from LED dots, topped by lit bails. */
private fun DrawScope.drawLedStumps(colors: DesiColors) {
    val r = size.minDimension * 0.035f
    val top = size.height * 0.26f
    val bottom = size.height * 0.92f
    val xs = listOf(0.3f, 0.5f, 0.7f).map { it * size.width }
    val steps = 7
    xs.forEach { x ->
        for (i in 0..steps) drawCircle(colors.muted, r, Offset(x, top + (bottom - top) * i / steps))
    }
    val bailY = size.height * 0.15f
    val stroke = size.minDimension * 0.07f
    drawGlow(colors, Offset(size.width * 0.5f, bailY), size.width * 0.32f)
    drawLine(colors.led.bailOn(colors), Offset(xs[0], bailY), Offset(xs[1] - stroke * 0.6f, bailY), stroke, StrokeCap.Round)
    drawLine(colors.led.bailOn(colors), Offset(xs[1] + stroke * 0.6f, bailY), Offset(xs[2], bailY), stroke, StrokeCap.Round)
}

/** The white ball of the short game, its seam stitched in dots and a halo of light around it. */
private fun DrawScope.drawWhiteBall(colors: DesiColors) {
    val r = size.minDimension * 0.38f
    val c = center
    drawGlow(colors, c, r * 1.3f)
    val ball = Color(0xFFF7F8FC)
    drawCircle(ball, r, c)
    drawCircle(colors.brand, r, c, style = Stroke(width = r * 0.08f))
    // Two seams bowing the same way, as on the app icon, stitched in dots.
    val stitch = r * 0.07f
    for (x in floatArrayOf(-0.2f, 0.2f)) {
        val start = Offset(c.x + x * r, c.y - 0.86f * r)
        val control = Offset(c.x + (x - 0.34f) * r, c.y)
        val end = Offset(c.x + x * r, c.y + 0.86f * r)
        for (i in 0..8) {
            val t = i / 8f
            val u = 1 - t
            drawCircle(colors.brand, stitch, start * (u * u) + control * (2 * u * t) + end * (t * t))
        }
    }
}

/** Two bails lit up the moment they're hit, with sparks. */
private fun DrawScope.drawLitBails(colors: DesiColors) {
    val stroke = size.minDimension * 0.12f
    val y = center.y
    drawGlow(colors, center, size.width * 0.45f)
    val bail = colors.led.bailOn(colors)
    drawLine(bail, Offset(size.width * 0.15f, y), Offset(size.width * 0.42f, y), stroke, StrokeCap.Round)
    drawLine(bail, Offset(size.width * 0.58f, y), Offset(size.width * 0.85f, y), stroke, StrokeCap.Round)
    val spark = colors.led
    val sparkStroke = size.minDimension * 0.04f
    listOf(-0.6f to -1f, 0f to -1.15f, 0.6f to -1f).forEach { (dx, dy) ->
        val from = Offset(center.x + dx * size.width * 0.12f, y + dy * size.height * 0.16f)
        val to = Offset(center.x + dx * size.width * 0.2f, y + dy * size.height * 0.3f)
        drawLine(spark, from, to, sparkStroke, StrokeCap.Round)
    }
}

/** Yellow bails on a dark ground; navy bails on a light one, where yellow alone would vanish. */
private fun Color.bailOn(colors: DesiColors): Color = if (colors.isDark) this else colors.brand

/** A soft yellow halo. */
private fun DrawScope.drawGlow(colors: DesiColors, at: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(colors.led.copy(alpha = if (colors.isDark) 0.35f else 0.55f), Color.Transparent), at, radius), radius, at)
}
