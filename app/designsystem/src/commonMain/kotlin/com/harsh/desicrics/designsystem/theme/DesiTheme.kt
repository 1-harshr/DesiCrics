package com.harsh.desicrics.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalDesiColors = staticCompositionLocalOf { LightDesiColors }
private val LocalReduceMotion = staticCompositionLocalOf { false }
private val LocalDesiTypography = staticCompositionLocalOf<DesiTypography> {
    error("DesiTypography is only available inside DesiTheme { }")
}
private val LocalDesiThemeSpec = staticCompositionLocalOf { DesiThemes.default }

/**
 * The app's theme: [DesiThemes.default] (Classic). Kept so screens and previews that don't care which
 * theme is picked stay as they are; the app root passes the player's pick to the overload with a spec.
 */
@Composable
fun DesiTheme(darkTheme: Boolean = isSystemInDarkTheme(), reduceMotion: Boolean = false, content: @Composable () -> Unit) =
    DesiTheme(DesiThemes.default, darkTheme, reduceMotion, content)

/**
 * Wraps [MaterialTheme] so stock Material components pick up the theme's colours, type and shapes, and
 * exposes the extra tokens through [DesiTheme.colors], [DesiTheme.type], [DesiTheme.shapes],
 * [DesiTheme.surface], [DesiTheme.motifs], [DesiTheme.space] and [DesiTheme.size]. [reduceMotion] is the
 * system's "remove animations" setting: looping animations (the live dot's pulse) stand still when it's on.
 */
@Composable
fun DesiTheme(
    spec: DesiThemeSpec,
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = spec.colors(darkTheme)
    val type = spec.typography()
    val shapes = spec.shapes
    CompositionLocalProvider(
        LocalDesiThemeSpec provides spec,
        LocalDesiColors provides colors,
        LocalDesiTypography provides type,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = type.toMaterial(),
            shapes = Shapes(
                extraSmall = shapes.small,
                small = shapes.small,
                medium = shapes.medium,
                large = shapes.large,
                extraLarge = shapes.large,
            ),
            content = content,
        )
    }
}

object DesiTheme {
    /** The theme in use, for pickers and anything that needs its [DesiThemeSpec.id]. */
    val spec: DesiThemeSpec
        @Composable @ReadOnlyComposable get() = LocalDesiThemeSpec.current
    val colors: DesiColors
        @Composable @ReadOnlyComposable get() = LocalDesiColors.current
    val type: DesiTypography
        @Composable @ReadOnlyComposable get() = LocalDesiTypography.current
    val shapes: DesiShapes
        @Composable @ReadOnlyComposable get() = LocalDesiThemeSpec.current.shapes

    /** The same tokens as [shapes] under their older names (`radius.md`), so they follow the theme too. */
    val radius: DesiShapes
        @Composable @ReadOnlyComposable get() = LocalDesiThemeSpec.current.shapes

    /** The theme's texture for the hero and scoreboard, or null for plain fills. */
    val surface: SurfacePainter?
        @Composable @ReadOnlyComposable get() = LocalDesiThemeSpec.current.surface
    val motifs: DesiMotifs
        @Composable @ReadOnlyComposable get() = LocalDesiThemeSpec.current.motifs
    val space: DesiSpacing get() = DesiSpacing
    val size: DesiSize get() = DesiSize
    val motion: DesiMotion get() = DesiMotion
    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}

private fun DesiColors.toMaterial() = if (isDark) {
    darkColorScheme(
        primary = brand, onPrimary = onBrand, primaryContainer = brandSoft, onPrimaryContainer = ink,
        secondary = brand, onSecondary = onBrand, secondaryContainer = brandSoft, onSecondaryContainer = ink,
        background = bg, onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = sunk, onSurfaceVariant = muted, surfaceContainer = surface,
        surfaceContainerLow = surface, surfaceContainerHigh = sunk, surfaceContainerHighest = sunk,
        outline = line, outlineVariant = line, error = event.wicket, onError = event.onEvent,
    )
} else {
    lightColorScheme(
        primary = brand, onPrimary = onBrand, primaryContainer = brandSoft, onPrimaryContainer = ink,
        secondary = brand, onSecondary = onBrand, secondaryContainer = brandSoft, onSecondaryContainer = ink,
        background = bg, onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = sunk, onSurfaceVariant = muted, surfaceContainer = surface,
        surfaceContainerLow = surface, surfaceContainerHigh = sunk, surfaceContainerHighest = sunk,
        outline = line, outlineVariant = line, error = event.wicket, onError = event.onEvent,
    )
}

/** Maps Crease styles onto Material's slots so stock components (text fields, dialogs, chips) match. */
private fun DesiTypography.toMaterial() = Typography(
    displayLarge = scoreXl, displayMedium = scoreLg, displaySmall = scoreMd,
    headlineLarge = titleLg, headlineMedium = titleLg, headlineSmall = titleMd,
    titleLarge = titleMd, titleMedium = bodyStrong, titleSmall = bodyStrong,
    bodyLarge = bodyLg, bodyMedium = body, bodySmall = caption,
    labelLarge = button, labelMedium = label, labelSmall = label,
)
