package com.harsh.desicrics.ui.match

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.OverStrip
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.OverSummary
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.ui.text.commentaryText
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Every over of an innings, newest first, each with its balls and a line of commentary per ball. */
@Composable
internal fun InningsOvers(innings: InningsState, showTitle: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        if (showTitle) SectionLabel(stringResource(Res.string.scorecard_innings, innings.battingTeam.name))
        if (innings.overs.isEmpty()) {
            Text(stringResource(Res.string.overs_empty), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        }
        innings.overs.asReversed().forEach { key(innings.number, it.number) { OverCard(it, it.number == innings.overs.last().number) } }
    }
}

@Composable
private fun OverCard(over: OverSummary, latest: Boolean) {
    val colors = DesiTheme.colors
    var expanded by rememberSaveable { mutableStateOf(latest) }
    DesiCard(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.overs_over, over.number).uppercase(), style = DesiTheme.type.label, color = colors.muted)
                Text(over.bowler.name, style = DesiTheme.type.bodyStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(over.scoreAfter, style = DesiTheme.type.scoreMd, color = colors.ink)
                val runs = pluralStringResource(Res.plurals.overs_runs, over.runs, over.runs)
                Text(
                    if (over.wickets == 0) runs
                    else stringResource(Res.string.overs_summary, runs, pluralStringResource(Res.plurals.overs_wickets, over.wickets, over.wickets)),
                    style = DesiTheme.type.caption,
                    color = colors.muted,
                )
            }
        }
        OverStrip(over.balls.map { ballFor(it.delivery) })
        Text(stringResource(if (expanded) Res.string.hide_deliveries else Res.string.show_deliveries), style = DesiTheme.type.caption)
        if (!expanded) return@DesiCard
        HorizontalDivider(color = colors.line)
        // Newest ball first, like the over list.
        over.balls.asReversed().forEach { ball ->
            Row(verticalAlignment = Alignment.Top) {
                Text(ball.ballNumber, style = DesiTheme.type.data, color = colors.muted, modifier = Modifier.width(44.dp).padding(top = 1.dp))
                Text(
                    commentaryText(ball),
                    style = DesiTheme.type.body,
                    color = if (ball.delivery.wicket != null) colors.event.wicket else colors.ink,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// Previews: sample teams and balls only; all labels come from string resources.

private fun previewInnings(): InningsState {
    val match = Match(
        id = "preview", createdBy = "me", createdAt = 0,
        teamA = Team("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir").mapIndexed { i, n -> Player("a$i", n) }),
        teamB = Team("Powai XI", listOf("Dev", "Esha").mapIndexed { i, n -> Player("b$i", n) }),
        oversPerInnings = 5, tossWinner = TeamSide.A, tossDecision = TossDecision.BAT,
    )
    var seq = 0
    fun ball(s: String, n: String, b: String, runs: Int = 0, extra: ExtraType? = null, extraRuns: Int = 0, wicket: Wicket? = null) =
        Delivery(seq++, 1, s, n, b, runs, extra, extraRuns, wicket)
    val balls = listOf(
        ball("a0", "a1", "b0", runs = 4), ball("a0", "a1", "b0", runs = 1), ball("a1", "a0", "b0", extra = ExtraType.WIDE, extraRuns = 1),
        ball("a1", "a0", "b0"), ball("a1", "a0", "b0", runs = 6), ball("a1", "a0", "b0", extra = ExtraType.LEG_BYE, extraRuns = 1),
        ball("a0", "a1", "b0", runs = 2), ball("a1", "a0", "b1", wicket = Wicket(DismissalKind.CAUGHT, "a1", "b0")),
    )
    return ScoringEngine.compute(match, balls).current
}

@Preview
@Composable
private fun OversPreview() = DesiTheme(darkTheme = false) {
    Column(Modifier.background(DesiTheme.colors.bg).padding(DesiTheme.space.s4)) { InningsOvers(previewInnings(), showTitle = true) }
}

@Preview
@Composable
private fun OversDarkPreview() = DesiTheme(darkTheme = true) {
    Column(Modifier.background(DesiTheme.colors.bg).padding(DesiTheme.space.s4)) { InningsOvers(previewInnings(), showTitle = false) }
}
