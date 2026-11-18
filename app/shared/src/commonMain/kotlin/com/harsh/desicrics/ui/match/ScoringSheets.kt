package com.harsh.desicrics.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.NumberStepper
import com.harsh.desicrics.designsystem.component.PadKey
import com.harsh.desicrics.designsystem.component.PlayerRow
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.RunPad
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A list of players to tap, e.g. the next batter or the bowler, with an optional note under each
 * ([notes] by player ID). [onAddPlayer], when given, lets the scorer add a latecomer or substitute who
 * then appears in the list.
 */
@Composable
internal fun PlayerSheet(
    title: String,
    players: List<Player>,
    onPick: (Player) -> Unit,
    onDismiss: () -> Unit,
    notes: Map<String, String> = emptyMap(),
    onAddPlayer: ((name: String) -> Unit)? = null,
) {
    DesiSheet(title, onDismiss) {
        if (players.isEmpty()) {
            Text(stringResource(Res.string.scoring_no_players), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        }
        Column(Modifier.fillMaxWidth().clip(DesiTheme.radius.md)) {
            players.forEach { player ->
                PlayerRow(player.name, subtitle = notes[player.id], onClick = { onPick(player) })
            }
        }
        if (onAddPlayer != null) AddPlayerField(onAddPlayer)
    }
}

@Composable
private fun AddPlayerField(onAdd: (String) -> Unit) {
    val colors = DesiTheme.colors
    var name by remember { mutableStateOf("") }
    val add = {
        if (name.isNotBlank()) onAdd(name.trim())
        name = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            DesiTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(Res.string.scoring_add_new_player),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            Box(
                Modifier
                    .size(DesiTheme.size.touchTarget)
                    .clip(DesiTheme.radius.full)
                    .background(if (name.isBlank()) colors.sunk else colors.brand)
                    .clickable(enabled = name.isNotBlank(), role = Role.Button, onClick = add),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(DesiIcons.Add),
                    contentDescription = stringResource(Res.string.scoring_add_new_player),
                    tint = if (name.isBlank()) colors.muted else colors.onBrand,
                )
            }
        }
        Text(stringResource(Res.string.scoring_add_new_player_hint), style = DesiTheme.type.caption, color = colors.muted)
    }
}

/** One choice in an [OptionsSheet]. */
internal class SheetOption(val title: String, val hint: String, val onClick: () -> Unit)

/** A short list of things to do, each with a line on what it means, e.g. retire a batter. */
@Composable
internal fun OptionsSheet(title: String, options: List<SheetOption>, onDismiss: () -> Unit) {
    DesiSheet(title, onDismiss) {
        Column(Modifier.fillMaxWidth().clip(DesiTheme.radius.md)) {
            options.forEach { option ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = option.onClick)
                        .padding(vertical = DesiTheme.space.s3),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(option.title, style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
                    Text(option.hint, style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
                }
            }
        }
    }
}

/** Asks how many runs came off an extra, as a row of big keys. */
@Composable
internal fun ExtraRunsSheet(type: ExtraType, onPick: (runs: Int) -> Unit, onDismiss: () -> Unit) {
    val (title, hint, options) = when (type) {
        ExtraType.WIDE -> Triple(Res.string.scoring_wide_title, Res.string.scoring_wide_hint, listOf(0, 1, 2, 3, 4))
        ExtraType.NO_BALL -> Triple(Res.string.scoring_no_ball_title, Res.string.scoring_no_ball_hint, listOf(0, 1, 2, 3, 4, 6))
        ExtraType.BYE -> Triple(Res.string.scoring_bye_title, Res.string.scoring_byes_hint, listOf(1, 2, 3, 4))
        ExtraType.LEG_BYE -> Triple(Res.string.scoring_leg_bye_title, Res.string.scoring_byes_hint, listOf(1, 2, 3, 4))
    }
    DesiSheet(stringResource(title), onDismiss, stringResource(hint)) {
        RunPad(
            keys = options.map { PadKey("$it", "$it") },
            onKey = { onPick(it.id.toInt()) },
        )
    }
}

/**
 * How a batter got out: the dismissal, then (for a run out) who and how many runs were completed, then
 * the fielder if the dismissal has one. Only the striker can be out any way but run out.
 */
@Composable
internal fun WicketSheet(
    striker: Player,
    nonStriker: Player,
    fielders: List<Player>,
    onConfirm: (DismissalKind, playerOut: Player, fielder: Player?, runsCompleted: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(DismissalKind.BOWLED) }
    var out by remember { mutableStateOf(striker) }
    var runs by remember { mutableStateOf(0) }
    var fielder by remember { mutableStateOf<Player?>(null) }

    DesiSheet(stringResource(Res.string.scoring_wicket_title), onDismiss) {
        SectionLabel(stringResource(Res.string.scoring_wicket_how))
        ChoiceChips(
            options = DismissalKind.entries,
            selected = kind,
            label = { stringResource(it.label) },
            onSelect = {
                kind = it
                if (!it.hasFielder) fielder = null
            },
        )
        if (!kind.strikerOnly) {
            SectionLabel(stringResource(Res.string.scoring_wicket_who), Modifier.padding(top = DesiTheme.space.s2))
            SegmentedControl(listOf(striker, nonStriker), selected = out, label = { it.name }, onSelect = { out = it })
        }
        if (kind.allowsRuns) {
            SectionLabel(stringResource(Res.string.scoring_wicket_runs), Modifier.padding(top = DesiTheme.space.s2))
            ChoiceChips(listOf(0, 1, 2, 3), selected = runs, label = { "$it" }, onSelect = { runs = it })
        }
        if (kind.hasFielder) {
            val label = if (kind == DismissalKind.STUMPED) Res.string.scoring_wicket_keeper else Res.string.scoring_wicket_fielder
            SectionLabel(stringResource(label), Modifier.padding(top = DesiTheme.space.s2))
            // Tap the selected fielder again to clear it.
            ChoiceChips(fielders, selected = fielder, label = { it.name }, onSelect = { fielder = if (fielder == it) null else it })
        }
        PrimaryButton(
            text = stringResource(Res.string.scoring_wicket_confirm),
            onClick = {
                val playerOut = if (kind.strikerOnly) striker else out
                onConfirm(kind, playerOut, fielder?.takeIf { kind.hasFielder }, if (kind.allowsRuns) runs else 0)
            },
            modifier = Modifier.fillMaxWidth().padding(top = DesiTheme.space.s2),
        )
    }
}

internal val DismissalKind.label: StringResource
    get() = when (this) {
        DismissalKind.BOWLED -> Res.string.scoring_dismissal_bowled
        DismissalKind.CAUGHT -> Res.string.scoring_dismissal_caught
        DismissalKind.LBW -> Res.string.scoring_dismissal_lbw
        DismissalKind.RUN_OUT -> Res.string.scoring_dismissal_run_out
        DismissalKind.STUMPED -> Res.string.scoring_dismissal_stumped
        DismissalKind.HIT_WICKET -> Res.string.scoring_dismissal_hit_wicket
        DismissalKind.OBSTRUCTING_FIELD -> Res.string.scoring_dismissal_obstructing
        DismissalKind.HIT_BALL_TWICE -> Res.string.scoring_dismissal_hit_twice
        DismissalKind.OTHER -> Res.string.scoring_dismissal_other
    }

/** Penalty runs to either side: the team, then how many (5 by default, as in the laws). */
@Composable
internal fun PenaltySheet(
    battingTeam: String,
    fieldingTeam: String,
    onAward: (toBattingSide: Boolean, runs: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var toBatting by remember { mutableStateOf(true) }
    var runs by remember { mutableStateOf(5) }
    DesiSheet(stringResource(Res.string.match_penalty), onDismiss, stringResource(Res.string.match_penalty_hint)) {
        SectionLabel(stringResource(Res.string.match_penalty_to))
        SegmentedControl(listOf(true, false), selected = toBatting, label = { if (it) battingTeam else fieldingTeam }, onSelect = { toBatting = it })
        if (!toBatting) {
            Text(stringResource(Res.string.match_penalty_fielding_note), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        }
        NumberStepper(
            value = runs,
            onValueChange = { runs = it },
            label = stringResource(Res.string.match_penalty_runs),
            decreaseLabel = stringResource(Res.string.match_penalty_fewer),
            increaseLabel = stringResource(Res.string.match_penalty_more),
            range = 1..10,
        )
        PrimaryButton(
            text = stringResource(Res.string.match_penalty_award, runs),
            onClick = { onAward(toBatting, runs) },
            modifier = Modifier.fillMaxWidth().padding(top = DesiTheme.space.s2),
        )
    }
}
