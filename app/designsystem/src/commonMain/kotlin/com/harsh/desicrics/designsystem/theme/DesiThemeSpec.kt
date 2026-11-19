package com.harsh.desicrics.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.StringResource

/**
 * One complete look for the app: colours for light and dark, type, shapes, an optional texture behind
 * headers, and small cricket art. Themes change how things look, never where they are, so spacing, sizes
 * and motion stay in [DesiSpacing], [DesiSize] and [DesiMotion] for every theme.
 *
 * To add a theme, build one of these next to [ClassicTheme] and list it in [DesiThemes.all].
 */
@Immutable
class DesiThemeSpec(
    /** Saved per device to remember the choice, so never change it once a theme ships. */
    val id: String,
    /** What the theme is called in the picker. */
    val name: StringResource,
    val light: DesiColors,
    val dark: DesiColors,
    /** Builds the type scale. Composable because fonts load from resources; see [desiTypography]. */
    val typography: @Composable () -> DesiTypography,
    val shapes: DesiShapes,
    val motifs: DesiMotifs,
    /** Texture painted behind the hero and the scoreboard; null for plain fills. */
    val surface: SurfacePainter? = null,
) {
    fun colors(darkTheme: Boolean): DesiColors = if (darkTheme) dark else light

    override fun equals(other: Any?): Boolean = other is DesiThemeSpec && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "DesiThemeSpec($id)"
}

/**
 * Corner radii and border weights. [small], [medium], [large] and [pill] are the `radius.*` tokens of
 * docs/design-system.md; a square-cornered theme sets them to `RoundedCornerShape(0.dp)`.
 */
@Immutable
data class DesiShapes(
    /** Chips in tables, small badges. */
    val small: CornerBasedShape,
    /** Cards, inputs, buttons. */
    val medium: CornerBasedShape,
    /** Bottom sheets, hero cards, run pad keys. */
    val large: CornerBasedShape,
    /** Ball chips, avatars, pills. */
    val pill: CornerBasedShape,
    /** The soft square behind an [com.harsh.desicrics.designsystem.component.IconTile]; scales with the tile. */
    val tile: CornerBasedShape,
    /** Card and divider outlines. */
    val border: Dp,
    /** Outlined controls: ghost buttons, ball chips. */
    val borderControl: Dp,
    /** A focused input or a selected option. */
    val borderFocus: Dp,
) {
    // The names screens used before themes; they follow the theme too.
    val sm: CornerBasedShape get() = small
    val md: CornerBasedShape get() = medium
    val lg: CornerBasedShape get() = large
    val full: CornerBasedShape get() = pill
}

/** Where a [SurfacePainter] is drawing, so a theme can paint the hero and the scoreboard differently. */
enum class SurfaceArea {
    /** The brand-coloured header of an inside page and the brand cards that share its background. */
    Hero,

    /** Behind the live score at the top of a match. */
    Scoreboard,
}

/**
 * A texture drawn behind content (turf stripes, paper grain, a painted board). It draws over the area's
 * fill and under its content, so keep it faint enough for text on top to keep its contrast.
 */
fun interface SurfacePainter {
    fun DrawScope.paint(area: SurfaceArea, colors: DesiColors)
}

/** A small piece of cricket art drawn to fill the canvas it's given, using the theme's colours. */
fun interface Motif {
    fun DrawScope.draw(colors: DesiColors)
}

/** Art slots for empty states, loaders and share cards. */
@Immutable
data class DesiMotifs(
    val stumps: Motif,
    val ball: Motif,
    val bails: Motif,
)
