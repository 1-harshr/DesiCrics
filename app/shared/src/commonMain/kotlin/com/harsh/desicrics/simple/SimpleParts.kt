package com.harsh.desicrics.simple

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.harsh.desicrics.designsystem.component.CardResult
import com.harsh.desicrics.designsystem.component.LiveBadge
import com.harsh.desicrics.designsystem.component.MatchCard
import com.harsh.desicrics.designsystem.component.TeamLine
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.home_status_not_started
import com.harsh.desicrics.scoring.formatOvers
import com.harsh.desicrics.ui.text.dayLabel
import com.harsh.desicrics.ui.text.matchStatusText
import com.harsh.desicrics.ui.text.oversText
import com.harsh.desicrics.ui.text.resultText
import org.jetbrains.compose.resources.stringResource

/** A match in Simple mode's lists: both scores in batting order, and how it stands or ended. */
@Composable
internal fun SimpleMatchCard(match: Match, onClick: () -> Unit) {
    val current = match.innings.lastOrNull()?.battingSide
    val teams = listOf(match.battingFirst, match.battingFirst.other).map { side ->
        val innings = match.innings.firstOrNull { it.battingSide == side }
        TeamLine(
            name = match.team(side).name,
            score = innings?.let { "${it.runs}/${it.wickets}" },
            overs = innings?.let { formatOvers(it.legalBalls) },
            batting = match.status == MatchStatus.IN_PROGRESS && side == current,
            won = match.result?.winner?.let { it == side },
        )
    }
    MatchCard(
        teams = teams,
        status = {
            when {
                match.status == MatchStatus.COMPLETED -> StatusLabel(dayLabel(match.createdAt))
                match.innings.isEmpty() -> StatusLabel(stringResource(Res.string.home_status_not_started))
                else -> LiveBadge()
            }
        },
        meta = oversText(match.oversPerInnings),
        footer = matchStatusText(match),
        result = match.result?.let { CardResult(resultText(match, it), neutral = it.winner == null) },
        onClick = onClick,
    )
}

@Composable
private fun StatusLabel(text: String) {
    Text(text.uppercase(), style = DesiTheme.type.label, color = DesiTheme.colors.muted)
}
