package com.harsh.desicrics.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.OptionCard
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.heroBackground
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.AbandonReason
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEnding
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.text.resultText
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * How a match stopped early. During play: no result (pick why) or a team concedes (pick which). Before the
 * start ([beforeStart]): a walkover (pick who turned up) or called off (pick why). Nothing is recorded until
 * the scorer confirms, and Undo reopens the match afterwards.
 */
@Composable
internal fun EndMatchSheet(
    match: Match,
    beforeStart: Boolean,
    onEnd: (ending: MatchEnding, winner: TeamSide?, reason: AbandonReason?) -> Unit,
    onDismiss: () -> Unit,
) {
    val space = DesiTheme.space
    val winning = if (beforeStart) MatchEnding.WALKOVER else MatchEnding.CONCEDED
    var ending by rememberSaveable { mutableStateOf<MatchEnding?>(null) }
    var reason by rememberSaveable { mutableStateOf<AbandonReason?>(null) }
    // For a concession the scorer picks who gave up; for a walkover, who turned up. Either way it's stored as the winner.
    var picked by rememberSaveable { mutableStateOf<TeamSide?>(null) }
    DesiSheet(
        stringResource(if (beforeStart) Res.string.end_title_before else Res.string.end_title),
        onDismiss,
        stringResource(if (beforeStart) Res.string.end_hint_before else Res.string.end_hint),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
            OptionCard(
                title = stringResource(if (beforeStart) Res.string.end_called_off else Res.string.end_no_result),
                description = stringResource(if (beforeStart) Res.string.end_called_off_hint else Res.string.end_no_result_hint),
                icon = if (beforeStart) DesiIcons.CalledOff else DesiIcons.Rain,
                selected = ending == MatchEnding.ABANDONED,
                onClick = { ending = MatchEnding.ABANDONED; picked = null },
                modifier = Modifier.weight(1f),
            )
            OptionCard(
                title = stringResource(if (beforeStart) Res.string.end_walkover else Res.string.end_conceded),
                description = stringResource(if (beforeStart) Res.string.end_walkover_hint else Res.string.end_conceded_hint),
                icon = if (beforeStart) DesiIcons.Trophy else DesiIcons.Flag,
                selected = ending == winning,
                onClick = { ending = winning; reason = null },
                modifier = Modifier.weight(1f),
            )
        }
        when (ending) {
            MatchEnding.ABANDONED -> {
                Text(stringResource(Res.string.end_why), style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
                ListCard {
                    AbandonReason.entries.forEachIndexed { index, option ->
                        ChoiceRow(stringResource(option.label), option == reason, divider = index > 0, leading = { IconTile(option.icon, size = 36.dp) }) { reason = option }
                    }
                }
            }
            null -> Unit
            else -> {
                Text(
                    stringResource(if (beforeStart) Res.string.end_who_turned_up else Res.string.end_who_conceded),
                    style = DesiTheme.type.bodyStrong,
                    color = DesiTheme.colors.ink,
                )
                ListCard {
                    TeamSide.entries.forEachIndexed { index, side ->
                        val team = match.team(side)
                        ChoiceRow(team.name, side == picked, divider = index > 0, leading = { Avatar(team.name, size = 36) }) { picked = side }
                    }
                }
            }
        }
        val ready = when (ending) {
            MatchEnding.ABANDONED -> reason != null
            null -> false
            else -> picked != null
        }
        PrimaryButton(
            stringResource(Res.string.end_confirm),
            onClick = {
                val chosen = ending ?: return@PrimaryButton
                // A concession is picked by the side that gave up, so the other side wins.
                val winner = picked?.let { if (chosen == MatchEnding.CONCEDED) it.other else it }
                onEnd(chosen, winner, reason)
            },
            enabled = ready,
            leadingIcon = DesiIcons.Flag,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(Res.string.end_undo_hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
    }
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, divider: Boolean, leading: @Composable () -> Unit, onClick: () -> Unit) {
    val colors = DesiTheme.colors
    ListRow(
        title = title,
        leading = leading,
        titleColor = if (selected) colors.brand else colors.ink,
        trailing = { if (selected) Icon(painterResource(DesiIcons.CheckCircle), null, tint = colors.brand, modifier = Modifier.size(DesiTheme.size.iconSmall)) },
        divider = divider,
        onClick = onClick,
    )
}

/**
 * A match that ended without a ball bowled: a walkover or called off. Shows both teams and how it ended,
 * with a way to put it back on the schedule for whoever recorded it.
 */
@Composable
internal fun EndedBeforePlay(match: Match, result: MatchResult, canUndo: Boolean, onUndo: () -> Unit, onBack: () -> Unit) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .heroBackground().padding(start = space.screen, end = space.screen, bottom = space.s6),
            verticalArrangement = Arrangement.spacedBy(space.s4),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = space.s1)) {
                Icon(painterResource(DesiIcons.Back), stringResource(Res.string.common_back), tint = colors.onBrandStrong)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamSide.entries.forEachIndexed { index, side ->
                    if (index == 1) Text(stringResource(Res.string.setup_vs).uppercase(), style = DesiTheme.type.scoreMd, color = colors.onBrandStrong.copy(alpha = 0.6f))
                    val team = match.team(side)
                    val won = result.winner == side
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(space.s2)) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Avatar(team.name, size = 64)
                            if (won) {
                                Box(Modifier.size(26.dp).clip(DesiTheme.radius.full).background(colors.event.extra), contentAlignment = Alignment.Center) {
                                    Icon(painterResource(DesiIcons.TrophyFilled), null, tint = colors.brandStrong, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Text(
                            team.name,
                            style = DesiTheme.type.bodyStrong,
                            color = if (result.winner != null && !won) colors.onBrandStrong.copy(alpha = 0.6f) else colors.onBrandStrong,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(space.screen).padding(top = space.s2), verticalArrangement = Arrangement.spacedBy(space.s4)) {
            Row(
                Modifier.fillMaxWidth().clip(DesiTheme.radius.md)
                    .background(if (result.winner != null) colors.brandSoft else colors.sunk).padding(space.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.s3),
            ) {
                val tint = if (result.winner != null) colors.brand else colors.ink
                Icon(painterResource(resultIcon(result)), null, tint = tint, modifier = Modifier.size(DesiTheme.size.icon))
                Text(resultText(match, result), style = DesiTheme.type.bodyLg.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = tint)
            }
            Text(
                stringResource(if (result.ending == MatchEnding.WALKOVER) Res.string.end_walkover_note else Res.string.end_called_off_note),
                style = DesiTheme.type.body,
                color = colors.muted,
            )
            if (canUndo) GhostButton(stringResource(Res.string.end_reopen_fixture), onUndo, Modifier.fillMaxWidth(), leadingIcon = DesiIcons.History)
        }
    }
}

/** The mark for how a match ended: a trophy for a winner, the weather or the ground for no result. */
internal fun resultIcon(result: MatchResult): DrawableResource = when {
    result.winner != null -> DesiIcons.TrophyFilled
    result.isNoResult -> result.reason?.icon ?: DesiIcons.CalledOff
    else -> DesiIcons.Swap
}

internal val AbandonReason.label: StringResource
    get() = when (this) {
        AbandonReason.RAIN -> Res.string.end_reason_rain
        AbandonReason.BAD_LIGHT -> Res.string.end_reason_light
        AbandonReason.GROUND -> Res.string.end_reason_ground
        AbandonReason.OTHER -> Res.string.end_reason_other
    }

internal val AbandonReason.icon: DrawableResource
    get() = when (this) {
        AbandonReason.RAIN -> DesiIcons.Rain
        AbandonReason.BAD_LIGHT -> DesiIcons.BadLight
        AbandonReason.GROUND -> DesiIcons.Stadium
        AbandonReason.OTHER -> DesiIcons.CalledOff
    }

// Previews: sample team names only; all labels come from string resources.

private val PreviewWalkover = Match(
    id = "w", createdBy = "me", createdAt = 0,
    teamA = Team("Juhu Kings", emptyList()), teamB = Team("Powai XI", emptyList()),
    oversPerInnings = 8, tossWinner = TeamSide.A, tossDecision = TossDecision.BAT, status = MatchStatus.COMPLETED,
    result = MatchResult(TeamSide.B, ending = MatchEnding.WALKOVER),
)

@Preview
@Composable
private fun WalkoverPreview() = DesiTheme(darkTheme = false) {
    Column(Modifier.background(DesiTheme.colors.bg)) { EndedBeforePlay(PreviewWalkover, PreviewWalkover.result!!, canUndo = true, onUndo = {}, onBack = {}) }
}
