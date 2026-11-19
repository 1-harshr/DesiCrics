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
    bg = Color(0xFFF7F7F1),
    surface = Color(0xFFFFFFFF),
    sunk = Color(0xFFF0F2E9),
    ink = Color(0xFF14241B),
    muted = Color(0xFF596A5E),
    line = Color(0xFFE1E7DC),
    brand = Color(0xFF087647),
    brandSoft = Color(0xFFE2F2E7),
    onBrand = Color(0xFFFFFFFF),
    brandStrong = Color(0xFF064B35),
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
    bg = Color(0xFF0D1712),
    surface = Color(0xFF17241C),
    sunk = Color(0xFF203027),
    ink = Color(0xFFF1F5ED),
    muted = Color(0xFFAABCAE),
    line = Color(0xFF304337),
    brand = Color(0xFF68D49A),
    brandSoft = Color(0xFF1D4932),
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
