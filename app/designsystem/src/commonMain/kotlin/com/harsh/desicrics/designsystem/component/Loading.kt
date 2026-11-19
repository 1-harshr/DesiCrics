package com.harsh.desicrics.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.theme.DesiTheme

/**
 * The app's loading indicator: the app icon's ball spinning on its seam. [color] fills the ball and
 * [seamColor] draws the stitching, so on a brand surface (a primary button) pass them the other way round.
 */
@Composable
fun BallSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = DesiTheme.colors.brand,
    seamColor: Color = DesiTheme.colors.onBrand,
) {
    val angle by rememberInfiniteTransition(label = "ball").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = center
        rotate(angle) {
            drawCircle(color, radius = r, center = c)
            // Two seams bowing the same way, as on the app icon (in units of the radius).
            val seam = Stroke(width = r * 0.11f, cap = StrokeCap.Round)
            for (x in floatArrayOf(-0.17f, 0.17f)) {
                val path = Path().apply {
                    moveTo(c.x + x * r, c.y - 0.92f * r)
                    quadraticTo(c.x + (x - 0.3f) * r, c.y, c.x + x * r, c.y + 0.92f * r)
                }
                drawPath(path, seamColor, style = seam)
            }
            // A few stitches either side of the seams, once the ball is big enough to show them.
            if (r >= 14.dp.toPx()) {
                val stitch = r * 0.07f
                for (y in floatArrayOf(-0.6f, -0.3f, 0f, 0.3f, 0.6f)) {
                    // Where the seam curve sits at this height: y is linear in t, x bows by 0.6·t(1−t).
                    val t = (y + 0.92f) / 1.84f
                    val bow = 0.6f * t * (1 - t)
                    val left = c.x + (-0.17f - bow) * r
                    val right = c.x + (0.17f - bow) * r
                    val yy = c.y + y * r
                    drawLine(seamColor, Offset(left - 0.18f * r, yy), Offset(left - 0.04f * r, yy), stitch, StrokeCap.Round)
                    drawLine(seamColor, Offset(right + 0.04f * r, yy), Offset(right + 0.18f * r, yy), stitch, StrokeCap.Round)
                }
            }
        }
    }
}
