package com.harsh.desicrics.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.resources.Res
import com.harsh.desicrics.designsystem.resources.bitter_variable
import com.harsh.desicrics.designsystem.resources.ds_theme_red_ball
import org.jetbrains.compose.resources.Font

/**
 * Red Ball, for Test cricket: cream flannels, a cherry-leather brand, willow and pavilion green, and slab
 * numerals like an old honours board. Dark mode is the pavilion at night, in walnut.
 */
val RedBallTheme: DesiThemeSpec = DesiThemeSpec(
    id = "red_ball",
    name = Res.string.ds_theme_red_ball,
    light = DesiColors(
        bg = Color(0xFFF5EFE1),
        surface = Color(0xFFFFFCF3),
        sunk = Color(0xFFEDE4CF),
        ink = Color(0xFF2A1B14),
        muted = Color(0xFF68584A),
        line = Color(0xFFE2D6BE),
        brand = Color(0xFF7A1A2A),
        // Pavilion green behind icon tiles and the field.
        brandSoft = Color(0xFFE1E9D6),
        onBrand = Color(0xFFFFFCF3),
        brandStrong = Color(0xFF5A1219),
        onBrandStrong = Color(0xFFFBF3E2),
        event = EventColors(
            four = Color(0xFF1D3C84),
            six = Color(0xFF1E5A3A),
            wicket = Color(0xFFA61E06),
            extra = Color(0xFF704A10),
            dot = Color(0xFF9A8B78),
            onEvent = Color(0xFFFFFCF3),
        ),
        isDark = false,
    ),
    dark = DesiColors(
        bg = Color(0xFF16110D),
        surface = Color(0xFF211913),
        sunk = Color(0xFF2B211A),
        ink = Color(0xFFF4ECDD),
        muted = Color(0xFFBCAC97),
        line = Color(0xFF3B2F26),
        // Willow and gilt by night; the cherry stays in the hero.
        brand = Color(0xFFE6BE7E),
        brandSoft = Color(0xFF26301F),
        onBrand = Color(0xFF1E140A),
        brandStrong = Color(0xFF4C1418),
        onBrandStrong = Color(0xFFF8EEDC),
        // Light fills with dark text, so outcome chips and outcome-coloured keys both read on walnut.
        event = EventColors(
            four = Color(0xFF8EC3F2),
            six = Color(0xFF8FD3A8),
            wicket = Color(0xFFFF8F86),
            extra = Color(0xFFDDB0EC),
            dot = Color(0xFF7C6E60),
            onEvent = Color(0xFF16110D),
        ),
        isDark = true,
    ),
    typography = { desiTypography(bitterFamily(), figtreeFamily(), jetBrainsMonoFamily()) },
    shapes = DesiShapes(
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(18.dp),
        pill = CircleShape,
        tile = RoundedCornerShape(percent = 22),
        border = 1.dp,
        borderControl = 1.5.dp,
        borderFocus = 2.dp,
    ),
    motifs = DesiMotifs(
        stumps = { drawRedBallStumps(it) },
        ball = { drawRedBall(it) },
        bails = { drawRedBallBails(it) },
    ),
    surface = { _, colors -> drawFlannelTwill(colors) },
)

/**
 * Bitter: Red Ball's display face, a slab serif like an honours board, with lining figures whose
 * tabular widths switch on even for a score on its own.
 */
@Composable
fun bitterFamily(): FontFamily = FontFamily(
    Font(Res.font.bitter_variable, FontWeight.SemiBold),
    Font(Res.font.bitter_variable, FontWeight.Bold),
)

/** A faint diagonal twill, like the weave of cricket flannels, with a gilt rule along the bottom edge. */
private fun DrawScope.drawFlannelTwill(colors: DesiColors) {
    val thread = colors.onBrandStrong.copy(alpha = 0.035f)
    val step = 5.dp.toPx()
    val stroke = 1.dp.toPx()
    clipRect {
        var x = -size.height
        while (x < size.width) {
            drawLine(thread, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
            x += step
        }
    }
    val gilt = colors.onBrandStrong.copy(alpha = 0.16f)
    val inset = 3.dp.toPx()
    drawLine(gilt, Offset(0f, size.height - inset), Offset(size.width, size.height - inset), stroke)
    drawLine(gilt, Offset(0f, size.height - inset * 2), Offset(size.width, size.height - inset * 2), stroke * 0.6f)
}

/** Three turned stumps in [DesiColors.muted], pointed at the foot, under two cherry bails. */
private fun DrawScope.drawRedBallStumps(colors: DesiColors) {
    val w = size.minDimension * 0.085f
    val top = size.height * 0.2f
    val foot = size.height * 0.9f
    val xs = listOf(0.3f, 0.5f, 0.7f).map { it * size.width }
    xs.forEach { x ->
        val stump = Path().apply {
            moveTo(x - w / 2, top)
            lineTo(x + w / 2, top)
            lineTo(x + w / 2, foot - w)
            lineTo(x, foot)
            lineTo(x - w / 2, foot - w)
            close()
        }
        drawPath(stump, colors.muted)
    }
    val bailY = size.height * 0.13f
    val bail = w * 0.75f
    listOf(xs[0] to xs[1], xs[1] to xs[2]).forEach { (a, b) ->
        drawLine(colors.brand, Offset(a + w * 0.1f, bailY), Offset(b - w * 0.1f, bailY), bail, StrokeCap.Round)
    }
}

/** A cherry leather ball side-on: a raised straight seam with two rows of stitching and a polished shine. */
private fun DrawScope.drawRedBall(colors: DesiColors) {
    val r = size.minDimension * 0.42f
    val c = center
    drawCircle(colors.brand, r, c)
    // A pale rim, so the ball still shows against the cherry hero.
    drawCircle(colors.onBrandStrong.copy(alpha = 0.3f), r, c, style = Stroke(width = r * 0.06f))
    val stitch = colors.onBrand
    // The seam: a solid band with stitches either side, running straight over the ball.
    drawLine(stitch.copy(alpha = 0.35f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), r * 0.08f)
    val rows = 7
    for (i in 0 until rows) {
        val t = (i + 0.5f) / rows
        val y = c.y - r * 0.86f + t * r * 1.72f
        for (side in floatArrayOf(-1f, 1f)) {
            val x = c.x + side * r * 0.16f
            drawLine(stitch, Offset(x - side * r * 0.03f, y - r * 0.05f), Offset(x + side * r * 0.03f, y + r * 0.05f), r * 0.055f, StrokeCap.Round)
        }
    }
    // Polish on the leather.
    drawArc(
        stitch.copy(alpha = 0.3f),
        startAngle = 200f,
        sweepAngle = 50f,
        useCenter = false,
        topLeft = Offset(c.x - r * 0.72f, c.y - r * 0.72f),
        size = Size(r * 1.44f, r * 1.44f),
        style = Stroke(width = r * 0.1f, cap = StrokeCap.Round),
    )
}

/** Two turned bails: a fat barrel with a short spigot at each end, in cherry. */
private fun DrawScope.drawRedBallBails(colors: DesiColors) {
    val h = size.minDimension * 0.14f
    val y = center.y
    listOf(0.12f to 0.46f, 0.54f to 0.88f).forEach { (a, b) ->
        val start = size.width * a
        val end = size.width * b
        val spigot = (end - start) * 0.16f
        drawLine(colors.brand, Offset(start, y), Offset(end, y), h * 0.45f, StrokeCap.Round)
        drawRoundRect(
            colors.brand,
            topLeft = Offset(start + spigot, y - h / 2),
            size = Size(end - start - spigot * 2, h),
            cornerRadius = CornerRadius(h / 2),
        )
    }
}
