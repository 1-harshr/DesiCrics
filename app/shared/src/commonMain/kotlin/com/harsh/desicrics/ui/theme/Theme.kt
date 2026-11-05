package com.harsh.desicrics.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F4B6),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF9A4600),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBC8),
    onSecondaryContainer = Color(0xFF321300),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD89C),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228),
    onPrimaryContainer = Color(0xFFA6F4B6),
    secondary = Color(0xFFFFB68A),
    onSecondary = Color(0xFF532200),
    secondaryContainer = Color(0xFF763300),
    onSecondaryContainer = Color(0xFFFFDBC8),
    error = Color(0xFFFFB4AB),
)

/** Accent for boundaries in ball-by-ball chips. */
val BoundaryColor = Color(0xFF1E88E5)

@Composable
fun DesiCricsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
