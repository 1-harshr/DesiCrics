package com.harsh.desicrics.ui.match
import com.harsh.desicrics.designsystem.icon.DesiIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.WagonWheel
import com.harsh.desicrics.model.FieldZone
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.stats.MvpPoints
import com.harsh.desicrics.ui.stats.mvpText
import com.harsh.desicrics.scoring.Retirement
import com.harsh.desicrics.ui.text.dismissalText
import com.harsh.desicrics.ui.text.label
import com.harsh.desicrics.util.format2
import org.jetbrains.compose.resources.stringResource

/** One innings: batting with how each batter got out, extras, who's yet to bat, then bowling figures. */
@Composable
internal fun InningsScorecard(innings: InningsState, modifier: Modifier = Modifier) {
    val colors = DesiTheme.colors
    DesiCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.scorecard_innings, innings.battingTeam.name),
                style = DesiTheme.type.titleMd,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (innings.declared) {
                Text(stringResource(Res.string.scorecard_declared), style = DesiTheme.type.caption, color = colors.muted, modifier = Modifier.padding(end = DesiTheme.space.s2))
            }
            Text("${innings.scoreText} (${innings.oversText})", style = DesiTheme.type.scoreMd, color = colors.ink)
        }
        if (innings.batters.isEmpty()) {
            Text(stringResource(Res.string.scorecard_not_started), style = DesiTheme.type.body, color = colors.muted)
            return@DesiCard
        }

        StatRow(
            listOf(
                stringResource(Res.string.scorecard_batter), stringResource(Res.string.scorecard_runs), stringResource(Res.string.scorecard_balls),
                stringResource(Res.string.scorecard_fours), stringResource(Res.string.scorecard_sixes), stringResource(Res.string.scorecard_strike_rate),
            ),
            header = true,
        )
        innings.batters.forEach { card ->
            StatRow(
                listOf(card.player.name, "${card.runs}", "${card.balls}", "${card.fours}", "${card.sixes}", card.strikeRate.format2()),
                subtitle = when (card.retired) {
                    Retirement.HURT -> stringResource(Res.string.scorecard_retired_hurt)
                    Retirement.NOT_OUT -> stringResource(Res.string.scorecard_retired_not_out)
                    Retirement.OUT -> stringResource(Res.string.scorecard_retired_out)
                    null -> dismissalText(card.dismissal)
                },
            )
        }
        val extras = innings.extras
        Text(
            stringResource(Res.string.scorecard_extras, extras.total, extras.wides, extras.noBalls, extras.byes, extras.legByes, extras.penalties),
            style = DesiTheme.type.caption,
            color = colors.muted,
        )
        if (innings.yetToBat.isNotEmpty()) {
            Text(
                stringResource(Res.string.scorecard_yet_to_bat, innings.yetToBat.joinToString { it.name }),
                style = DesiTheme.type.caption,
                color = colors.muted,
            )
        }

        HorizontalDivider(color = colors.line)
        StatRow(
            listOf(
                stringResource(Res.string.scorecard_bowler), stringResource(Res.string.scorecard_overs), stringResource(Res.string.scorecard_maidens),
                stringResource(Res.string.scorecard_runs), stringResource(Res.string.scorecard_wickets), stringResource(Res.string.scorecard_economy),
            ),
            header = true,
        )
        innings.bowlers.forEach { card ->
            StatRow(listOf(card.player.name, card.oversText, "${card.maidens}", "${card.runs}", "${card.wickets}", card.economy.format2()))
        }
    }
}

/**
 * MVP points for everyone who did something, best first (the formula is [MvpPoints]). Once the match is
 * over, the top player is player of the match.
 */
@Composable
internal fun MatchMvp(state: MatchState) {
    val points = MvpPoints.forMatch(state)
    if (points.isEmpty()) return
    val players = (state.match.teamA.players + state.match.teamB.players).associateBy { it.id }
    val ranked = points.entries.sortedByDescending { it.value }.take(5).mapNotNull { (id, tenths) -> players[id]?.let { it to tenths } }
    DesiCard(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(Res.string.scorecard_mvp), icon = DesiIcons.Star)
        if (state.isComplete) {
            ranked.firstOrNull()?.let { (best, _) ->
                Text(stringResource(Res.string.scorecard_player_of_the_match, best.name), style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.brand)
            }
        }
        ranked.forEach { (player, tenths) ->
            Row {
                Text(player.name, style = DesiTheme.type.body, color = DesiTheme.colors.ink, modifier = Modifier.weight(1f))
                Text(mvpText(tenths), style = DesiTheme.type.data, color = DesiTheme.colors.ink)
            }
        }
    }
}

/** Where the runs off the bat went, for everyone or one batter. Only shown when the match kept a wagon wheel. */
@Composable
internal fun InningsWagonWheel(innings: InningsState) {
    if (innings.zoneRuns().isEmpty()) return
    var batterId by remember(innings.number) { mutableStateOf<String?>(null) }
    val batters = innings.batters.filter { innings.zoneRuns(it.player.id).isNotEmpty() }.map { it.player }
    val runs = innings.zoneRuns(batterId)
    DesiCard(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(Res.string.scorecard_wagon_wheel), icon = DesiIcons.Stadium)
        ChoiceChips(
            options = listOf<Player?>(null) + batters,
            selected = batters.firstOrNull { it.id == batterId },
            label = { it?.name ?: stringResource(Res.string.scorecard_wagon_wheel_all) },
            onSelect = { batterId = it?.id },
        )
        WagonWheel(
            labels = FieldZone.Clockwise.map { stringResource(it.label) },
            values = FieldZone.Clockwise.map { runs[it] ?: 0 },
            description = stringResource(Res.string.scorecard_wagon_wheel_description),
        )
    }
}

/** A name column then five right-aligned numbers, the last one wider for rates like "137.50". */
@Composable
private fun StatRow(cells: List<String>, header: Boolean = false, subtitle: String? = null) {
    val colors = DesiTheme.colors
    val numberStyle = if (header) DesiTheme.type.label else DesiTheme.type.data
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(2.6f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (header) cells.first().uppercase() else cells.first(),
                style = if (header) DesiTheme.type.label else DesiTheme.type.bodyStrong,
                color = if (header) colors.muted else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, style = DesiTheme.type.caption, color = colors.muted)
        }
        cells.drop(1).forEachIndexed { index, cell ->
            Text(
                cell,
                style = numberStyle,
                color = if (header) colors.muted else colors.ink,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(if (index == cells.size - 2) 1.4f else 0.9f),
            )
        }
    }
}
