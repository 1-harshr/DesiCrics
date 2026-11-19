package com.harsh.desicrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.MotifArt
import com.harsh.desicrics.designsystem.component.MotifTile
import com.harsh.desicrics.designsystem.component.ThemePicker
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.designsystem.theme.DesiThemeSpec
import com.harsh.desicrics.designsystem.theme.DesiThemes
import com.harsh.desicrics.model.BattingStats
import com.harsh.desicrics.model.BowlingStats
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.InningsScore
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.PlayerPage
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.scoring.withScore
import com.harsh.desicrics.ui.home.HomeMatchCard
import com.harsh.desicrics.ui.match.MatchActions
import com.harsh.desicrics.ui.match.MatchContent
import com.harsh.desicrics.ui.match.MatchUiState
import com.harsh.desicrics.ui.social.PlayerContent
import com.harsh.desicrics.ui.social.PlayerUiState
import com.harsh.desicrics.ui.stats.CareerCards
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.profile_theme
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The scoring screen, match cards, a player page and the theme picker in every theme of
 * [DesiThemes.all], light and dark, saved to app/shared/screenshots/themes/ as `<theme>_<screen>_<mode>`.
 * A new theme gets its pictures by being registered; nothing here needs to change.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ThemeScreenshotTest {

    @Before
    fun setUp() {
        System.setProperty("roborazzi.test.record", "true")
        startKoin { modules(module { single { ApiConfig("http://localhost") } }) }
    }

    @After
    fun tearDown() = stopKoin()

    /** Shoots [content] once per theme and mode. */
    private fun shootEveryTheme(screen: String, content: @Composable (DesiThemeSpec) -> Unit) {
        DesiThemes.all.forEach { spec ->
            listOf(false, true).forEach { dark ->
                val mode = if (dark) "dark" else "light"
                captureRoboImage("screenshots/themes/${spec.id}_${screen}_$mode.png") {
                    DesiTheme(spec, darkTheme = dark, reduceMotion = true) { content(spec) }
                }
            }
        }
    }

    private val strikers = Team("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir").mapIndexed { i, n -> Player("a$i", n, "pa$i") }, teamId = "t1")
    private val royals = Team("Powai XI", listOf("Esha", "Farhan", "Gaurav").mapIndexed { i, n -> Player("b$i", n, "pb$i") }, teamId = "t2")

    private val balls: List<Delivery> = run {
        var seq = 0
        fun ball(runs: Int = 0, extra: ExtraType? = null, extraRuns: Int = 0) = Delivery(seq++, 1, "a0", "a1", "b0", runs, extra, extraRuns, null)
        listOf(ball(runs = 4), ball(runs = 1), ball(), ball(extra = ExtraType.WIDE, extraRuns = 1), ball(runs = 6), ball(runs = 2))
    }

    private fun match(): MatchUiState {
        val base = Match("m", "me", 0, strikers, royals, 5, TeamSide.A, TossDecision.BAT, rules = MatchRules.Standard, scorerUid = "me")
        val scored = base.withScore(ScoringEngine.compute(base, balls))
        return MatchUiState(loading = false, match = scored, deliveries = balls, state = ScoringEngine.compute(scored, balls), isScorer = true)
    }

    private val actions = MatchActions({}, {}, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _, _, _ -> }, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {})

    @Test
    fun scoring() = shootEveryTheme("scoring") { MatchContent(match(), actions) }

    @Test
    fun matchCards() = shootEveryTheme("match_cards") {
        val live = match().match!!.copy(status = MatchStatus.IN_PROGRESS)
        val done = live.copy(
            id = "d", status = MatchStatus.COMPLETED, result = MatchResult(TeamSide.A, 12, MarginUnit.RUNS),
            innings = listOf(InningsScore(TeamSide.A, 64, 3, 30), InningsScore(TeamSide.B, 52, 4, 30)),
        )
        Column(
            Modifier.background(DesiTheme.colors.bg).padding(DesiTheme.space.s4),
            verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
        ) {
            listOf(live, live.copy(status = MatchStatus.SCHEDULED, innings = emptyList()), done).forEach { HomeMatchCard(it, isMine = false, onClick = {}) }
        }
    }

    @Test
    fun playerStats() = shootEveryTheme("player_stats") {
        PlayerContent(
            PlayerUiState(loading = false, page = PlayerPage(PlayerSummary("p", "Aarav Shah", "Mumbai", PlayerRole.ALL_ROUNDER), followers = 12)),
            {}, {}, {}, {},
            extra = {
                Column(Modifier.padding(top = 8.dp)) {
                    CareerCards(
                        CareerStats(
                            "p", matches = 12, won = 7,
                            batting = BattingStats(12, 2, 412, 330, 40, 12, 88, true, 3),
                            bowling = BowlingStats(9, 150, 190, 11, 1, 3, 14),
                        ),
                    )
                }
            },
        )
    }

    /** The picker as the settings sheet shows it, plus the theme's motifs and settings row. */
    @Test
    fun picker() = shootEveryTheme("picker") { spec ->
        Column(
            Modifier.background(DesiTheme.colors.surface).padding(DesiTheme.space.s5),
            verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s4),
        ) {
            ThemePicker(DesiThemes.all, selected = spec, onSelect = {})
            ListCard {
                ListRow(
                    stringResource(Res.string.profile_theme),
                    subtitle = stringResource(spec.name),
                    leading = { MotifTile(DesiTheme.motifs.ball) },
                    onClick = {},
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s4)) {
                val motifs = DesiTheme.motifs
                listOf(motifs.stumps, motifs.ball, motifs.bails).forEach { MotifArt(it, Modifier.size(48.dp)) }
            }
        }
    }
}
