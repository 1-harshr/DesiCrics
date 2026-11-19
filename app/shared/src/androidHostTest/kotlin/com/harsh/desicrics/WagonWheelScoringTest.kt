package com.harsh.desicrics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.FieldZone
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Wagon wheel first: tap where the ball went, then the runs. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class WagonWheelScoringTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        startKoin { modules(module { single { ApiConfig("http://localhost") } }) }
    }

    @After
    fun tearDown() = stopKoin()

    private val strikers = Team("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir").mapIndexed { i, n -> Player("a$i", n) })
    private val royals = Team("Powai XI", listOf("Esha", "Farhan", "Gaurav").mapIndexed { i, n -> Player("b$i", n) })

    /** Every ball the screen recorded, as (runs, zone). */
    private val recorded = mutableListOf<Pair<Int, FieldZone?>>()
    private var ui by mutableStateOf(MatchUiState())
    private var undone = 0

    /** One ball in, scored by this phone; each ball the screen records is added, so the screen moves on. */
    private fun score(rules: MatchRules) {
        val base = Match("m", "me", 0, strikers, royals, 5, TeamSide.A, TossDecision.BAT, rules = rules, scorerUid = "me")
        fun uiFor(deliveries: List<Delivery>): MatchUiState {
            val match = base.withScore(ScoringEngine.compute(base, deliveries))
            return MatchUiState(loading = false, match = match, deliveries = deliveries, state = ScoringEngine.compute(match, deliveries), isScorer = true)
        }
        ui = uiFor(listOf(Delivery(0, 1, "a0", "a1", "b0", runsOffBat = 1)))
        val actions = MatchActions(
            {}, {}, {}, {},
            onRuns = { runs, zone ->
                recorded += runs to zone
                ui = uiFor(ui.deliveries + Delivery(ui.nextSeq, 1, ui.strikerId!!, ui.nonStrikerId, "b0", runsOffBat = runs, zone = zone))
            },
            {}, {}, { _, _ -> }, { _, _, _, _ -> },
            onUndo = { undone++; ui = uiFor(ui.deliveries.dropLast(1)) },
            {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {},
        )
        compose.setContent { DesiTheme(reduceMotion = true) { MatchContent(ui, actions) } }
    }

    private val field get() = compose.onNodeWithContentDescription("Where the next shot went", substring = true)

    private fun tapZone(name: String) {
        val action = field.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == name }
        compose.runOnIdle { action.action() }
    }

    private fun armedZone(): String? =
        field.fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }

    private fun tapKey(label: String) = compose.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()

    @Test
    fun anArmedZoneGoesWithTheNextRunsAndThenClears() {
        score(MatchRules.Relaxed)
        tapZone("Cover")
        assertEquals("Cover", armedZone())
        tapKey("4")
        assertEquals<List<Pair<Int, FieldZone?>>>(listOf(4 to FieldZone.COVER), recorded)
        compose.waitForIdle()
        assertNull(armedZone())
    }

    @Test
    fun tappingTheArmedZoneAgainClearsIt() {
        score(MatchRules.Relaxed)
        tapZone("Fine leg")
        tapZone("Point")
        assertEquals("Point", armedZone())
        tapZone("Point")
        assertNull(armedZone())
    }

    @Test
    fun aDotBallTakesNoZoneAndClearsTheArmedOne() {
        score(MatchRules.Relaxed)
        tapZone("Long on")
        tapKey("0")
        assertEquals<List<Pair<Int, FieldZone?>>>(listOf(0 to null), recorded)
        compose.waitForIdle()
        assertNull(armedZone())
    }

    @Test
    fun runsWithNoZoneArmedAskWhereAndCanSkip() {
        score(MatchRules.Relaxed)
        tapKey("2")
        compose.onNodeWithText("Skip").assertExists()
        assertEquals(0, recorded.size)
        compose.onNodeWithText("Skip").performClick()
        assertEquals<List<Pair<Int, FieldZone?>>>(listOf(2 to null), recorded)
    }

    @Test
    fun undoClearsTheArmedZone() {
        score(MatchRules.Relaxed)
        tapZone("Mid-wicket")
        tapKey("Undo")
        // Undo asks first.
        compose.onNode(hasText("Undo") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        check(undone == 1)
        assertNull(armedZone())
    }

    @Test
    fun withTheWagonWheelOffThereIsNoFieldAndRunsGoStraightIn() {
        score(MatchRules())
        field.assertDoesNotExist()
        tapKey("4")
        assertEquals<List<Pair<Int, FieldZone?>>>(listOf(4 to null), recorded)
    }
}
