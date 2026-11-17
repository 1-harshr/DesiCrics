package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.theme.DesiTheme

// Preview-only sample data; real screens pass text from string resources.

private val SampleOver = listOf(
    Ball("•", BallKind.Dot),
    Ball("1", BallKind.Runs),
    Ball("4", BallKind.Four),
    Ball("Wd", BallKind.Extra),
    Ball("6", BallKind.Six),
    Ball("W", BallKind.Wicket),
)

private val SamplePad = listOf(
    PadKey("0", "0"), PadKey("1", "1"), PadKey("2", "2"), PadKey("3", "3"),
    PadKey("4", "4", PadKeyStyle.Four), PadKey("6", "6", PadKeyStyle.Six),
    PadKey("extra", "Extra", PadKeyStyle.Extra), PadKey("out", "OUT", PadKeyStyle.Wicket),
    PadKey("undo", "Undo", PadKeyStyle.Utility), PadKey("5", "5/7", PadKeyStyle.Utility),
    PadKey("swap", "Swap", PadKeyStyle.Utility), PadKey("more", "More", PadKeyStyle.Utility),
)

@Composable
private fun Catalogue() {
    Column(
        Modifier.background(DesiTheme.colors.bg).padding(DesiTheme.space.s4).width(360.dp),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s6),
    ) {
        ScoreHeader("Andheri Strikers · 2nd inns", "142/6", "16.4 ov", detail = "Need 27 off 20 · RRR 8.10")
        OverStrip(SampleOver)
        MatchCard(
            teams = listOf(TeamLine("Andheri Strikers", "142/6", "16.4", batting = true), TeamLine("Powai XI", "168/7", "20")),
            status = { LiveBadge() },
            meta = "T20 · Shivaji Park",
            footer = "Strikers need 27 off 20 balls",
        )
        RunPad(SamplePad, onKey = {})
        PlayerRow("Rohan Iyer", subtitle = "Top-order batter · Right-hand bat")
        Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            PrimaryButton("Start match", onClick = {})
            GhostButton("Add player", onClick = {})
        }
        SyncIndicator(pendingCount = 3, label = "3 balls waiting to sync")
    }
}

@Preview
@Composable
private fun CatalogueLight() = DesiTheme(darkTheme = false) { Catalogue() }

@Preview
@Composable
private fun CatalogueDark() = DesiTheme(darkTheme = true) { Catalogue() }
