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
import com.harsh.desicrics.model.Badge
import com.harsh.desicrics.model.BattingStats
import com.harsh.desicrics.model.BowlingStats
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.FieldZone
import com.harsh.desicrics.model.FormEntry
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.scoring.withScore
import com.harsh.desicrics.ui.share.ShareContent
import com.harsh.desicrics.ui.share.ShareFormat
import com.harsh.desicrics.ui.share.ShareLook
import com.harsh.desicrics.ui.share.ShareStudioContent
import com.harsh.desicrics.ui.share.SeasonShare
import com.harsh.desicrics.ui.share.TemplatePreview
import com.harsh.desicrics.ui.share.battingShareKey
import com.harsh.desicrics.ui.share.bowlingShareKey
import com.harsh.desicrics.ui.share.matchShareContents
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
 * Every share-studio template drawn at its design size (360 dp at xxhdpi, so 1080 px wide like the file
 * that's shared) and saved to app/shared/screenshots/share/. Overlays are drawn on the checkerboard so
 * their transparency shows. Re-run to refresh them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ShareStudioScreenshotTest {

    @Before
    fun setUp() {
        System.setProperty("roborazzi.test.record", "true")
        startKoin { modules(module { single { ApiConfig("http://localhost") } }) }
    }

    @After
    fun tearDown() = stopKoin()

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage("screenshots/share/$name.png") { DesiTheme(darkTheme = dark, reduceMotion = true) { content() } }

    private val strikers = Team("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir", "Ishaan", "Dev").mapIndexed { i, n -> Player("a$i", n, "pa$i") }, teamId = "t1")
    private val royals = Team("Powai XI", listOf("Esha", "Farhan", "Gaurav", "Hari").mapIndexed { i, n -> Player("b$i", n, "pb$i") }, teamId = "t2")

    /** A finished two-over match with a wagon wheel: Strikers 27/1, Royals 15/2, Strikers win. */
    private val finished: MatchState = run {
        var seq = 0
        fun ball(innings: Int, s: String, n: String, b: String, runs: Int = 0, zone: FieldZone? = null, wicket: Wicket? = null) =
            Delivery(seq++, innings, s, n, b, runs, wicket = wicket, zone = zone)
        val deliveries = listOf(
            ball(1, "a0", "a1", "b0", 4, FieldZone.COVER), ball(1, "a0", "a1", "b0", 1, FieldZone.LONG_ON), ball(1, "a1", "a0", "b0"),
            ball(1, "a1", "a0", "b0", 6, FieldZone.MID_WICKET), ball(1, "a1", "a0", "b0", wicket = Wicket(DismissalKind.CAUGHT, "a1", "b2")),
            ball(1, "a2", "a0", "b0", 1, FieldZone.POINT),
            ball(1, "a2", "a0", "b1", 1, FieldZone.SQUARE_LEG), ball(1, "a0", "a2", "b1", 4, FieldZone.COVER), ball(1, "a0", "a2", "b1", 6, FieldZone.LONG_OFF),
            ball(1, "a0", "a2", "b1", 2, FieldZone.MID_WICKET), ball(1, "a0", "a2", "b1"), ball(1, "a0", "a2", "b1", 2, FieldZone.THIRD_MAN),
            ball(2, "b0", "b1", "a3", 1), ball(2, "b1", "b0", "a3", wicket = Wicket(DismissalKind.BOWLED, "b1")), ball(2, "b2", "b0", "a3", 4),
            ball(2, "b2", "b0", "a3"), ball(2, "b2", "b0", "a3", 2), ball(2, "b2", "b0", "a3", 1),
            ball(2, "b2", "b0", "a4", 1), ball(2, "b0", "b2", "a4", wicket = Wicket(DismissalKind.CAUGHT, "b0", "a0")), ball(2, "b3", "b2", "a4", 4),
            ball(2, "b3", "b2", "a4", 1), ball(2, "b2", "b3", "a4"), ball(2, "b2", "b3", "a4", 1),
        )
        val base = Match("m", "me", 0, strikers, royals, 2, TeamSide.A, TossDecision.BAT, rules = MatchRules.Standard.copy(wagonWheel = true), scorerUid = "me")
        ScoringEngine.compute(base.withScore(ScoringEngine.compute(base, deliveries)), deliveries)
    }

    private val contents = matchShareContents(finished)
    private fun content(key: String) = contents.first { it.key == key }

    private val season = SeasonShare(
        "Aarav Shah",
        CareerStats(
            "p", matches = 12, won = 7,
            batting = BattingStats(12, 2, 412, 330, 40, 12, 88, true, 3),
            bowling = BowlingStats(9, 150, 190, 11, 1, 3, 14),
            form = (1..10).map { FormEntry("m$it", it.toLong(), it * 9 % 70, it % 3 == 0, 1, true) },
            badges = listOf(Badge.FIRST_FIFTY, Badge.PLAYER_OF_THE_MATCH),
        ),
        "2026 season",
    )

    /** Each of [content]'s templates in [format], at the shared file's width. */
    private fun shootTemplates(name: String, content: ShareContent, format: ShareFormat = ShareFormat.STORY) =
        content.templates.forEach { template ->
            shoot("${name}_${template.key}_${format.name.lowercase()}") {
                Box(Modifier.width(ShareFormat.DESIGN_WIDTH)) { TemplatePreview(template, format, ShareLook.Default) }
            }
        }

    @Test fun result() = shootTemplates("result", content("result"))
    @Test fun resultPost() = shootTemplates("result", content("result"), ShareFormat.POST)
    @Test fun batting() = shootTemplates("batting", content(battingShareKey(1, "a0")))
    @Test fun battingPost() = shootTemplates("batting", content(battingShareKey(1, "a0")), ShareFormat.POST)
    @Test fun bowling() = shootTemplates("bowling", content(bowlingShareKey(2, "a3")))
    @Test fun bowlingPost() = shootTemplates("bowling", content(bowlingShareKey(2, "a3")), ShareFormat.POST)
    @Test fun season() = shootTemplates("season", season)
    @Test fun seasonPost() = shootTemplates("season", season, ShareFormat.POST)

    @Test
    fun overlayOnPhoto() = shoot("batting_overlay_on_photo") {
        Box(Modifier.width(ShareFormat.DESIGN_WIDTH)) { TemplatePreview(content(battingShareKey(1, "a0")).templates.first(), ShareFormat.STORY, onPhoto = true) }
    }

    @Test
    fun studio() = shoot("studio") {
        Box(Modifier.background(DesiTheme.colors.surface).padding(20.dp)) {
            ShareStudioContent(contents, initial = battingShareKey(1, "a0"), onShare = { _, _, _ -> }, onSave = { _, _, _ -> true })
        }
    }

    @Test
    fun studioDark() = shoot("studio_dark", dark = true) {
        Box(Modifier.background(DesiTheme.colors.surface).padding(20.dp)) {
            ShareStudioContent(contents, initial = "result", onShare = { _, _, _ -> }, onSave = { _, _, _ -> true })
        }
    }
}
