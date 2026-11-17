package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.icon.DesiIllustrations
import com.harsh.desicrics.designsystem.theme.DesiTheme

// Light and dark previews for every component, one group per file, each showing its states.
// Preview-only sample text; real screens pass text from string resources.

@Composable
private fun PreviewSurface(darkTheme: Boolean, content: @Composable ColumnScope.() -> Unit) = DesiTheme(darkTheme = darkTheme) {
    Column(
        Modifier.background(DesiTheme.colors.bg).padding(DesiTheme.space.s4).width(360.dp),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s4),
        content = content,
    )
}

// Buttons

@Composable
private fun ButtonStates() {
    PrimaryButton("Send code", onClick = {}, trailingIcon = DesiIcons.Forward, modifier = Modifier.fillMaxWidth())
    PrimaryButton("Send code", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
    PrimaryButton("Send code", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
    TonalButton("Continue with Google", onClick = {}, leadingIcon = DesiIllustrations.GoogleLogo, tintLeadingIcon = false, modifier = Modifier.fillMaxWidth())
    TonalButton("Sync now", onClick = {}, leadingIcon = DesiIcons.Sync, modifier = Modifier.fillMaxWidth())
    TonalButton("Continue with Google", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        GhostButton("Add player", onClick = {})
        GhostButton("Disabled", onClick = {}, enabled = false)
    }
}

@Preview
@Composable
private fun ButtonsLight() = PreviewSurface(darkTheme = false) { ButtonStates() }

@Preview
@Composable
private fun ButtonsDark() = PreviewSurface(darkTheme = true) { ButtonStates() }

// Input fields

@Composable
private fun FieldStates() {
    PhoneNumberField("", {}, label = "Mobile number", countryCode = "+91", countryLabel = "IN", placeholder = "98765 43210")
    PhoneNumberField("9876543210", {}, label = "Mobile number", countryCode = "+91", countryLabel = "IN", placeholder = "98765 43210")
    PhoneNumberField(
        "98765", {}, label = "Mobile number", countryCode = "+91", countryLabel = "IN", placeholder = "98765 43210",
        error = "Enter a 10-digit mobile number",
    )
    OtpField("", {})
    OtpField("4821", {})
    OtpField("482193", {}, isError = true)
    DesiTextField("Rohan Iyer", {}, label = "Name")
    DesiTextField("", {}, label = "City", placeholder = "Mumbai", supportingText = "Shown on your profile")
    DesiTextField("", {}, label = "Name", error = "Please enter your name")
    ChoiceChips(listOf("Batter", "Bowler", "All-rounder", "Wicket-keeper"), selected = "All-rounder", label = { it }, onSelect = {})
}

@Preview
@Composable
private fun FieldsLight() = PreviewSurface(darkTheme = false) { FieldStates() }

@Preview
@Composable
private fun FieldsDark() = PreviewSurface(darkTheme = true) { FieldStates() }

// Content

@Composable
private fun ContentStates() {
    MatchCard(
        teams = listOf(TeamLine("Andheri Strikers", "142/6", "16.4", batting = true), TeamLine("Powai XI", "168/7", "20")),
        status = { LiveBadge() },
        meta = "T20 · Shivaji Park",
        footer = "Strikers need 27 off 20 balls",
        onClick = {},
    )
    MatchCard(
        teams = listOf(TeamLine("Bandra Blues"), TeamLine("Juhu Gymkhana")),
        meta = "10 overs · yet to start",
    )
    DesiCard(Modifier.fillMaxWidth()) {
        SectionLabel("Squad")
        PlayerRow("Rohan Iyer", subtitle = "Top-order batter · Right-hand bat", onClick = {})
        PlayerRow("Aarav Shah", subtitle = "Left-arm orthodox", trailing = { LiveBadge() })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        Avatar("Rohan Iyer", size = 32)
        Avatar("Aarav Shah")
        Avatar("Zoya", size = 64)
    }
    SyncIndicator(pendingCount = 0, label = "All balls synced")
    SyncIndicator(pendingCount = 3, label = "3 balls waiting to sync")
}

@Preview
@Composable
private fun ContentLight() = PreviewSurface(darkTheme = false) { ContentStates() }

@Preview
@Composable
private fun ContentDark() = PreviewSurface(darkTheme = true) { ContentStates() }

// Scoring

private val PreviewOver = listOf(
    Ball("•", BallKind.Dot),
    Ball("1", BallKind.Runs),
    Ball("4", BallKind.Four),
    Ball("Wd", BallKind.Extra),
    Ball("6", BallKind.Six),
    Ball("W", BallKind.Wicket),
    Ball("2", BallKind.Runs),
)

private val PreviewPad = listOf(
    PadKey("0", "0"), PadKey("1", "1"), PadKey("2", "2"), PadKey("3", "3"),
    PadKey("4", "4", PadKeyStyle.Four), PadKey("6", "6", PadKeyStyle.Six),
    PadKey("extra", "Extra", PadKeyStyle.Extra), PadKey("out", "OUT", PadKeyStyle.Wicket),
    PadKey("undo", "Undo", PadKeyStyle.Utility), PadKey("5", "5/7", PadKeyStyle.Utility),
    PadKey("swap", "Swap", PadKeyStyle.Utility), PadKey("more", "More", PadKeyStyle.Utility),
)

@Composable
private fun ScoringStates() {
    ScoreHeader("Andheri Strikers · 2nd inns", "142/6", "16.4 ov", detail = "Need 27 off 20 · RRR 8.10")
    ScoreHeader("Powai XI · 1st inns", "0/0", "0.0 ov")
    Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        BallKind.entries.forEach { kind -> BallChip(PreviewOver.first { it.kind == kind }) }
    }
    OverStrip(PreviewOver)
    RunPad(PreviewPad, onKey = {})
    RunPad(PreviewPad.take(4), onKey = {}, enabled = false)
}

@Preview
@Composable
private fun ScoringLight() = PreviewSurface(darkTheme = false) { ScoringStates() }

@Preview
@Composable
private fun ScoringDark() = PreviewSurface(darkTheme = true) { ScoringStates() }

// Structure

@Composable
private fun ScaffoldSample() {
    Column(Modifier.height(320.dp)) {
        DesiScaffold(title = "New match", onBack = {}) { padding ->
            Column(Modifier.padding(padding).padding(DesiTheme.space.screen)) {
                Text("Screen content", style = DesiTheme.type.body, color = DesiTheme.colors.ink)
            }
        }
    }
    Column(Modifier.height(120.dp)) { FullScreenLoading() }
}

@Preview
@Composable
private fun StructureLight() = PreviewSurface(darkTheme = false) { ScaffoldSample() }

@Preview
@Composable
private fun StructureDark() = PreviewSurface(darkTheme = true) { ScaffoldSample() }
