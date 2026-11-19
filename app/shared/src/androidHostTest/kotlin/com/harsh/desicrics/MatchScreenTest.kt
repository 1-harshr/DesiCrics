package com.harsh.desicrics

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEnding
import com.harsh.desicrics.model.MatchEvent
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

/** The match screen's own behaviour: which tab it opens on, and what each shows. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class MatchScreenTest {

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
    private val actions = MatchActions({}, {}, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _, _, _ -> }, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {})

    /** Two balls, then Powai concede: finished, watched by someone who isn't scoring it. */
    private fun finished(): MatchUiState {
        val base = Match("m", "me", 0, strikers, royals, 5, TeamSide.A, TossDecision.BAT, scorerUid = "someone")
        val deliveries = listOf(Delivery(0, 1, "a0", "a1", "b0", runsOffBat = 4), Delivery(1, 1, "a0", "a1", "b0", runsOffBat = 1))
        val events = listOf(MatchEvent.EndMatch(2, 1, MatchEnding.CONCEDED, winner = TeamSide.A))
        val match = base.withScore(ScoringEngine.compute(base, deliveries, events))
        return MatchUiState(loading = false, match = match, deliveries = deliveries, events = events, state = ScoringEngine.compute(match, deliveries, events))
    }

    @Test
    fun finishedMatchOpensOnItsScorecardAndLiveShowsTheResult() {
        compose.setContent { DesiTheme(reduceMotion = true) { MatchContent(finished(), actions) } }
        val result = "Andheri Strikers won · Powai XI conceded"
        // The scorecard, under the scoreboard's result line.
        compose.onAllNodesWithText("not out").fetchSemanticsNodes().isNotEmpty().let { check(it) }
        compose.onAllNodesWithText(result).assertCountEquals(1)

        compose.onNodeWithText("Live").performClick()
        compose.onAllNodesWithText(result).assertCountEquals(2)
        compose.onAllNodesWithText("Only the scorer", substring = true).assertCountEquals(0)
    }
}
