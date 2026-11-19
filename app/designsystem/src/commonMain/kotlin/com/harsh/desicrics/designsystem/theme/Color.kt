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
    /** Pictures made in the share studio; the same in light and dark so a shared card never changes. */
    val share: ShareColors = CreaseShareColors,
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

/**
 * Colours for share-studio pictures. Overlays are white line art on a transparent background, meant to
 * sit on the user's own photo, so they get their own ink and shadow rather than the screen's.
 */
@Immutable
data class ShareColors(
    /** Text and line art on transparent overlays. */
    val overlayInk: Color,
    /** The soft shadow under overlay text and lines, so they read on bright photos. */
    val overlayShadow: Color,
    /** The two squares of the checkerboard that shows where an overlay is transparent. */
    val checkerLight: Color,
    val checkerDark: Color,
    /** The sample dark photo an overlay can be previewed on: top and bottom of its gradient. */
    val photoTop: Color,
    val photoBottom: Color,
    /** The broadcast template's gold, used beside the brand green. */
    val gold: Color,
    /** Text and art on [gold]. */
    val onGold: Color,
    /** The minimal template's off-white paper and the ink printed on it. */
    val paper: Color,
    val paperInk: Color,
)

val CreaseShareColors = ShareColors(
    overlayInk = Color(0xFFFFFFFF),
    overlayShadow = Color(0x73000000),
    checkerLight = Color(0xFFF2F2EE),
    checkerDark = Color(0xFFDADBD4),
    photoTop = Color(0xFF3A4A5C),
    photoBottom = Color(0xFF1A1F17),
    gold = Color(0xFFF2B630),
    onGold = Color(0xFF231A02),
    paper = Color(0xFFF4EFE4),
    paperInk = Color(0xFF1B231D),
)
