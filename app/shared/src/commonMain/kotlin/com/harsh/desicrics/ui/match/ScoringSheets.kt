package com.harsh.desicrics.ui.match

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.PadKey
import com.harsh.desicrics.designsystem.component.PlayerRow
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.RunPad
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A list of players to tap, e.g. the next batter or the bowler. */
@Composable
internal fun PlayerSheet(
    title: String,
    players: List<Player>,
    onPick: (Player) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
) {
    DesiSheet(title, onDismiss, hint) {
        if (players.isEmpty()) {
            Text(stringResource(Res.string.scoring_no_players), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        }
        Column(Modifier.fillMaxWidth().clip(DesiTheme.radius.md)) {
            players.forEach { player ->
                PlayerRow(player.name, onClick = { onPick(player) })
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
        if (kind == DismissalKind.RUN_OUT) {
            SectionLabel(stringResource(Res.string.scoring_wicket_who), Modifier.padding(top = DesiTheme.space.s2))
            SegmentedControl(listOf(striker, nonStriker), selected = out, label = { it.name }, onSelect = { out = it })
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
                val playerOut = if (kind == DismissalKind.RUN_OUT) out else striker
                onConfirm(kind, playerOut, fielder?.takeIf { kind.hasFielder }, if (kind == DismissalKind.RUN_OUT) runs else 0)
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
    }
