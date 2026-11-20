package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.designsystem.theme.DesiThemeSpec
import com.harsh.desicrics.designsystem.theme.Motif
import com.harsh.desicrics.designsystem.theme.SurfaceArea
import org.jetbrains.compose.resources.stringResource

/**
 * Paints the theme's [DesiTheme.surface] texture for [area] over what's drawn so far and under the
 * content. Does nothing in a theme without one, so it's safe on any header.
 */
@Composable
fun Modifier.themeSurface(area: SurfaceArea): Modifier {
    val painter = DesiTheme.surface ?: return this
    val colors = DesiTheme.colors
    return drawBehind { with(painter) { paint(area, colors) } }
}

/**
 * One of the theme's [DesiTheme.motifs] (stumps, ball, bails) drawn at [modifier]'s size. Decorative by
 * default; pass [contentDescription] when the art says something on its own.
 */
@Composable
fun MotifArt(motif: Motif, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val colors = DesiTheme.colors
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics {}
    }
    Canvas(modifier.then(semantics)) { with(motif) { draw(colors) } }
}

/** A [Motif] on a soft square, sized and shaped like an [IconTile] so it can lead a row. */
@Composable
fun MotifTile(
    motif: Motif,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = DesiTheme.colors.brandSoft,
) {
    Box(modifier.size(size).clip(DesiTheme.shapes.tile).background(container), contentAlignment = Alignment.Center) {
        MotifArt(motif, Modifier.size(size * 0.6f))
    }
}

/**
 * A row of [themes] to pick from, each drawn as a small live preview in its own colours, shapes and art.
 * [darkTheme] is the mode the previews show, normally the one the app is in.
 */
@Composable
fun ThemePicker(
    themes: List<DesiThemeSpec>,
    selected: DesiThemeSpec,
    onSelect: (DesiThemeSpec) -> Unit,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = DesiTheme.colors.isDark,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
    ) {
        themes.forEach { spec ->
            ThemePreviewChip(spec, selected = spec == selected, onClick = { onSelect(spec) }, darkTheme = darkTheme)
        }
    }
}

/** One theme as a miniature screen: its hero and ball, the outcome colours and a brand button, over its name. */
@Composable
fun ThemePreviewChip(
    spec: DesiThemeSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = DesiTheme.colors.isDark,
) {
    val outer = DesiTheme.colors
    val outerShapes = DesiTheme.shapes
    Column(
        modifier
            .width(PreviewWidth)
            .clip(outerShapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(DesiTheme.space.s1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
    ) {
        DesiTheme(spec, darkTheme = darkTheme, reduceMotion = DesiTheme.reduceMotion) {
            ThemeMiniature(selected)
        }
        Text(
            stringResource(spec.name),
            style = DesiTheme.type.bodyStrong,
            color = if (selected) outer.ink else outer.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private val PreviewWidth = 112.dp

@Composable
private fun ThemeMiniature(selected: Boolean) {
    val colors = DesiTheme.colors
    val shapes = DesiTheme.shapes
    val space = DesiTheme.space
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shapes.medium)
            .background(colors.bg)
            .border(if (selected) shapes.borderFocus * 1.5f else shapes.border, if (selected) colors.brand else colors.line, shapes.medium),
        verticalArrangement = Arrangement.spacedBy(space.s2),
    ) {
        Box(Modifier.fillMaxWidth().height(44.dp).heroBackground(), contentAlignment = Alignment.Center) {
            MotifArt(DesiTheme.motifs.ball, Modifier.size(28.dp))
        }
        Row(
            Modifier.padding(horizontal = space.s2).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val event = colors.event
            listOf(event.four, event.six, event.wicket, event.extra).forEach {
                Box(Modifier.size(14.dp).clip(shapes.pill).background(it))
            }
        }
        Box(
            Modifier
                .padding(horizontal = space.s2)
                .padding(bottom = space.s2)
                .fillMaxWidth()
                .height(14.dp)
                .clip(shapes.small)
                .background(colors.brand),
        )
    }
}
