package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 *
 * It's a circle by default. With [aspectRatio] null the caller sizes it, and a field wider than it is tall
 * is drawn as an oval, like a real ground, so it fits a short strip and its labels still have room.
 */
@Composable
fun WagonWheel(
    labels: List<String>,
    modifier: Modifier = Modifier,
    values: List<Int>? = null,
    selected: Int? = null,
    onSector: ((Int) -> Unit)? = null,
    description: String? = null,
    aspectRatio: Float? = 1f,
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
            .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
            .semantics {
                if (description != null) contentDescription = description
                if (onSector != null) {
                    role = Role.Button
                    // A screen reader can't aim at a sector, so each one is also an action by name.
                    customActions = labels.mapIndexed { i, label -> CustomAccessibilityAction(label) { onSector(i); true } }
                }
                if (selected != null) stateDescription = labels[selected]
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .then(
                    if (onSector == null) Modifier
                    else Modifier.pointerInput(sectors) {
                        detectTapGestures { tap ->
                            // Scaled to a unit circle, so an oval field splits the same way it's drawn.
                            val dx = (tap.x - size.width / 2f) / (size.width / 2f)
                            val dy = (tap.y - size.height / 2f) / (size.height / 2f)
                            if (hypot(dx, dy) > 1f) return@detectTapGestures
                            // Degrees clockwise from straight up.
                            val degrees = ((atan2(dy, dx) * 180 / PI).toFloat() + 90f + 360f) % 360f
                            onSector((degrees / sweep).toInt().coerceIn(0, sectors - 1))
                        }
                    },
                ),
        ) {
            val radiusX = size.width / 2f
            val radiusY = size.height / 2f
            val center = Offset(radiusX, radiusY)
            val topLeft = Offset(center.x - radiusX, center.y - radiusX)
            val arcSize = Size(radiusX * 2, radiusX * 2)
            for (i in 0 until sectors) {
                val start = -90f + i * sweep
                val shade = when {
                    i == selected -> colors.brand
                    busiest != null -> colors.brand.copy(alpha = 0.08f + 0.6f * (values[i].toFloat() / busiest))
                    else -> colors.brandSoft
                }
                // A circle squashed to the field's height; for a round wheel the scale is 1.
                scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = center) {
                    drawArc(shade, start, sweep, useCenter = true, topLeft = topLeft, size = arcSize)
                    drawArc(colors.surface, start, sweep, useCenter = true, topLeft = topLeft, size = arcSize, style = Stroke(2.dp.toPx()))
                }

                val mid = (start + sweep / 2) * PI.toFloat() / 180f
                val at = Offset(center.x + cos(mid) * radiusX * 0.66f, center.y + sin(mid) * radiusY * 0.66f)
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
            val radius = minOf(radiusX, radiusY)
            val pitch = Size(radius * 0.08f, radius * 0.3f)
            drawRect(colors.surface, Offset(center.x - pitch.width / 2, center.y - pitch.height / 2), pitch)
        }
    }
}
