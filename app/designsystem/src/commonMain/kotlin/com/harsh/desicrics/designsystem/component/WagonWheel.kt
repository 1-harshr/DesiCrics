package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.theme.DesiTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A cricket field cut into equal sectors, clockwise from straight down the ground (top). Each sector shows
 * its [labels] entry and, if given, its [values] entry; the busiest sectors are shaded darkest. With
 * [onSector], tapping a sector picks it (by index), so the same wheel works for scoring and for reading back.
 */
@Composable
fun WagonWheel(
    labels: List<String>,
    modifier: Modifier = Modifier,
    values: List<Int>? = null,
    selected: Int? = null,
    onSector: ((Int) -> Unit)? = null,
    description: String? = null,
) {
    val colors = DesiTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = DesiTheme.type.caption.copy(color = colors.ink)
    val valueStyle = DesiTheme.type.scoreMd.copy(color = colors.ink)
    val sectors = labels.size
    val sweep = 360f / sectors
    val busiest = values?.maxOrNull()?.takeIf { it > 0 }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics {
                if (description != null) contentDescription = description
                if (onSector != null) role = Role.Button
            },
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (onSector == null) Modifier
                    else Modifier.pointerInput(sectors) {
                        detectTapGestures { tap ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = tap.x - center.x
                            val dy = tap.y - center.y
                            if (hypot(dx, dy) > size.width / 2f) return@detectTapGestures
                            // Degrees clockwise from straight up.
                            val degrees = ((atan2(dy, dx) * 180 / PI).toFloat() + 90f + 360f) % 360f
                            onSector((degrees / sweep).toInt().coerceIn(0, sectors - 1))
                        }
                    },
                ),
        ) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            for (i in 0 until sectors) {
                val start = -90f + i * sweep
                val shade = when {
                    i == selected -> colors.brand
                    busiest != null -> colors.brand.copy(alpha = 0.08f + 0.6f * (values[i].toFloat() / busiest))
                    else -> colors.brandSoft
                }
                drawArc(shade, start, sweep, useCenter = true, topLeft = topLeft, size = arcSize)
                drawArc(colors.surface, start, sweep, useCenter = true, topLeft = topLeft, size = arcSize, style = Stroke(2.dp.toPx()))

                val mid = (start + sweep / 2) * PI.toFloat() / 180f
                val at = Offset(center.x + cos(mid) * radius * 0.66f, center.y + sin(mid) * radius * 0.66f)
                val onShade = if (i == selected) labelStyle.copy(color = colors.onBrand) else labelStyle
                val label = measurer.measure(labels[i], onShade)
                val value = values?.let { measurer.measure("${it[i]}", if (i == selected) valueStyle.copy(color = colors.onBrand) else valueStyle) }
                val height = label.size.height + (value?.size?.height ?: 0)
                var y = at.y - height / 2f
                value?.let {
                    drawText(it, topLeft = Offset(at.x - it.size.width / 2f, y))
                    y += it.size.height
                }
                drawText(label, topLeft = Offset(at.x - label.size.width / 2f, y))
            }
            // The pitch, so the wheel reads as a field.
            val pitch = Size(radius * 0.08f, radius * 0.3f)
            drawRect(colors.surface, Offset(center.x - pitch.width / 2, center.y - pitch.height / 2), pitch)
        }
    }
}
