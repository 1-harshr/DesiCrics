package com.harsh.desicrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.FixtureInfo
import com.harsh.desicrics.model.InningsScore
import com.harsh.desicrics.model.KeyStats
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Organiser
import com.harsh.desicrics.model.OrganiserRole
import com.harsh.desicrics.model.PointsRow
import com.harsh.desicrics.model.Stage
import com.harsh.desicrics.model.StatLeader
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.model.TournamentTeam
import com.harsh.desicrics.ui.share.ShareContent
import com.harsh.desicrics.ui.share.ShareFormat
import com.harsh.desicrics.ui.share.ShareLook
import com.harsh.desicrics.ui.share.ShareStudioContent
import com.harsh.desicrics.ui.share.TemplatePreview
import com.harsh.desicrics.ui.share.tournamentShareContents
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * Every tournament poster template drawn at its design size (360 dp at xxhdpi, so 1080 px wide like the
 * shared file) and saved to app/shared/screenshots/share/. Times are drawn in India's time zone so the
 * pictures don't depend on the machine. Re-run to refresh them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class TournamentPosterScreenshotTest {

    private val defaultZone: TimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        System.setProperty("roborazzi.test.record", "true")
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        startKoin { modules(module { single { ApiConfig("http://localhost") } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
        TimeZone.setDefault(defaultZone)
    }

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage("screenshots/share/$name.png") { DesiTheme(darkTheme = dark, reduceMotion = true) { content() } }

    private val names = listOf(
        "Andheri Strikers", "Powai XI", "Bandra Blasters", "Juhu Warriors", "Malad Mavericks",
        "Goregaon Giants", "Borivali Bulls", "Dadar Dynamos", "Kurla Kings", "Chembur Chargers",
    )
    private val teams = names.mapIndexed { i, n -> TournamentTeam("t$i", n, seed = i + 1, group = i % 2) }
    private fun side(i: Int) = Team(names[i], emptyList(), teamId = "t$i")

    /** Saturday 21 November 2026, 9:00 in Mumbai. */
    private val saturday = 1_795_231_800_000L
    private val slot = 150 * 60_000L

    private var seq = 0
    private fun fixture(
        a: Int, b: Int, stage: Stage, round: Int, number: Int, group: Int? = null,
        at: Long? = null, score: Pair<Int, Int>? = null, venue: String = "",
    ): Match {
        val done = score != null
        return Match(
            id = "m${seq++}", createdBy = "me", createdAt = 0, teamA = side(a), teamB = side(b), oversPerInnings = 8,
            tossWinner = TeamSide.A, tossDecision = TossDecision.BAT,
            status = if (done) MatchStatus.COMPLETED else MatchStatus.SCHEDULED,
            innings = if (score == null) emptyList() else listOf(
                InningsScore(TeamSide.A, score.first, 5, 48),
                InningsScore(TeamSide.B, score.second, if (score.second > score.first) 4 else 9, 44),
            ),
            result = score?.let { (x, y) -> if (x > y) MatchResult(TeamSide.A, x - y, MarginUnit.RUNS) else MatchResult(TeamSide.B, 6, MarginUnit.WICKETS) },
            scheduledAt = at,
            fixture = FixtureInfo("apl", stage, round, number, group),
            venue = venue,
        )
    }

    private val organisers = listOf(Organiser("u1", "Rahul Desai", OrganiserRole.ORGANISER))

    /** Two groups of five, halfway through the league, with Saturday's six fixtures still to play. */
    private val league: TournamentDetails = run {
        val tournament = Tournament(
            "apl", "Andheri Premier League", "Mumbai", TournamentFormat.GROUPS_KNOCKOUT, 8,
            groups = 2, advance = 2, createdBy = "u1", organisers = organisers, teams = teams, started = true,
        )
        val played = listOf(
            fixture(0, 2, Stage.LEAGUE, 1, 1, 0, score = 142 to 120), fixture(4, 6, Stage.LEAGUE, 1, 2, 0, score = 98 to 101),
            fixture(1, 3, Stage.LEAGUE, 1, 3, 1, score = 131 to 99), fixture(5, 7, Stage.LEAGUE, 1, 4, 1, score = 88 to 112),
        )
        val saturdayGames = listOf(
            fixture(0, 4, Stage.LEAGUE, 2, 1, 0, at = saturday, venue = "Andheri Sports Complex"),
            fixture(2, 8, Stage.LEAGUE, 2, 2, 0, at = saturday + slot, venue = "Andheri Sports Complex"),
            fixture(1, 5, Stage.LEAGUE, 2, 3, 1, at = saturday + 2 * slot, venue = "Powai Ground"),
            fixture(3, 9, Stage.LEAGUE, 2, 4, 1, at = saturday + 3 * slot, venue = "Powai Ground"),
            fixture(6, 8, Stage.LEAGUE, 2, 5, 0, at = saturday + 4 * slot, venue = "Andheri Sports Complex"),
            fixture(7, 9, Stage.LEAGUE, 2, 6, 1, at = saturday + 5 * slot, venue = "Powai Ground"),
        )
        // Sunday's games aren't on Saturday's poster.
        val sunday = listOf(fixture(0, 6, Stage.LEAGUE, 3, 1, 0, at = saturday + 24 * 60 * 60_000L))
        fun row(i: Int, p: Int, w: Int, runsFor: Int, runsAgainst: Int) =
            PointsRow("t$i", names[i], played = p, won = w, lost = p - w, runsFor = runsFor, ballsFaced = p * 48, runsAgainst = runsAgainst, ballsBowled = p * 48)
        TournamentDetails(
            tournament,
            fixtures = played + saturdayGames + sunday,
            tables = listOf(
                listOf(row(0, 2, 2, 270, 221), row(6, 2, 2, 230, 205), row(2, 2, 1, 240, 236), row(4, 2, 0, 198, 230), row(8, 2, 0, 190, 236)),
                listOf(row(1, 2, 2, 262, 200), row(7, 2, 1, 224, 210), row(3, 2, 1, 211, 220), row(5, 2, 1, 200, 214), row(9, 2, 0, 180, 233)),
            ),
        )
    }

    /** An eight-team knockout that's over: Strikers beat Royals in the final. */
    private val knockout: TournamentDetails = run {
        val tournament = Tournament(
            "apl", "Monsoon Cup", "Mumbai", TournamentFormat.KNOCKOUT, 6,
            createdBy = "u1", organisers = organisers, teams = teams.take(8).map { it.copy(group = null) }, started = true,
        )
        val fixtures = listOf(
            fixture(0, 7, Stage.QUARTERFINAL, 1, 1, score = 96 to 71), fixture(1, 6, Stage.QUARTERFINAL, 1, 2, score = 84 to 88),
            fixture(2, 5, Stage.QUARTERFINAL, 1, 3, score = 77 to 70), fixture(3, 4, Stage.QUARTERFINAL, 1, 4, score = 101 to 64),
            fixture(0, 3, Stage.SEMIFINAL, 2, 1, score = 110 to 92), fixture(2, 6, Stage.SEMIFINAL, 2, 2, score = 66 to 70),
            fixture(0, 6, Stage.FINAL, 3, 1, score = 124 to 117),
        )
        TournamentDetails(
            tournament, fixtures,
            keyStats = KeyStats(
                mostRuns = listOf(StatLeader("Rohan Iyer", names[0], 168, "p1"), StatLeader("Kabir Shah", names[6], 140)),
                mostWickets = listOf(StatLeader("Dev Patil", names[6], 9), StatLeader("Rohan Iyer", names[0], 6, "p1")),
            ),
            championId = "t0",
            playerOfTournament = StatLeader("Rohan Iyer", names[0], 1845, "p1"),
        )
    }

    /** The same knockout mid-way: quarter-finals done, one semi-final played, the other on Saturday. */
    private val knockoutLive: TournamentDetails = knockout.copy(
        fixtures = knockout.fixtures.take(5) + fixture(2, 6, Stage.SEMIFINAL, 2, 2, at = saturday + 3 * slot),
        championId = null,
        playerOfTournament = null,
    )

    /** Not started yet: entries are open. */
    private val announced = TournamentDetails(
        Tournament(
            "apl", "Andheri Premier League", "Mumbai", TournamentFormat.GROUPS_KNOCKOUT, 8, groups = 2, advance = 2,
            createdBy = "u1", startsAt = saturday + 7 * 24 * 60 * 60_000L, registrationOpen = true, organisers = organisers, teams = teams.take(6),
        ),
    )

    private fun content(details: TournamentDetails, key: String) = tournamentShareContents(details).first { it.key == key }

    private fun shootTemplates(name: String, content: ShareContent, format: ShareFormat = ShareFormat.STORY) =
        content.templates.forEach { template ->
            shoot("${name}_${template.key}_${format.name.lowercase()}") {
                Box(Modifier.width(ShareFormat.DESIGN_WIDTH)) { TemplatePreview(template, format, ShareLook.Default) }
            }
        }

    @Test
    fun contentsFollowTheTournament() {
        assertEquals(listOf("announcement"), tournamentShareContents(announced).map { it.key })
        assertEquals(listOf("fixtures", "table", "announcement"), tournamentShareContents(league).map { it.key })
        assertEquals(listOf("fixtures", "bracket", "announcement"), tournamentShareContents(knockoutLive).map { it.key })
        assertEquals(listOf("player-of-tournament", "bracket", "announcement"), tournamentShareContents(knockout).map { it.key })
        // Six fixtures: matchday cards and versus tiles take four a page, the lists six.
        assertEquals(
            listOf("matchday-1", "matchday-2", "broadcast", "minimal", "versus-1", "versus-2"),
            content(league, "fixtures").templates.map { it.key },
        )
        // Twelve lines (two headings, ten teams) make two pages of each table.
        assertEquals(listOf("table-1", "table-2", "minimal-table-1", "minimal-table-2"), content(league, "table").templates.map { it.key })
        // The road to the final only once there's a final.
        assertEquals(listOf("bracket"), content(knockoutLive, "bracket").templates.map { it.key })
        assertEquals(listOf("bracket", "road"), content(knockout, "bracket").templates.map { it.key })
    }

    @Test fun fixtures() = shootTemplates("fixtures", content(league, "fixtures"))
    @Test fun fixturesPost() = shootTemplates("fixtures", content(league, "fixtures"), ShareFormat.POST)
    @Test fun table() = shootTemplates("table", content(league, "table"))
    @Test fun tablePost() = shootTemplates("table", content(league, "table"), ShareFormat.POST)
    @Test fun bracketLive() = shootTemplates("bracket_live", content(knockoutLive, "bracket"))
    @Test fun bracket() = shootTemplates("bracket", content(knockout, "bracket"))
    @Test fun bracketPost() = shootTemplates("bracket", content(knockout, "bracket"), ShareFormat.POST)
    @Test fun playerOfTournament() = shootTemplates("player_of_tournament", content(knockout, "player-of-tournament"))
    @Test fun playerOfTournamentPost() = shootTemplates("player_of_tournament", content(knockout, "player-of-tournament"), ShareFormat.POST)
    @Test fun announcement() = shootTemplates("announcement", content(announced, "announcement"))
    @Test fun announcementPost() = shootTemplates("announcement", content(announced, "announcement"), ShareFormat.POST)

    @Test
    fun studio() = shoot("posters_studio") {
        Box(Modifier.background(DesiTheme.colors.surface).padding(20.dp)) {
            ShareStudioContent(tournamentShareContents(league), onShare = { _, _, _ -> }, onSave = { _, _, _ -> true })
        }
    }
}
