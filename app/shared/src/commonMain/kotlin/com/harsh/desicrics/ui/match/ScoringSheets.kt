package com.harsh.desicrics.ui.match
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.ErrorBanner

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.NumberStepper
import com.harsh.desicrics.designsystem.component.PadKey
import com.harsh.desicrics.designsystem.component.PlayerRow
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.RunPad
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.component.WagonWheel
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.FieldZone
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.MAX_TARGET
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
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
            EmptyState(DesiIcons.Teams, stringResource(Res.string.scoring_no_players))
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

/** Asks how many runs came off an extra, as a row of big keys. [penalty] is what a wide or no-ball adds itself. */
@Composable
internal fun ExtraRunsSheet(type: ExtraType, penalty: Int, onPick: (runs: Int) -> Unit, onDismiss: () -> Unit) {
    val (title, hint, options) = when (type) {
        ExtraType.WIDE -> Triple(
            Res.string.scoring_wide_title,
            if (penalty == 0) stringResource(Res.string.scoring_wide_hint_free) else pluralStringResource(Res.plurals.scoring_wide_hint, penalty, penalty),
            listOf(0, 1, 2, 3, 4),
        )
        ExtraType.NO_BALL -> Triple(
            Res.string.scoring_no_ball_title,
            if (penalty == 0) stringResource(Res.string.scoring_no_ball_hint_free) else pluralStringResource(Res.plurals.scoring_no_ball_hint, penalty, penalty),
            listOf(0, 1, 2, 3, 4, 6),
        )
        ExtraType.BYE -> Triple(Res.string.scoring_bye_title, stringResource(Res.string.scoring_byes_hint), listOf(1, 2, 3, 4))
        ExtraType.LEG_BYE -> Triple(Res.string.scoring_leg_bye_title, stringResource(Res.string.scoring_byes_hint), listOf(1, 2, 3, 4))
    }
    // A wide or no-ball always carries its own run, so its keys read "Wd", "Wd+1"… rather than a bare 0.
    val short = when (type) {
        ExtraType.WIDE -> stringResource(Res.string.scoring_key_wide)
        ExtraType.NO_BALL -> stringResource(Res.string.scoring_key_no_ball)
        ExtraType.BYE, ExtraType.LEG_BYE -> null
    }
    val labels = options.map { runs ->
        when {
            short == null -> "$runs"
            runs == 0 -> short
            else -> stringResource(Res.string.scoring_extra_plus, short, runs)
        }
    }
    DesiSheet(stringResource(title), onDismiss, hint) {
        RunPad(
            keys = options.mapIndexed { i, runs -> PadKey("$runs", labels[i]) },
            onKey = { onPick(it.id.toInt()) },
        )
    }
}

/**
 * How a batter got out: the dismissal, then (for a run out) who and how many runs were completed, then
 * the fielder if the dismissal has one. Only the striker can be out any way but run out. [nonStriker] is
 * null when the last batter is batting alone.
 */
@Composable
internal fun WicketSheet(
    striker: Player,
    nonStriker: Player?,
    fielders: List<Player>,
    onConfirm: (DismissalKind, playerOut: Player, fielder: Player?, runsCompleted: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(DismissalKind.BOWLED) }
    var out by remember { mutableStateOf(striker) }
    var runs by remember { mutableStateOf(0) }
    var fielder by remember { mutableStateOf<Player?>(null) }

    DesiSheet(stringResource(Res.string.scoring_wicket_title), onDismiss) {
        SectionHeader(stringResource(Res.string.scoring_wicket_how))
        ChoiceChips(
            options = DismissalKind.entries,
            selected = kind,
            label = { stringResource(it.label) },
            onSelect = {
                kind = it
                if (!it.hasFielder) fielder = null
            },
        )
        if (!kind.strikerOnly && nonStriker != null) {
            SectionHeader(stringResource(Res.string.scoring_wicket_who), Modifier.padding(top = DesiTheme.space.s2))
            SegmentedControl(listOf(striker, nonStriker), selected = out, label = { it.name }, onSelect = { out = it })
        }
        if (kind.allowsRuns) {
            SectionHeader(stringResource(Res.string.scoring_wicket_runs), Modifier.padding(top = DesiTheme.space.s2), icon = DesiIcons.Swap)
            ChoiceChips(listOf(0, 1, 2, 3), selected = runs, label = { "$it" }, onSelect = { runs = it })
        }
        if (kind.hasFielder) {
            val label = if (kind == DismissalKind.STUMPED) Res.string.scoring_wicket_keeper else Res.string.scoring_wicket_fielder
            SectionHeader(stringResource(label), Modifier.padding(top = DesiTheme.space.s2))
            // Tap the selected fielder again to clear it.
            ChoiceChips(fielders, selected = fielder, label = { it.name }, onSelect = { fielder = if (fielder == it) null else it })
        }
        PrimaryButton(
            text = stringResource(Res.string.scoring_wicket_confirm),
            onClick = {
                val playerOut = if (kind.strikerOnly || nonStriker == null) striker else out
                onConfirm(kind, playerOut, fielder?.takeIf { kind.hasFielder }, if (kind.allowsRuns) runs else 0)
            },
            modifier = Modifier.fillMaxWidth().padding(top = DesiTheme.space.s2),
        )
    }
}

/** A ball that breaks rules the match set to warn: what it breaks, then record it anyway or think again. */
@Composable
internal fun RuleWarningSheet(pending: PendingBall, onRecord: () -> Unit, onCancel: () -> Unit) {
    DesiSheet(stringResource(Res.string.scoring_rule_warning_title), onCancel) {
        pending.warnings.forEach { warning ->
            Text(stringResource(warning.label), style = DesiTheme.type.body, color = DesiTheme.colors.ink)
        }
        PrimaryButton(
            text = stringResource(Res.string.scoring_rule_warning_record),
            onClick = onRecord,
            modifier = Modifier.fillMaxWidth().padding(top = DesiTheme.space.s2),
        )
        GhostButton(stringResource(Res.string.scoring_rule_warning_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Rain: the innings' new overs, no fewer than already bowled, and in the chase the new target. The target is
 * typed because it can move by dozens of runs.
 */
@Composable
internal fun ReviseTargetSheet(innings: InningsState, matchOvers: Int, onRevise: (overs: Int, target: Int?) -> Unit, onDismiss: () -> Unit) {
    val minOvers = ((innings.legalBalls + 5) / 6).coerceAtLeast(1)
    var overs by remember { mutableStateOf((innings.maxBalls / 6).coerceIn(minOvers, matchOvers)) }
    var target by remember { mutableStateOf(innings.target?.toString().orEmpty()) }
    val chase = innings.target != null
    val parsedTarget = target.toIntOrNull()?.takeIf { it in 1..MAX_TARGET }

    DesiSheet(stringResource(Res.string.match_revise), onDismiss, stringResource(Res.string.match_revise_hint)) {
        NumberStepper(
            value = overs,
            onValueChange = { overs = it },
            label = stringResource(Res.string.match_revise_overs_label),
            decreaseLabel = stringResource(Res.string.match_revise_overs_fewer),
            increaseLabel = stringResource(Res.string.match_revise_overs_more),
            range = minOvers..matchOvers,
        )
        if (chase) {
            DesiTextField(
                value = target,
                onValueChange = { new -> target = new.filter { it.isDigit() }.take(3) },
                label = stringResource(Res.string.match_revise_target),
                supportingText = stringResource(Res.string.match_revise_target_hint),
                error = stringResource(Res.string.match_revise_target_invalid).takeIf { parsedTarget == null },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
        } else {
            Text(stringResource(Res.string.match_revise_first_innings_note), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        }
        PrimaryButton(
            text = stringResource(Res.string.match_revise_save),
            onClick = { onRevise(overs, if (chase) parsedTarget else null) },
            enabled = !chase || parsedTarget != null,
            modifier = Modifier.fillMaxWidth().padding(top = DesiTheme.space.s2),
        )
    }
}

/** Where a scoring shot went: tap a sector of the field, or skip. Skipping still records the runs. */
@Composable
internal fun ZoneSheet(runs: Int, onPick: (FieldZone?) -> Unit, onDismiss: () -> Unit) {
    val labels = FieldZone.Clockwise.map { stringResource(it.label) }
    DesiSheet(pluralStringResource(Res.plurals.scoring_zone_title, runs, runs), onDismiss, stringResource(Res.string.scoring_zone_hint)) {
        WagonWheel(labels = labels, onSector = { onPick(FieldZone.Clockwise[it]) })
        GhostButton(stringResource(Res.string.scoring_zone_skip), onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth())
    }
}

/** The handover code, big enough to read off from across the pitch. */
@Composable
internal fun HandoverSheet(code: String?, onDismiss: () -> Unit) {
    DesiSheet(stringResource(Res.string.match_handover), onDismiss) {
        if (code == null) {
            ErrorBanner(stringResource(Res.string.match_handover_failed))
        } else {
            Text(
                code.chunked(3).joinToString(" "),
                style = DesiTheme.type.scoreXl,
                color = DesiTheme.colors.ink,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(stringResource(Res.string.match_handover_code_hint), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        }
        PrimaryButton(text = stringResource(Res.string.rules_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
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
        SectionHeader(stringResource(Res.string.match_penalty_to))
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
