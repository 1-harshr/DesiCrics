package com.harsh.desicrics.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Checks every theme in [DesiThemes.all], light and dark, against the contrast rules every theme keeps
 * (docs/phase-8.md): 7:1 for scores and scoring controls, 4.5:1 for body text, 3:1 for the dot ball's
 * mark, and outcome colours that stay apart from each other and from the brand colour.
 */
class ThemeContrastTest {

    private enum class Need(val ratio: Double) { Score(7.0), Body(4.5), Graphic(3.0) }

    /** Text or a mark ([fg]) drawn on a fill ([bg]), named "fg on bg" in failures. */
    private class Pair(val fg: String, val bg: String, val need: Need, val pick: (DesiColors) -> kotlin.Pair<Color, Color>)

    private val pairs = listOf(
        // Scores and scoring controls.
        Pair("ink", "bg", Need.Score) { it.ink to it.bg },
        Pair("ink", "surface", Need.Score) { it.ink to it.surface },
        Pair("ink", "sunk", Need.Score) { it.ink to it.sunk },
        Pair("onBrand", "brand", Need.Score) { it.onBrand to it.brand },
        Pair("onBrandStrong", "brandStrong", Need.Score) { it.onBrandStrong to it.brandStrong },
        // Ball chips and the wicket key: text on an outcome fill.
        Pair("onEvent", "event.four", Need.Score) { it.event.onEvent to it.event.four },
        Pair("onEvent", "event.six", Need.Score) { it.event.onEvent to it.event.six },
        Pair("onEvent", "event.wicket", Need.Score) { it.event.onEvent to it.event.wicket },
        // Run pad keys and extra chips: outcome-coloured text on a card.
        Pair("event.four", "surface", Need.Score) { it.event.four to it.surface },
        Pair("event.six", "surface", Need.Score) { it.event.six to it.surface },
        Pair("event.wicket", "surface", Need.Score) { it.event.wicket to it.surface },
        Pair("event.extra", "surface", Need.Score) { it.event.extra to it.surface },
        // A dot ball is deliberately quiet, but its mark must still show.
        Pair("event.dot", "surface", Need.Graphic) { it.event.dot to it.surface },
        // Body text.
        Pair("muted", "bg", Need.Body) { it.muted to it.bg },
        Pair("muted", "surface", Need.Body) { it.muted to it.surface },
        Pair("muted", "sunk", Need.Body) { it.muted to it.sunk },
        Pair("brand", "bg", Need.Body) { it.brand to it.bg },
        Pair("brand", "surface", Need.Body) { it.brand to it.surface },
        Pair("ink", "brandSoft", Need.Body) { it.ink to it.brandSoft },
    )

    /**
     * Pairs Crease shipped below the rule, with the ratio each has today as a floor so they can't slip
     * further. Classic must look exactly as it did, so these wait for a palette pass of their own. New
     * themes get no entries here: they meet the rules outright.
     */
    private val classicExceptions: Map<String, Double> = mapOf(
        "light onBrand on brand" to 5.6,
        "light onEvent on event.four" to 6.0,
        "light onEvent on event.six" to 6.4,
        "light onEvent on event.wicket" to 4.9,
        "light event.four on surface" to 6.0,
        "light event.six on surface" to 6.4,
        "light event.wicket on surface" to 4.9,
        "light event.extra on surface" to 4.3,
        "dark onEvent on event.four" to 2.6,
        "dark onEvent on event.six" to 2.6,
        "dark onEvent on event.wicket" to 2.7,
        "dark event.four on surface" to 5.9,
        "dark event.six on surface" to 5.9,
        "dark event.wicket on surface" to 5.8,
    )

    private fun exceptionsFor(spec: DesiThemeSpec) = if (spec.id == ClassicTheme.id) classicExceptions else emptyMap()

    @Test
    fun everyThemeMeetsTheContrastRules() {
        val failures = buildList {
            DesiThemes.all.forEach { spec ->
                val exceptions = exceptionsFor(spec)
                listOf(false, true).forEach { dark ->
                    val colors = spec.colors(dark)
                    val mode = if (dark) "dark" else "light"
                    pairs.forEach { pair ->
                        val (fg, bg) = pair.pick(colors)
                        val ratio = contrast(fg, bg)
                        val key = "$mode ${pair.fg} on ${pair.bg}"
                        val floor = exceptions[key]
                        when {
                            floor == null && ratio < pair.need.ratio ->
                                add("${spec.id}: $key is ${ratio.fmt()}:1, needs ${pair.need.ratio}:1")
                            floor != null && ratio < floor ->
                                add("${spec.id}: $key is ${ratio.fmt()}:1, below its recorded ${floor}:1")
                            floor != null && ratio >= pair.need.ratio ->
                                add("${spec.id}: $key now meets ${pair.need.ratio}:1, so drop its exception")
                        }
                    }
                }
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test
    fun outcomeColoursStayApartFromEachOtherAndTheBrand() {
        DesiThemes.all.forEach { spec ->
            listOf(false, true).forEach { dark ->
                val c = spec.colors(dark)
                val named = listOf(
                    "four" to c.event.four, "six" to c.event.six, "wicket" to c.event.wicket,
                    "extra" to c.event.extra, "dot" to c.event.dot, "brand" to c.brand,
                )
                named.forEachIndexed { i, (a, colorA) ->
                    named.drop(i + 1).forEach { (b, colorB) ->
                        assertTrue(distance(colorA, colorB) >= MIN_DISTANCE, "${spec.id} ${if (dark) "dark" else "light"}: $a and $b are too alike")
                    }
                }
            }
        }
    }

    @Test
    fun themesAreWellFormed() {
        val ids = DesiThemes.all.map { it.id }
        assertEquals(ids.distinct(), ids, "theme ids must be unique")
        assertTrue(DesiThemes.default in DesiThemes.all)
        DesiThemes.all.forEach {
            assertTrue(it.id.matches(Regex("[a-z][a-z0-9_]*")), "${it.id}: ids are saved, so keep them lower_snake_case")
            assertFalse(it.light.isDark, "${it.id}: light colours say they're dark")
            assertTrue(it.dark.isDark, "${it.id}: dark colours say they're light")
        }
    }

    @Test
    fun unknownOrMissingIdsFallBackToTheDefault() {
        assertEquals(DesiThemes.default, DesiThemes.byId(null))
        assertEquals(DesiThemes.default, DesiThemes.byId("retired_theme"))
        DesiThemes.all.forEach { assertEquals(it, DesiThemes.byId(it.id)) }
    }

    /** WCAG 2 contrast ratio. */
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun distance(a: Color, b: Color): Float {
        val dr = a.red - b.red
        val dg = a.green - b.green
        val db = a.blue - b.blue
        return sqrt(dr * dr + dg * dg + db * db)
    }

    private fun Double.fmt() = ((this * 100).toInt() / 100.0).toString()

    private companion object {
        /** In sRGB units (0–1 per channel); Classic's closest outcome pair, four and six in dark, is 0.23. */
        const val MIN_DISTANCE = 0.15f
    }
}
