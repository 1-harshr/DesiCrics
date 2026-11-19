package com.harsh.desicrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.scoring.withScore
import com.harsh.desicrics.ui.match.MatchActions
import com.harsh.desicrics.ui.match.MatchContent
import com.harsh.desicrics.ui.match.MatchUiState
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The scoring screen with the wagon wheel's field above the run pad, saved to app/shared/screenshots/
 * like [ScreenshotTest]: on a roomy phone, a budget one and a small old one, with and without a zone armed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class WagonWheelScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        System.setProperty("roborazzi.test.record", "true")
        // Avatars look up the server address to load photos; none of these have one.
        startKoin { modules(module { single { ApiConfig("http://localhost") } }) }
    }

    @After
    fun tearDown() = stopKoin()

    /** Draws [content], taps [zone] on the field if given, and saves the screen as [name]. */
    private fun shoot(name: String, dark: Boolean = false, zone: String? = null, content: @Composable () -> Unit) {
        compose.setContent { DesiTheme(darkTheme = dark, reduceMotion = true) { content() } }
        if (zone != null) {
            val field = compose.onNodeWithContentDescription("Where the next shot went", substring = true).fetchSemanticsNode()
            val action = field.config[SemanticsActions.CustomActions].single { it.label == zone }
            compose.runOnIdle { action.action() }
        }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private val strikers = Team("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir", "Ishaan", "Dev").mapIndexed { i, n -> Player("a$i", n, "pa$i") }, teamId = "t1")
    private val royals = Team("Powai XI", listOf("Esha", "Farhan", "Gaurav", "Hari").mapIndexed { i, n -> Player("b$i", n, "pb$i") }, teamId = "t2")

    private fun match(deliveries: List<Delivery>, rules: MatchRules = MatchRules.Club): MatchUiState {
        val base = Match("m", "me", 0, strikers, royals, 5, TeamSide.A, TossDecision.BAT, rules = rules, scorerUid = "me")
        val state = ScoringEngine.compute(base, deliveries)
        val scored = base.withScore(state)
        return MatchUiState(loading = false, match = scored, deliveries = deliveries, state = ScoringEngine.compute(scored, deliveries), isScorer = true)
    }

    private val balls: List<Delivery> = run {
        var seq = 0
        fun ball(s: String, n: String, b: String, runs: Int = 0, extra: ExtraType? = null, extraRuns: Int = 0) =
            Delivery(seq++, 1, s, n, b, runs, extra, extraRuns)
        listOf(
            ball("a0", "a1", "b0", runs = 4), ball("a0", "a1", "b0", runs = 1), ball("a1", "a0", "b0"),
            ball("a1", "a0", "b0", extra = ExtraType.WIDE), ball("a1", "a0", "b0", runs = 6),
        )
    }

    private val actions = MatchActions({}, {}, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _, _, _ -> }, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {})

    @Test
    fun wagonWheelField() = shoot("wagon_wheel_field") { MatchContent(match(balls), actions) }

    @Test
    fun wagonWheelArmed() = shoot("wagon_wheel_armed", zone = "Cover") { MatchContent(match(balls), actions) }

    @Test
    fun wagonWheelArmedDark() = shoot("wagon_wheel_armed_dark", dark = true, zone = "Mid-wicket") { MatchContent(match(balls), actions) }

    // A common budget phone, and a small old one: the crease card gives way, the field and the keys don't.
    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h692dp-xhdpi")
    fun wagonWheelBudgetPhone() = shoot("wagon_wheel_budget_phone", zone = "Long off") { MatchContent(match(balls), actions) }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h592dp-xhdpi")
    fun wagonWheelSmallPhone() = shoot("wagon_wheel_small_phone") { MatchContent(match(balls), actions) }

    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h568dp-xhdpi")
    fun wagonWheelNarrowPhone() = shoot("wagon_wheel_narrow_phone", zone = "Third man") { MatchContent(match(balls), actions) }
}
