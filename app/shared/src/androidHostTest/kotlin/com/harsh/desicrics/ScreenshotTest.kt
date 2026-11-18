package com.harsh.desicrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.BattingStats
import com.harsh.desicrics.model.Badge
import com.harsh.desicrics.model.BowlingStats
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.FormEntry
import com.harsh.desicrics.model.InningsScore
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Notification
import com.harsh.desicrics.model.NotificationKind
import com.harsh.desicrics.model.PlayerPage
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.PointsRow
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamMember
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.model.TodayFeed
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.model.TournamentTeam
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.scoring.withScore
import com.harsh.desicrics.ui.home.HomeContent
import com.harsh.desicrics.ui.home.HomeUiState
import com.harsh.desicrics.ui.match.MatchActions
import com.harsh.desicrics.ui.match.MatchContent
import com.harsh.desicrics.ui.match.MatchUiState
import com.harsh.desicrics.ui.newmatch.NewMatchActions
import com.harsh.desicrics.ui.newmatch.NewMatchContent
import com.harsh.desicrics.ui.newmatch.NewMatchFormState
import com.harsh.desicrics.ui.newmatch.SideForm
import com.harsh.desicrics.ui.social.NotificationsContent
import com.harsh.desicrics.ui.social.NotificationsUiState
import com.harsh.desicrics.ui.social.PlayerContent
import com.harsh.desicrics.ui.social.PlayerUiState
import com.harsh.desicrics.ui.stats.CareerCards
import com.harsh.desicrics.ui.teams.TeamContent
import com.harsh.desicrics.ui.teams.TeamUiState
import com.harsh.desicrics.ui.tournament.TournamentContent
import com.harsh.desicrics.ui.tournament.TournamentTab
import com.harsh.desicrics.ui.tournament.TournamentUiState
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
 * The main screens drawn on the JVM with sample data and saved to app/shared/screenshots/, so a change
 * to a screen shows up as a changed picture in review. Re-run to refresh them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @Before
    fun setUp() {
        System.setProperty("roborazzi.test.record", "true")
        // Avatars look up the server address to load photos; none of these have one.
        startKoin { modules(module { single { ApiConfig("http://localhost") } }) }
    }

    @After
    fun tearDown() = stopKoin()

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage("screenshots/$name.png") { DesiTheme(darkTheme = dark) { content() } }

    private val strikers = Team("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir", "Ishaan", "Dev").mapIndexed { i, n -> Player("a$i", n, "pa$i") }, teamId = "t1")
    private val royals = Team("Powai XI", listOf("Esha", "Farhan", "Gaurav", "Hari").mapIndexed { i, n -> Player("b$i", n, "pb$i") }, teamId = "t2")

    private fun match(deliveries: List<Delivery>, rules: MatchRules = MatchRules.Standard): MatchUiState {
        val base = Match("m", "me", 0, strikers, royals, 5, TeamSide.A, TossDecision.BAT, rules = rules, scorerUid = "me")
        val state = ScoringEngine.compute(base, deliveries)
        val scored = base.withScore(state)
        return MatchUiState(loading = false, match = scored, deliveries = deliveries, state = ScoringEngine.compute(scored, deliveries), isScorer = true)
    }

    private val balls: List<Delivery> = run {
        var seq = 0
        fun ball(s: String, n: String, b: String, runs: Int = 0, extra: ExtraType? = null, extraRuns: Int = 0, wicket: Wicket? = null) =
            Delivery(seq++, 1, s, n, b, runs, extra, extraRuns, wicket)
        listOf(
            ball("a0", "a1", "b0", runs = 4), ball("a0", "a1", "b0", runs = 1), ball("a1", "a0", "b0"),
            ball("a1", "a0", "b0", extra = ExtraType.NO_BALL, extraRuns = 1), ball("a1", "a0", "b0", runs = 6),
            ball("a1", "a0", "b0", runs = 2),
        )
    }

    private val actions = MatchActions({}, {}, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _, _, _ -> }, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {})

    @Test
    fun scoring() = shoot("scoring") { MatchContent(match(balls), actions) }

    @Test
    fun scoringFreeHitDark() = shoot("scoring_free_hit_dark", dark = true) { MatchContent(match(balls.dropLast(2)), actions) }

    @Test
    fun home() = shoot("home") {
        val live = match(balls).match!!.copy(status = MatchStatus.IN_PROGRESS)
        val done = live.copy(id = "d", status = MatchStatus.COMPLETED, result = com.harsh.desicrics.model.MatchResult(TeamSide.A, 12, com.harsh.desicrics.model.MarginUnit.RUNS),
            innings = listOf(InningsScore(TeamSide.A, 64, 3, 30), InningsScore(TeamSide.B, 52, 4, 30)))
        HomeContent(HomeUiState(loading = false, feed = TodayFeed(mine = listOf(live), recent = listOf(done), unreadNotifications = 2)), "Rohan Iyer", "me", {}, {}, {})
    }

    @Test
    fun newMatch() = shoot("new_match") {
        NewMatchContent(
            NewMatchFormState(
                teamA = SideForm("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir").map { SquadPlayer(it, "id-$it") }, teamId = "t1", captain = 0, keeper = 2),
                teamB = SideForm("Powai XI", listOf(SquadPlayer("Esha"), SquadPlayer("Farhan"))),
                overs = 8,
                rules = MatchRules.Gully,
            ),
            NewMatchActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}),
        )
    }

    @Test
    fun team() = shoot("team") {
        TeamContent(
            TeamUiState(
                myId = "me", loading = false, following = null,
                team = SavedTeam(
                    "t1", "Andheri Strikers", "Mumbai", "me",
                    listOf(
                        TeamMember(PlayerSummary("me", "Rohan Iyer", "Mumbai", PlayerRole.ALL_ROUNDER), TeamRole.ADMIN),
                        TeamMember(PlayerSummary("p2", "Aarav Shah", role = PlayerRole.BOWLER), TeamRole.MEMBER),
                        TeamMember(PlayerSummary("p3", "Kabir", isPlaceholder = true), TeamRole.MEMBER, claimCode = "K7M2QX"),
                    ),
                    joinCode = "AB3XY9",
                ),
                otherTeams = listOf(TeamSummary("t2", "Powai XI", "", 9)),
            ),
            {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun tournamentTable() = shoot("tournament_table") {
        val teams = listOf("Andheri Strikers", "Powai XI", "Bandra Blasters", "Juhu Kings").mapIndexed { i, n -> TournamentTeam("t$i", n, i + 1) }
        TournamentContent(
            TournamentUiState(
                myId = "me", loading = false,
                details = TournamentDetails(
                    Tournament("x", "Andheri Premier League", "Mumbai", TournamentFormat.ROUND_ROBIN, 8, createdBy = "me", teams = teams, started = true),
                    tables = listOf(teams.mapIndexed { i, t -> PointsRow(t.teamId, t.name, 3, 3 - i, i, 0, 150 - i * 10, 144, 140, 144) }),
                ),
            ),
            {}, {}, {}, {}, {}, {}, { _, _ -> },
            initialTab = TournamentTab.TABLE,
        )
    }

    @Test
    fun playerStats() = shoot("player_stats") {
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
                            mvpTenths = 5124,
                            form = (1..10).map { FormEntry("m$it", it.toLong(), it * 9 % 70, it % 3 == 0, 1, true) },
                            badges = listOf(Badge.FIRST_FIFTY, Badge.PLAYER_OF_THE_MATCH),
                        ),
                    )
                }
            },
        )
    }

    @Test
    fun notificationsDark() = shoot("notifications_dark", dark = true) {
        NotificationsContent(
            NotificationsUiState(
                loading = false,
                notifications = listOf(
                    Notification("1", NotificationKind.MILESTONE_RUNS, "m", "Strikers vs Royals", "Aarav", 50, createdAt = 0),
                    Notification("2", NotificationKind.MATCH_RESULT, "m", "Strikers vs Royals", detail = "Strikers won by 12 runs", createdAt = 0, read = true),
                    Notification("3", NotificationKind.FIXTURE_RESCHEDULED, "m", "Juhu Kings vs Powai XI", detail = "1800000000000", createdAt = 0),
                ),
            ),
            {}, {},
        )
    }
}
