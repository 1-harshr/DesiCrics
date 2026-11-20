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
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.scoring.withScore
import com.harsh.desicrics.ui.share.PlayerOfTheMatchShare
import com.harsh.desicrics.ui.share.ResultShare
import com.harsh.desicrics.ui.share.ScorecardShare
import com.harsh.desicrics.ui.share.ShareFormat
import com.harsh.desicrics.ui.share.ShareLook
import com.harsh.desicrics.ui.share.ShareStudioContent
import com.harsh.desicrics.ui.share.ShareTemplate
import com.harsh.desicrics.ui.share.SquadFigures
import com.harsh.desicrics.ui.share.SquadShare
import com.harsh.desicrics.ui.share.TemplatePreview
import com.harsh.desicrics.ui.share.matchShareContents
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

/**
 * The match posters (result, scorecard, player of the match) and the squad posters, every template and
 * every page, at the shared file's width, saved to app/shared/screenshots/share/. Re-run to refresh them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class MatchPosterScreenshotTest {

    @Before
    fun setUp() {
        System.setProperty("roborazzi.test.record", "true")
        startKoin { modules(module { single { ApiConfig("http://localhost") } }) }
    }

    @After
    fun tearDown() = stopKoin()

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage("screenshots/share/$name.png") { DesiTheme(darkTheme = dark, reduceMotion = true) { content() } }

    private val strikers = Team(
        "Andheri Strikers",
        listOf("Rohan Iyer", "Aarav Shah", "Kabir Mehta", "Ishaan Rao", "Dev Patil", "Neel Joshi", "Om Kulkarni", "Parth Desai", "Rahul Nair", "Sahil Khan", "Tanay Gupta")
            .mapIndexed { i, n -> Player("a$i", n, "pa$i") },
        teamId = "t1",
    )
    private val powai = Team(
        "Powai XI",
        listOf("Esha Verma", "Farhan Ali", "Gaurav Singh", "Hari Menon", "Imran Sheikh", "Jay Thakur", "Karan Bose", "Lakshya Jain", "Manav Reddy", "Nikhil Das", "Pranav Pillai")
            .mapIndexed { i, n -> Player("b$i", n, "pb$i") },
        teamId = "t2",
    )

    /**
     * Six overs a side between two elevens, written as one token a ball ("4", "0", "W"). Strike turns over
     * on odd runs and at the end of each over; five bowlers take an over each in turn; wickets go caught,
     * bowled, lbw in rotation.
     */
    private val finished: MatchState = run {
        val first = "1 4 0 6 W 2  0 1 1 4 0 W  6 1 0 2 4 1  W 0 1 6 4 1  2 2 W 1 0 4  6 6 1 W 1 2"
        val second = "0 1 W 4 0 1  1 0 6 W 0 0  4 1 1 W 0 2  0 W 1 4 1 0  W 1 0 2 6 W  1 0 W 4 1 0"
        var seq = 0
        fun innings(number: Int, balls: String, batting: Team, bowling: Team): List<Delivery> {
            val tokens = balls.split(Regex("\\s+")).filter { it.isNotEmpty() }
            var striker = 0
            var nonStriker = 1
            var next = 2
            var wickets = 0
            return tokens.mapIndexed { i, token ->
                val bowler = bowling.players[10 - (i / 6) % 5]
                val ball = if (token == "W") {
                    val kind = listOf(DismissalKind.CAUGHT, DismissalKind.BOWLED, DismissalKind.CAUGHT, DismissalKind.LBW)[wickets % 4]
                    val fielder = if (kind == DismissalKind.CAUGHT) bowling.players[wickets % 7].id else null
                    Delivery(seq++, number, batting.players[striker].id, batting.players[nonStriker].id, bowler.id, wicket = Wicket(kind, batting.players[striker].id, fielder))
                        .also { wickets++; striker = next++ }
                } else {
                    val runs = token.toInt()
                    Delivery(seq++, number, batting.players[striker].id, batting.players[nonStriker].id, bowler.id, runs)
                        .also { if (runs % 2 == 1) striker = nonStriker.also { nonStriker = striker } }
                }
                if (i % 6 == 5) striker = nonStriker.also { nonStriker = striker }
                ball
            }
        }
        val deliveries = innings(1, first, strikers, powai) + innings(2, second, powai, strikers)
        val base = Match("m", "me", 0, strikers, powai, 6, TeamSide.A, TossDecision.BAT, rules = MatchRules.Standard, scorerUid = "me")
        ScoringEngine.compute(base.withScore(ScoringEngine.compute(base, deliveries)), deliveries)
    }

    private val contents = matchShareContents(finished)
    private fun content(key: String) = contents.first { it.key == key }

    private val squad = SquadShare(
        SquadFigures(
            "Andheri Strikers",
            logoUrl = null,
            city = "Mumbai",
            players = (strikers.players + Player("a11", "Vihaan Kapoor") + Player("a12", "Yash Malhotra") + Player("a13", "Zubin Irani")).mapIndexed { i, p ->
                PlayerSummary(p.id, p.name, role = PlayerRole.entries[i % PlayerRole.entries.size].takeIf { i % 5 != 4 })
            },
        ),
    )

    /** Every page of each of [templates] in [format]. */
    private fun shootTemplates(name: String, templates: List<ShareTemplate>, format: ShareFormat = ShareFormat.STORY) =
        templates.filter { format in it.formats }.forEach { template ->
            val pages = template.pages(format)
            repeat(pages) { page ->
                val suffix = if (pages > 1) "_p${page + 1}" else ""
                shoot("${name}_${template.key}_${format.name.lowercase()}$suffix") {
                    Box(Modifier.width(ShareFormat.DESIGN_WIDTH)) { TemplatePreview(template, format, ShareLook.Default, page = page) }
                }
            }
        }

    @Test
    fun matchContents() {
        assertEquals(listOf(ResultShare.KEY, ScorecardShare.KEY, PlayerOfTheMatchShare.KEY), contents.take(3).map { it.key })
        assertEquals(listOf("bigscore", "broadcast", "minimal", "overlay"), content(ResultShare.KEY).templates.map { it.key })
    }

    @Test fun result() = shootTemplates("poster_result", content(ResultShare.KEY).templates)
    @Test fun resultPost() = shootTemplates("poster_result", content(ResultShare.KEY).templates, ShareFormat.POST)
    @Test fun scorecard() = shootTemplates("poster_scorecard", content(ScorecardShare.KEY).templates)
    @Test fun scorecardPost() = shootTemplates("poster_scorecard", content(ScorecardShare.KEY).templates, ShareFormat.POST)
    @Test fun playerOfTheMatch() = shootTemplates("poster_potm", content(PlayerOfTheMatchShare.KEY).templates)
    @Test fun playerOfTheMatchPost() = shootTemplates("poster_potm", content(PlayerOfTheMatchShare.KEY).templates, ShareFormat.POST)
    @Test fun squad() = shootTemplates("poster_squad", squad.templates)
    @Test fun squadPost() = shootTemplates("poster_squad", squad.templates, ShareFormat.POST)

    @Test
    fun studioPages() = shoot("poster_studio_pages") {
        Box(Modifier.background(DesiTheme.colors.surface).padding(20.dp)) {
            ShareStudioContent(listOf(squad), onShare = { _, _, _ -> }, onSave = { _, _, _ -> true })
        }
    }
}
