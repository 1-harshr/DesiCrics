package com.harsh.desicrics.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.resources.Res
import com.harsh.desicrics.designsystem.resources.antonio_variable
import com.harsh.desicrics.designsystem.resources.ds_theme_gully
import com.harsh.desicrics.designsystem.resources.yatra_one_regular
import org.jetbrains.compose.resources.Font
import kotlin.random.Random

/**
 * Gully, street and tennis-ball cricket: asphalt grey, chalk-white crease lines, a taped-ball yellow and a
 * brick wall for a wicket. Titles are painted like a shop sign; scores are tall and narrow, like a price board.
 */
val GullyTheme: DesiThemeSpec = DesiThemeSpec(
    id = "gully",
    name = Res.string.ds_theme_gully,
    // A sunny afternoon: dusty limewash, asphalt buttons with yellow lettering.
    light = DesiColors(
        bg = Color(0xFFF5F1E6),
        surface = Color(0xFFFFFDF8),
        sunk = Color(0xFFEDE7D8),
        ink = Color(0xFF1C1D20),
        muted = Color(0xFF5A5850),
        line = Color(0xFFE2DACA),
        brand = Color(0xFF2B2D31),
        brandSoft = Color(0xFFFFEFB0),
        onBrand = Color(0xFFFFD43A),
        brandStrong = Color(0xFF2A2C30),
        onBrandStrong = Color(0xFFF6F2E8),
        event = EventColors(
            four = Color(0xFF1D4FB8),
            six = Color(0xFF6A2BB5),
            wicket = Color(0xFFA8162C),
            extra = Color(0xFF7A4A00),
            dot = Color(0xFF8C887C),
            onEvent = Color(0xFFFFFFFF),
        ),
        isDark = false,
    ),
    // Under the streetlight: asphalt at night, the taped ball as the brand.
    dark = DesiColors(
        bg = Color(0xFF161719),
        surface = Color(0xFF1F2023),
        sunk = Color(0xFF2B2C30),
        ink = Color(0xFFF3EFE4),
        muted = Color(0xFFB4AE9F),
        line = Color(0xFF3A3B40),
        brand = Color(0xFFFFD23F),
        brandSoft = Color(0xFF3C3418),
        onBrand = Color(0xFF1A1B1D),
        brandStrong = Color(0xFF2A2B2F),
        onBrandStrong = Color(0xFFF5F1E6),
        event = EventColors(
            four = Color(0xFF6FB0FF),
            six = Color(0xFFC9A0FF),
            wicket = Color(0xFFFF8A85),
            extra = Color(0xFFFF9A1F),
            dot = Color(0xFF726F66),
            onEvent = Color(0xFF161719),
        ),
        isDark = true,
    ),
    typography = { gullyTypography() },
    // Slightly uneven corners, as if cut by hand.
    shapes = DesiShapes(
        small = RoundedCornerShape(topStart = 6.dp, topEnd = 4.dp, bottomEnd = 6.dp, bottomStart = 3.dp),
        medium = RoundedCornerShape(topStart = 12.dp, topEnd = 8.dp, bottomEnd = 12.dp, bottomStart = 6.dp),
        large = RoundedCornerShape(topStart = 22.dp, topEnd = 16.dp, bottomEnd = 22.dp, bottomStart = 14.dp),
        pill = CircleShape,
        tile = RoundedCornerShape(topStartPercent = 34, topEndPercent = 24, bottomEndPercent = 34, bottomStartPercent = 20),
        border = 1.5.dp,
        borderControl = 2.dp,
        borderFocus = 3.dp,
    ),
    motifs = DesiMotifs(
        stumps = { drawWallStumps(it) },
        ball = { drawTapedBall(it) },
        bails = { drawChalkBails(it) },
    ),
    surface = { area, colors -> drawAsphalt(area, colors) },
)

/** Antonio: tall, narrow numbers like a hand-painted price board. Its digits are tabular. */
@Composable
fun antonioFamily(): FontFamily = FontFamily(
    Font(Res.font.antonio_variable, FontWeight.SemiBold),
    Font(Res.font.antonio_variable, FontWeight.Bold),
)

/** Yatra One: hand-painted Indian signage lettering for titles. One weight, standing in for both. */
@Composable
fun yatraOneFamily(): FontFamily = FontFamily(
    Font(Res.font.yatra_one_regular, FontWeight.SemiBold),
    Font(Res.font.yatra_one_regular, FontWeight.Bold),
)

/** Scores in Antonio, titles in Yatra One's sign-painter letters. */
@Composable
private fun gullyTypography(): DesiTypography {
    val base = desiTypography(antonioFamily(), figtreeFamily(), jetBrainsMonoFamily())
    val sign = yatraOneFamily()
    return base.copy(
        titleLg = base.titleLg.copy(fontFamily = sign),
        titleMd = base.titleMd.copy(fontFamily = sign),
    )
}

private val Brick = Color(0xFFA9452C)
private val Mortar = Color(0xFF7A2E1C)
private val Chalk = Color(0xFFF6F2E8)
private val TennisBall = Color(0xFFDDE64B)
private val Tape = Color(0xFFC9302C)
private val TapeEdge = Color(0xFF8E1F1C)

/** Asphalt grit everywhere, with the chalk crease and stumps the kids drew on the road behind the hero. */
private fun DrawScope.drawAsphalt(area: SurfaceArea, colors: DesiColors) {
    val random = Random(7)
    val count = (size.width * size.height / (9.dp.toPx() * 9.dp.toPx())).toInt().coerceAtMost(4000)
    val light = List(count / 2) { Offset(random.nextFloat() * size.width, random.nextFloat() * size.height) }
    val dark = List(count / 2) { Offset(random.nextFloat() * size.width, random.nextFloat() * size.height) }
    drawPoints(light, PointMode.Points, colors.onBrandStrong.copy(alpha = 0.10f), 1.6.dp.toPx(), StrokeCap.Round)
    drawPoints(dark, PointMode.Points, Color.Black.copy(alpha = 0.22f), 1.4.dp.toPx(), StrokeCap.Round)
    val chalk = colors.onBrandStrong.copy(alpha = 0.16f)
    val line = 3.dp.toPx()
    when (area) {
        SurfaceArea.Hero -> {
            // A popping crease near the bottom, a return crease, and three stumps by the right edge.
            val creaseY = size.height * 0.86f
            chalkLine(chalk, Offset(-line, creaseY), Offset(size.width + line, creaseY + 2.dp.toPx()), line)
            chalkLine(chalk, Offset(size.width * 0.78f, size.height * 0.52f), Offset(size.width * 0.785f, size.height + line), line)
            val top = size.height * 0.18f
            val bottom = size.height * 0.62f
            listOf(0.88f, 0.92f, 0.96f).forEach { x ->
                chalkLine(chalk, Offset(size.width * x, top), Offset(size.width * x + 1.dp.toPx(), bottom), line)
            }
        }
        SurfaceArea.Scoreboard -> {
            val creaseY = size.height - 6.dp.toPx()
            chalkLine(chalk, Offset(-line, creaseY), Offset(size.width + line, creaseY - 2.dp.toPx()), line)
        }
    }
}

/** A chalk stroke: a firm line with a fainter, slightly offset second pass, so the edge looks dusty. */
private fun DrawScope.chalkLine(color: Color, from: Offset, to: Offset, width: Float) {
    drawLine(color, from, to, width, StrokeCap.Round)
    val wobble = Offset(width * 0.35f, -width * 0.3f)
    drawLine(color.copy(alpha = color.alpha * 0.5f), from + wobble, to + wobble, width * 0.7f, StrokeCap.Round)
}

/** Three chalk stumps drawn on a brick wall. */
private fun DrawScope.drawWallStumps(colors: DesiColors) {
    val wallTop = size.height * 0.08f
    val wallSize = Size(size.width * 0.9f, size.height * 0.88f)
    val wallLeft = size.width * 0.05f
    val corner = CornerRadius(size.minDimension * 0.06f)
    drawRoundRect(Brick, Offset(wallLeft, wallTop), wallSize, corner)
    // Mortar courses, every other row staggered by half a brick.
    val rows = 5
    val rowH = wallSize.height / rows
    val mortar = size.minDimension * 0.025f
    for (row in 1 until rows) {
        val y = wallTop + row * rowH
        drawLine(Mortar, Offset(wallLeft, y), Offset(wallLeft + wallSize.width, y), mortar)
    }
    for (row in 0 until rows) {
        val y = wallTop + row * rowH
        val offset = if (row % 2 == 0) 0.25f else 0.5f
        var x = offset
        while (x < 1f) {
            val px = wallLeft + x * wallSize.width
            drawLine(Mortar, Offset(px, y), Offset(px, y + rowH), mortar)
            x += 0.5f
        }
    }
    val stroke = size.minDimension * 0.07f
    val top = size.height * 0.22f
    val bottom = size.height * 0.88f
    listOf(0.32f, 0.5f, 0.68f).forEach { x ->
        chalkLine(Chalk, Offset(size.width * x, top), Offset(size.width * x + stroke * 0.2f, bottom), stroke)
    }
    chalkLine(Chalk, Offset(size.width * 0.28f, size.height * 0.17f), Offset(size.width * 0.72f, size.height * 0.16f), stroke * 0.8f)
}

/** A tennis ball wrapped in red electrical tape, the way every gully team plays it. */
private fun DrawScope.drawTapedBall(colors: DesiColors) {
    val r = size.minDimension * 0.42f
    val c = center
    val ball = Path().apply { addOval(Rect(c, r)) }
    drawCircle(TennisBall, r, c)
    clipPath(ball) {
        // Three overlapping turns of tape across the middle.
        val band = r * 0.34f
        listOf(-0.36f, 0f, 0.36f).forEach { dy ->
            val y = c.y + dy * r
            drawLine(Tape, Offset(c.x - r, y - r * 0.18f), Offset(c.x + r, y + r * 0.18f), band)
            drawLine(TapeEdge, Offset(c.x - r, y - r * 0.18f + band / 2), Offset(c.x + r, y + r * 0.18f + band / 2), r * 0.05f)
        }
        // The felt's curved seam peeking out above and below the tape.
        val seam = Stroke(width = r * 0.07f, cap = StrokeCap.Round)
        drawArc(Chalk, 0f, 180f, false, Offset(c.x - r * 0.7f, c.y - r * 1.25f), Size(r * 1.4f, r * 0.8f), style = seam)
        drawArc(Chalk, 180f, 180f, false, Offset(c.x - r * 0.7f, c.y + r * 0.45f), Size(r * 1.4f, r * 0.8f), style = seam)
    }
    drawCircle(colors.ink.copy(alpha = 0.55f), r, c, style = Stroke(width = r * 0.06f))
}

/** Two bails drawn in chalk, one knocked askew. */
private fun DrawScope.drawChalkBails(colors: DesiColors) {
    val stroke = size.minDimension * 0.12f
    val y = center.y
    chalkLine(colors.ink, Offset(size.width * 0.14f, y + stroke * 0.3f), Offset(size.width * 0.42f, y), stroke)
    chalkLine(colors.ink, Offset(size.width * 0.58f, y - stroke * 0.9f), Offset(size.width * 0.84f, y - stroke * 0.1f), stroke)
}
