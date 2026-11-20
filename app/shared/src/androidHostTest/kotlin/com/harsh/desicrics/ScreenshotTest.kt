package com.harsh.desicrics

import androidx.compose.foundation.layout.Arrangement
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.AbandonReason
import com.harsh.desicrics.model.MatchEnding
import com.harsh.desicrics.model.LeaderboardRow
import com.harsh.desicrics.ui.stats.LeaderboardUiState
import com.harsh.desicrics.ui.stats.LeaderboardContent
import com.harsh.desicrics.ui.match.ScoringError

import com.harsh.desicrics.ui.teams.Insights
import com.harsh.desicrics.model.WinRecord
import com.harsh.desicrics.model.TeamInsights
import com.harsh.desicrics.model.PhaseStat
import com.harsh.desicrics.model.Performer
import com.harsh.desicrics.model.InningsPhase
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
import com.harsh.desicrics.model.TournamentPlayer
import com.harsh.desicrics.model.EntryPolicy
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.Organiser
import com.harsh.desicrics.model.OrganiserRole
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.scoring.withScore
import com.harsh.desicrics.ui.home.HomeContent
import com.harsh.desicrics.ui.onboarding.OnboardingActions
import com.harsh.desicrics.ui.onboarding.OnboardingContent
import com.harsh.desicrics.ui.onboarding.OnboardingStep
import com.harsh.desicrics.ui.onboarding.OnboardingUiState
import com.harsh.desicrics.ui.onboarding.PreviewOnboardingForm
import com.harsh.desicrics.ui.onboarding.PreviewOnboardingState
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
import com.harsh.desicrics.ui.teams.TeamsContent
import com.harsh.desicrics.ui.teams.TeamsUiState
import com.harsh.desicrics.ui.tournament.TournamentsContent
import com.harsh.desicrics.ui.tournament.TournamentsUiState
import com.harsh.desicrics.model.TournamentSummary
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

    // Reduced motion keeps looping animations (the live dot) still, so the screen settles and can be captured.
    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage("screenshots/$name.png") { DesiTheme(darkTheme = dark, reduceMotion = true) { content() } }

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

    // A common budget phone, and a small old one, less the status and navigation bars: the batters, bowler
    // and this over must stay in view above the pad.
    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h692dp-xhdpi")
    fun scoringBudgetPhone() = shoot("scoring_budget_phone") { MatchContent(match(balls), actions) }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h692dp-xhdpi")
    fun errorScoringBudgetPhone() = shoot("error_scoring_budget_phone") {
        MatchContent(match(balls).copy(offline = true, confirmedAt = 0, error = ScoringError.SaveFailed), actions)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h592dp-xhdpi")
    fun scoringSmallPhone() = shoot("scoring_small_phone") { MatchContent(match(balls), actions) }

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
    fun matchCardsDark() = shoot("match_cards_dark", dark = true) {
        val live = match(balls).match!!.copy(
            status = MatchStatus.IN_PROGRESS,
            teamA = strikers.copy(name = "Andheri Community Cricket Club"),
        )
        Column(Modifier.background(DesiTheme.colors.bg).padding(DesiTheme.space.s4),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(DesiTheme.space.s3)) {
            listOf(live, live.copy(status = MatchStatus.SCHEDULED, innings = emptyList()),
                live.copy(status = MatchStatus.COMPLETED, result = com.harsh.desicrics.model.MatchResult(
                    TeamSide.A, 2, com.harsh.desicrics.model.MarginUnit.RUNS),
                    innings = listOf(InningsScore(TeamSide.A, 52, 2, 30), InningsScore(TeamSide.B, 50, 4, 30))),
            ).forEach { com.harsh.desicrics.ui.home.HomeMatchCard(it, isMine = false, onClick = {}) }
        }
    }

    @Test
    fun newMatch() = setupPage("new_match", 0, false)
    @Test fun teamADark() = setupPage("team_a_dark", 0, true)
    @Test fun teamB() = setupPage("team_b", 1, false)
    @Test fun teamBDark() = setupPage("team_b_dark", 1, true)
    @Test fun matchDetails() = setupPage("match_details", 2, false)
    @Test fun matchDetailsDark() = setupPage("match_details_dark", 2, true)

    private fun setupPage(name: String, step: Int, dark: Boolean) = shoot(name, dark) {
        NewMatchContent(
            NewMatchFormState(
                teamA = SideForm("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir").map { SquadPlayer(it, "id-$it") }, teamId = "t1", captain = 0, keeper = 2),
                teamB = SideForm("Powai XI", listOf(SquadPlayer("Esha"), SquadPlayer("Farhan"))),
                overs = 8,
                rules = MatchRules.Gully,
            ),
            NewMatchActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}), initialStep = step,
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
                    code = "48217390",
                    joinRequests = listOf(PlayerSummary("p4", "Ishaan Rao", "Mumbai")),
                ),
                otherTeams = listOf(TeamSummary("t2", "Powai XI", "", 9)),
            ),
            {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun teamAsOutsider() = shoot("team_outsider") {
        TeamContent(
            TeamUiState(
                myId = "me", loading = false, following = false,
                team = SavedTeam(
                    "t1", "Andheri Strikers", "Mumbai", "p2",
                    listOf(TeamMember(PlayerSummary("p2", "Aarav Shah", role = PlayerRole.BOWLER), TeamRole.ADMIN)),
                    requested = true,
                ),
            ),
            {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun teamInsights() = shoot("team_insights") {
        Column(Modifier.background(DesiTheme.colors.bg).padding(16.dp)) {
            Insights(
                TeamInsights(
                    teamId = "t1", played = 14, won = 9, lost = 4, tied = 1,
                    battingFirst = WinRecord(8, 6), chasing = WinRecord(6, 3), tossWon = WinRecord(7, 5), averageFirstInnings = 87,
                    batting = listOf(PhaseStat(InningsPhase.START, 312, 224, 11), PhaseStat(InningsPhase.MIDDLE, 241, 168, 14), PhaseStat(InningsPhase.FINISH, 398, 230, 27)),
                    bowling = listOf(PhaseStat(InningsPhase.START, 280, 236, 15), PhaseStat(InningsPhase.MIDDLE, 230, 170, 12), PhaseStat(InningsPhase.FINISH, 371, 216, 31)),
                    howOut = mapOf(DismissalKind.CAUGHT to 28, DismissalKind.BOWLED to 14, DismissalKind.RUN_OUT to 7, DismissalKind.LBW to 3),
                    topRunScorers = listOf(Performer("Rohan Iyer", "me", 412, 14), Performer("Aarav Shah", "p2", 288, 13), Performer("Kabir", null, 190, 11)),
                    topWicketTakers = listOf(Performer("Aarav Shah", "p2", 21, 13), Performer("Dev", null, 12, 9)),
                ),
            )
        }
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
                    Notification("4", NotificationKind.TEAM_JOIN_REQUEST, "", "Andheri Strikers", "Ishaan Rao", createdAt = 0, teamId = "t1"),
                ),
            ),
            {}, {},
        )
    }

    @Test
    fun teamsList() = shoot("teams_list") {
        TeamsContent(
            TeamsUiState(
                loading = false,
                teams = listOf(TeamSummary("t1", "Andheri Strikers", "Mumbai", 14, TeamRole.ADMIN), TeamSummary("t2", "Powai XI", "", 9)),
                browsed = listOf(TeamSummary("t1", "Andheri Strikers", "Mumbai", 14), TeamSummary("t3", "Bandra Blasters", "Mumbai", 11), TeamSummary("t4", "Juhu Jets", "Mumbai", 12)),
                browsing = false,
                browsedAll = true,
            ),
            {}, {}, {}, {},
        )
    }

    @Test
    fun tournamentsListDark() = shoot("tournaments_list_dark", dark = true) {
        TournamentsContent(
            TournamentsUiState(
                loading = false,
                tournaments = listOf(
                    TournamentSummary("x", "Andheri Premier League", "Mumbai", TournamentFormat.ROUND_ROBIN, 8, isOrganiser = true),
                    TournamentSummary("y", "Monsoon Cup", "", TournamentFormat.KNOCKOUT, 16),
                ),
            ),
            {}, {}, {},
        )
    }

    @Test
    fun errorTeam() = shoot("error_team") {
        TeamContent(TeamUiState(myId = "me", loading = false, loadFailed = true), {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun errorNotificationsDark() = shoot("error_notifications_dark", dark = true) {
        NotificationsContent(NotificationsUiState(loading = false, loadFailed = true), {}, {})
    }

    @Test
    fun errorScoring() = shoot("error_scoring") {
        MatchContent(match(balls).copy(offline = true, confirmedAt = 0, error = ScoringError.SaveFailed), actions)
    }

    @Test
    fun leaderboard() = shoot("leaderboard") {
        val names = listOf("Aarav Shah", "Rohan Iyer", "Kabir Mehta", "Ishaan Rao", "Dev Patel", "Farhan Ali")
        LeaderboardContent(
            LeaderboardUiState(loading = false, rows = names.mapIndexed { i, n -> LeaderboardRow(PlayerSummary("p$i", n), 412 - i * 47, 12 - i) }),
            forTournament = false, onUpdate = {}, onOpenPlayer = {}, onBack = {},
        )
    }

    @Test
    fun leaderboardEmptyDark() = shoot("leaderboard_empty_dark", dark = true) {
        LeaderboardContent(LeaderboardUiState(loading = false), forTournament = false, onUpdate = {}, onOpenPlayer = {}, onBack = {})
    }

    @Test
    fun endedMatches() = shoot("ended_matches") {
        val base = Match("e", "me", 0, strikers, royals, 5, TeamSide.A, TossDecision.BAT, status = MatchStatus.COMPLETED)
        Column(Modifier.background(DesiTheme.colors.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                base.copy(id = "rain", innings = listOf(InningsScore(TeamSide.A, 38, 2, 22)), result = MatchResult(null, ending = MatchEnding.ABANDONED, reason = AbandonReason.RAIN)),
                base.copy(id = "walk", result = MatchResult(TeamSide.B, ending = MatchEnding.WALKOVER)),
                base.copy(id = "gave", innings = listOf(InningsScore(TeamSide.A, 91, 3, 30), InningsScore(TeamSide.B, 12, 6, 14)), result = MatchResult(TeamSide.A, ending = MatchEnding.CONCEDED)),
            ).forEach { com.harsh.desicrics.ui.home.HomeMatchCard(it, isMine = false, onClick = {}) }
        }
    }

    @Test
    fun walkover() = shoot("walkover") {
        Column(Modifier.background(DesiTheme.colors.bg)) {
            com.harsh.desicrics.ui.match.EndedBeforePlay(
                Match("w", "me", 0, strikers, royals, 5, TeamSide.A, TossDecision.BAT, status = MatchStatus.COMPLETED),
                MatchResult(TeamSide.B, ending = MatchEnding.WALKOVER), canUndo = true, onUndo = {}, onBack = {},
            )
        }
    }

    // Audit: screens with no other screenshot.

    @Test
    fun auditSignIn() = shoot("audit_sign_in") {
        com.harsh.desicrics.ui.signin.SignInContent(com.harsh.desicrics.ui.signin.SignInUiState(), emptyList(), {}, {}, {}, {})
    }

    // Onboarding: one shot per step, plus a new player with nothing filled in yet.

    @Test
    fun onboardingAboutEmpty() = shoot("onboarding_about_empty") {
        OnboardingContent(com.harsh.desicrics.ui.profile.ProfileFormState(loading = false), OnboardingUiState(), OnboardingActions())
    }

    @Test
    fun onboardingSteps() = OnboardingStep.entries.forEach { step ->
        shoot("onboarding_${step.name.lowercase()}") { OnboardingContent(PreviewOnboardingForm, PreviewOnboardingState.copy(step = step), OnboardingActions()) }
    }

    @Test
    fun onboardingGameDark() = shoot("onboarding_game_dark", dark = true) {
        OnboardingContent(PreviewOnboardingForm, PreviewOnboardingState.copy(step = OnboardingStep.GAME), OnboardingActions())
    }

    @Test
    fun onboardingDone() = shoot("onboarding_team_done") {
        OnboardingContent(PreviewOnboardingForm, PreviewOnboardingState.copy(step = OnboardingStep.TEAM, teamName = "Andheri Strikers"), OnboardingActions())
    }

    @Test
    fun auditFixtureStart() = shoot("audit_fixture_start") {
        com.harsh.desicrics.ui.match.FixturePending(
            Match("f", "me", 0, strikers, royals, 5, TeamSide.A, TossDecision.BAT, status = MatchStatus.SCHEDULED),
            com.harsh.desicrics.ui.match.FixtureSetup(canStart = true, teamA = null, teamB = null), { _, _, _, _ -> }, {},
        )
    }

    @Test
    fun auditScorecard() = shoot("audit_scorecard") {
        val state = match(balls).state!!
        Column(Modifier.background(DesiTheme.colors.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            com.harsh.desicrics.ui.match.InningsScorecard(state.current)
            com.harsh.desicrics.ui.match.InningsOvers(state.current, showTitle = false)
        }
    }

    @Test
    fun auditTournamentTabs() = shoot("audit_tournament_teams") {
        val teams = listOf("Andheri Strikers", "Powai XI", "Bandra Blasters", "Juhu Kings").mapIndexed { i, n -> TournamentTeam("t$i", n, i + 1) }
        TournamentContent(
            TournamentUiState(myId = "me", loading = false, details = TournamentDetails(Tournament("x", "Andheri Premier League", "Mumbai", TournamentFormat.ROUND_ROBIN, 8, createdBy = "me", teams = teams))),
            {}, {}, {}, {}, {}, {}, { _, _ -> },
            initialTab = TournamentTab.TEAMS,
        )
    }

    /** A league taking teams on approval and players openly, with its pool, a waiting player and two squads. */
    private fun entryTournament(): Tournament {
        val teams = listOf("Andheri Strikers", "Powai XI").mapIndexed { i, n -> TournamentTeam("t$i", n, i + 1) } +
            TournamentTeam("t2", "Bandra Blasters", 3, status = EntryStatus.PENDING)
        fun p(id: String, name: String, role: PlayerRole) = PlayerSummary(id, name, role = role)
        val players = listOf(
            TournamentPlayer(p("p1", "Rohan Iyer", PlayerRole.BATTER), "t0"),
            TournamentPlayer(p("p2", "Aarav Shah", PlayerRole.BOWLER), "t0"),
            TournamentPlayer(p("p3", "Esha Rao", PlayerRole.ALL_ROUNDER), "t1"),
            TournamentPlayer(p("p4", "Kabir Mehta", PlayerRole.WICKET_KEEPER), fromPool = true),
            TournamentPlayer(p("p5", "Neel Joshi", PlayerRole.BOWLER), status = EntryStatus.PENDING, fromPool = true),
        )
        return Tournament(
            "x", "Andheri Premier League", "Mumbai", TournamentFormat.ROUND_ROBIN, 8, createdBy = "org",
            teamEntry = EntryPolicy.APPROVAL, playerEntry = EntryPolicy.OPEN,
            organisers = listOf(Organiser("org", "Harsh", OrganiserRole.ORGANISER)), teams = teams, players = players,
        )
    }

    @Test
    fun tournamentPlayersForOrganisers() = shoot("tournament_players") {
        TournamentContent(
            TournamentUiState(myId = "org", loading = false, details = TournamentDetails(entryTournament())),
            {}, {}, {}, {}, {}, {}, { _, _ -> },
            initialTab = TournamentTab.PLAYERS,
        )
    }

    @Test
    fun tournamentTeamsForOrganisers() = shoot("tournament_entry_settings") {
        TournamentContent(
            TournamentUiState(myId = "org", loading = false, details = TournamentDetails(entryTournament())),
            {}, {}, {}, {}, {}, {}, { _, _ -> },
            initialTab = TournamentTab.TEAMS,
        )
    }

    @Test
    fun tournamentEntryCardForVisitors() = shoot("tournament_entry_card") {
        TournamentContent(
            TournamentUiState(
                myId = "me",
                loading = false,
                details = TournamentDetails(entryTournament()),
                myTeams = listOf(TeamSummary("t9", "Juhu Kings", "Mumbai", 11, myRole = TeamRole.ADMIN)),
            ),
            {}, {}, {}, {}, {}, {}, { _, _ -> },
        )
    }

    @Test
    fun tournamentInviteForATeamAdmin() = shoot("tournament_entry_invited") {
        val t = entryTournament()
        TournamentContent(
            TournamentUiState(
                myId = "me",
                loading = false,
                details = TournamentDetails(t.copy(teams = t.teams + TournamentTeam("t9", "Juhu Kings", 4, status = EntryStatus.INVITED))),
                myTeams = listOf(TeamSummary("t9", "Juhu Kings", "Mumbai", 11, myRole = TeamRole.ADMIN)),
            ),
            {}, {}, {}, {}, {}, {}, { _, _ -> },
        )
    }
}
