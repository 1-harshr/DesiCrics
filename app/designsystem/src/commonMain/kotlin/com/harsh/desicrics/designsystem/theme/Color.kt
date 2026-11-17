package com.harsh.desicrics.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Ball-outcome colours. Only used for deliveries (and [wicket] for LIVE and destructive actions). */
@Immutable
data class EventColors(
    val four: Color,
    val six: Color,
    val wicket: Color,
    val extra: Color,
    val dot: Color,
    /** Text and icons on [four], [six] and [wicket] fills; white in both themes. */
    val onEvent: Color,
)

@Immutable
data class DesiColors(
    val bg: Color,
    val surface: Color,
    val sunk: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val brand: Color,
    val brandSoft: Color,
    val onBrand: Color,
    /** Deep green for full-bleed brand areas (sign-in header); stays dark in both themes so white text works. */
    val brandStrong: Color,
    val onBrandStrong: Color,
    val event: EventColors,
    val isDark: Boolean,
)

val LightDesiColors = DesiColors(
    bg = Color(0xFFF5F7F4),
    surface = Color(0xFFFFFFFF),
    sunk = Color(0xFFECF0EC),
    ink = Color(0xFF111A15),
    muted = Color(0xFF56635B),
    line = Color(0xFFD8DFD9),
    brand = Color(0xFF0B7A4B),
    brandSoft = Color(0xFFDDF1E6),
    onBrand = Color(0xFFFFFFFF),
    brandStrong = Color(0xFF0B7A4B),
    onBrandStrong = Color(0xFFFFFFFF),
    event = EventColors(
        four = Color(0xFF2459D6),
        six = Color(0xFF6D3BD8),
        wicket = Color(0xFFD22F3B),
        extra = Color(0xFFA86B12),
        dot = Color(0xFF8A958E),
        onEvent = Color(0xFFFFFFFF),
    ),
    isDark = false,
)

val DarkDesiColors = DesiColors(
    bg = Color(0xFF0C110E),
    surface = Color(0xFF141B17),
    sunk = Color(0xFF1A231E),
    ink = Color(0xFFE6EDE8),
    muted = Color(0xFF95A39A),
    line = Color(0xFF27322C),
    brand = Color(0xFF3CC788),
    brandSoft = Color(0xFF143325),
    onBrand = Color(0xFF06140D),
    brandStrong = Color(0xFF0E5A3A),
    onBrandStrong = Color(0xFFF2F7F4),
    event = EventColors(
        four = Color(0xFF6D9BFF),
        six = Color(0xFFA68BFF),
        wicket = Color(0xFFFF6B74),
        extra = Color(0xFFE3AE4C),
        dot = Color(0xFF6E7A73),
        onEvent = Color(0xFFFFFFFF),
    ),
    isDark = true,
)
