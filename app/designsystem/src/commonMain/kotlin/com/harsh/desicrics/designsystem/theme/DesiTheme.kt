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

/**
 * Wraps [MaterialTheme] so stock Material components pick up Crease colours and type, and exposes the
 * extra tokens through [DesiTheme.colors], [DesiTheme.type], [DesiTheme.space], [DesiTheme.radius] and
 * [DesiTheme.size]. [reduceMotion] is the system's "remove animations" setting: looping animations
 * (the live dot's pulse) stand still when it's on.
 */
@Composable
fun DesiTheme(darkTheme: Boolean = isSystemInDarkTheme(), reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkDesiColors else LightDesiColors
    val type = desiTypography()
    CompositionLocalProvider(LocalDesiColors provides colors, LocalDesiTypography provides type, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = type.toMaterial(),
            shapes = Shapes(
                extraSmall = DesiRadius.sm,
                small = DesiRadius.sm,
                medium = DesiRadius.md,
                large = DesiRadius.lg,
                extraLarge = DesiRadius.lg,
            ),
            content = content,
        )
    }
}

object DesiTheme {
    val colors: DesiColors
        @Composable @ReadOnlyComposable get() = LocalDesiColors.current
    val type: DesiTypography
        @Composable @ReadOnlyComposable get() = LocalDesiTypography.current
    val space: DesiSpacing get() = DesiSpacing
    val radius: DesiRadius get() = DesiRadius
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
