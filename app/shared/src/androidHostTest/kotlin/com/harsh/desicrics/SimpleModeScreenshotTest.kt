package com.harsh.desicrics

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.harsh.desicrics.data.AppMode
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.InningsScore
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TodayFeed
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.shell.AppModeSheet
import com.harsh.desicrics.simple.SimpleHomeContent
import com.harsh.desicrics.simple.SimpleMatchContent
import com.harsh.desicrics.simple.SimpleMeContent
import com.harsh.desicrics.simple.SimpleNewMatchContent
import com.harsh.desicrics.simple.SimplePreviewActions
import com.harsh.desicrics.simple.SimplePreviewForm
import com.harsh.desicrics.simple.SimplePreviewMatches
import com.harsh.desicrics.simple.simplePreviewUi
import com.harsh.desicrics.ui.home.HomeUiState
import com.harsh.desicrics.ui.match.Selection
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Simple mode's own screens, light and dark, saved to app/shared/screenshots/simple/. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class SimpleModeScreenshotTest {

    @Before
    fun setUp() {
        System.setProperty("roborazzi.test.record", "true")
        startKoin { modules(module { single { ApiConfig("http://localhost") } }) }
    }

    @After
    fun tearDown() = stopKoin()

    private fun shoot(name: String, content: @Composable () -> Unit) = listOf(false, true).forEach { dark ->
        captureRoboImage("screenshots/simple/${name}_${if (dark) "dark" else "light"}.png") {
            DesiTheme(darkTheme = dark, reduceMotion = true) { content() }
        }
    }

    private val profile = UserProfile(uid = "me", name = "Rohan Mehta", phone = "", city = "Mumbai")

    private val finished = SimplePreviewMatches.first().copy(
        id = "done", status = MatchStatus.COMPLETED,
        innings = listOf(InningsScore(TeamSide.B, 84, 3, 60), InningsScore(TeamSide.A, 71, 9, 60)),
        result = MatchResult(TeamSide.B, 13, MarginUnit.RUNS),
    )

    @Test
    fun home() = shoot("home") {
        SimpleHomeContent(HomeUiState(loading = false, feed = TodayFeed(mine = SimplePreviewMatches + finished)), profile.name, null, {}, {}, {}, {}, {})
    }

    @Test
    fun homeEmpty() = shoot("home_empty") {
        SimpleHomeContent(HomeUiState(loading = false), profile.name, null, {}, {}, {}, {}, {})
    }

    @Test
    fun newMatch() = shoot("new_match") {
        SimpleNewMatchContent(SimplePreviewForm, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {})
    }

    @Test
    fun scoring() = shoot("scoring") { SimpleMatchContent(simplePreviewUi(), SimplePreviewActions) }

    @Test
    fun pickingOpeners() = shoot("scoring_start") {
        SimpleMatchContent(simplePreviewUi(emptyList()).copy(selection = Selection(strikerId = "a0")), SimplePreviewActions)
    }

    @Test
    fun finishedMatch() = shoot("scoring_finished") {
        // Two balls of a one-over chase after a 6-run first innings: enough for a result.
        val ui = simplePreviewUi(listOf(6, 0, 0, 0, 0, 0))
        val match = ui.match!!.copy(oversPerInnings = 1)
        val deliveries = ui.deliveries + listOf(4, 4).mapIndexed { i, r ->
            ui.deliveries.first().copy(seq = 6 + i, innings = 2, strikerId = "b0", nonStrikerId = "b1", bowlerId = "a2", runsOffBat = r)
        }
        SimpleMatchContent(ui.copy(match = match, deliveries = deliveries, state = ScoringEngine.compute(match, deliveries, emptyList())), SimplePreviewActions)
    }

    @Test
    fun me() = shoot("me") { SimpleMeContent(profile, SimplePreviewMatches, AppMode.SIMPLE, {}, {}, {}) }

    @Test
    fun modeSheet() = shoot("mode_sheet") { AppModeSheet(AppMode.SIMPLE, {}, {}) }
}
