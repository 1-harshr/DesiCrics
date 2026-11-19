package com.harsh.desicrics.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harsh.desicrics.designsystem.theme.DesiTheme

/**
 * Lays [content] out on the fixed design canvas of [format] (360 dp wide) and scales it to fill this
 * box's width, text included, ignoring the phone's font size. A template drawn here looks the same in a
 * 250 dp preview and in the 1080 px file.
 */
@Composable
fun ShareCanvas(format: ShareFormat, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.aspectRatio(format.aspectRatio)) {
        val scaled = Density(constraints.maxWidth / ShareFormat.DESIGN_WIDTH.value, fontScale = 1f)
        CompositionLocalProvider(LocalDensity provides scaled) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

/** Grey and white squares, so the transparent parts of an overlay show as transparent. */
@Composable
internal fun Checkerboard(modifier: Modifier = Modifier) {
    val share = DesiTheme.colors.share
    Canvas(modifier.fillMaxSize()) {
        drawRect(share.checkerLight)
        val cell = 12.dp.toPx()
        var row = 0
        var y = 0f
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(share.checkerDark, Offset(x, y), Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

/**
 * A stand-in for the user's own photo: a dusky sky over a dark ground, with a floodlight's glow. It shows
 * how an overlay reads on a real picture without shipping one.
 */
@Composable
internal fun SamplePhoto(modifier: Modifier = Modifier) {
    val share = DesiTheme.colors.share
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(share.photoTop, share.photoBottom)))
        drawCircle(
            Brush.radialGradient(
                listOf(share.overlayInk.copy(alpha = 0.22f), share.overlayInk.copy(alpha = 0f)),
                center = Offset(size.width * 0.8f, size.height * 0.12f),
                radius = size.width * 0.5f,
            ),
            radius = size.width * 0.5f,
            center = Offset(size.width * 0.8f, size.height * 0.12f),
        )
        // A low horizon: the ground is a little lighter than the sky's foot.
        drawRect(share.photoBottom.copy(alpha = 0.6f), Offset(0f, size.height * 0.72f), Size(size.width, size.height * 0.28f))
    }
}

/** Overlay text: white with a soft shadow so it still reads on a bright photo. */
@Composable
internal fun TextStyle.overlay(alpha: Float = 1f): TextStyle {
    val share = DesiTheme.colors.share
    val density = LocalDensity.current
    return copy(
        color = share.overlayInk.copy(alpha = alpha),
        shadow = with(density) { Shadow(share.overlayShadow, Offset(0f, 1.dp.toPx()), blurRadius = 6.dp.toPx()) },
    )
}

/** A design-canvas text size, for the large numerals templates use beyond the app's own type scale. */
internal fun TextStyle.sized(size: Int, lineHeight: Int = size) = copy(fontSize = size.sp, lineHeight = lineHeight.sp)

/**
 * Draws [path] as overlay line art: first a wider, translucent dark stroke a touch lower (the soft
 * shadow), then the white line.
 */
internal fun DrawScope.overlayStroke(path: Path, ink: Color, shadow: Color, width: Float) {
    translate(top = width * 0.6f) { drawPath(path, shadow, style = Stroke(width * 2.2f, cap = StrokeCap.Round)) }
    drawPath(path, ink, style = Stroke(width, cap = StrokeCap.Round))
}
