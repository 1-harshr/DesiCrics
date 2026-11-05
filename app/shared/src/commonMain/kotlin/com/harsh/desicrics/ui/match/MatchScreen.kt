package com.harsh.desicrics.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.ui.components.AppScaffold
import com.harsh.desicrics.ui.components.ChoiceChips
import com.harsh.desicrics.ui.components.FullScreenLoading
import com.harsh.desicrics.ui.components.PlayerPickerDialog
import com.harsh.desicrics.ui.components.RunsPickerDialog
import com.harsh.desicrics.ui.components.SectionLabel
import com.harsh.desicrics.ui.theme.BoundaryColor
import com.harsh.desicrics.util.format2
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MatchScreen(
    matchId: String,
    onBack: () -> Unit,
    viewModel: MatchViewModel = koinViewModel { parametersOf(matchId) },
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val match = ui.match

    AppScaffold(
        title = match?.let { "${it.teamA.name} vs ${it.teamB.name}" } ?: "Match",
        onBack = onBack,
    ) { padding ->
        val state = ui.state
        when {
            ui.loading -> FullScreenLoading()
            ui.notFound || state == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("This match doesn't exist any more")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ui.connectionError?.let { message ->
                    item {
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                item { ScoreHeader(state) }
                if (ui.isScorer && !state.isComplete) {
                    item { ScoringPanel(ui, viewModel) }
                } else if (ui.isScorer && ui.canUndo) {
                    item {
                        OutlinedButton(onClick = viewModel::undo, modifier = Modifier.fillMaxWidth()) {
                            Text("Undo last ball")
                        }
                    }
                }
                state.innings.forEach { innings ->
                    item(key = "innings-${innings.number}") { InningsCard(innings) }
                }
            }
        }
    }

    ui.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Something went wrong") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun ScoreHeader(state: MatchState) {
    val innings = state.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.innings.size > 1) {
                val first = state.innings.first()
                Text("${first.battingTeam.name} ${first.scoreText} (${first.oversText})", style = MaterialTheme.typography.bodyMedium)
            }
            Text(innings.battingTeam.name, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(innings.scoreText, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    "  (${innings.oversText} / ${state.match.oversPerInnings} ov)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            val details = buildList {
                add("CRR ${innings.runRate.format2()}")
                val needed = innings.runsNeeded
                if (needed != null && !innings.isComplete) {
                    add("Need $needed off ${innings.ballsRemaining} balls")
                }
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            state.result?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            if (innings.currentOver.isNotEmpty() && !state.isComplete) {
                Text("This over", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    innings.currentOver.forEach { BallChip(it) }
                }
            }
        }
    }
}

@Composable
private fun BallChip(delivery: Delivery) {
    val (background, content) = when {
        delivery.wicket != null -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        delivery.runsOffBat == 4 || delivery.runsOffBat == 6 -> BoundaryColor to Color.White
        else -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier.size(36.dp).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(delivery.label(), color = content, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun Delivery.label(): String {
    val base = when (val type = extra) {
        ExtraType.WIDE -> "${extraRuns}wd"
        ExtraType.NO_BALL -> if (runsOffBat > 0) "${runsOffBat}nb" else "nb"
        ExtraType.BYE, ExtraType.LEG_BYE -> "$extraRuns${type.shortName}"
        null -> "$runsOffBat"
    }
    return if (wicket != null) "W" else base
}

private sealed interface ScoringDialog {
    data object PickStriker : ScoringDialog
    data object PickNonStriker : ScoringDialog
    data object PickBowler : ScoringDialog
    data class Extra(val type: ExtraType) : ScoringDialog
    data object Wicket : ScoringDialog
}

@Composable
private fun ScoringPanel(ui: MatchUiState, viewModel: MatchViewModel) {
    val innings = ui.state?.current ?: return
    var dialog by remember { mutableStateOf<ScoringDialog?>(null) }

    fun batter(id: String?) = innings.battingTeam.players.firstOrNull { it.id == id }
    fun bowler(id: String?) = innings.bowlingTeam.players.firstOrNull { it.id == id }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Scoring")

            CreaseRow("Striker", batter(ui.strikerId), innings, isStriker = true) { dialog = ScoringDialog.PickStriker }
            CreaseRow("Non-striker", batter(ui.nonStrikerId), innings, isStriker = false) { dialog = ScoringDialog.PickNonStriker }
            val currentBowler = bowler(ui.bowlerId)
            if (currentBowler == null) {
                OutlinedButton(onClick = { dialog = ScoringDialog.PickBowler }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (innings.legalBalls == 0) "Choose opening bowler" else "Choose next bowler")
                }
            } else {
                val card = innings.bowlers.firstOrNull { it.player.id == currentBowler.id }
                Text(
                    "Bowling: ${currentBowler.name}" +
                        (card?.let { "  ${it.oversText}-${it.maidens}-${it.runs}-${it.wickets}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (ui.canScore) {
                HorizontalDivider()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2, 3, 4, 6).forEach { runs ->
                        FilledTonalButton(onClick = { viewModel.recordRuns(runs) }, modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) {
                            Text("$runs", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ExtraType.WIDE to "Wide",
                        ExtraType.NO_BALL to "No ball",
                        ExtraType.BYE to "Bye",
                        ExtraType.LEG_BYE to "Leg bye",
                    ).forEach { (type, label) ->
                        OutlinedButton(onClick = { dialog = ScoringDialog.Extra(type) }) { Text(label) }
                    }
                    FilledTonalButton(
                        onClick = { dialog = ScoringDialog.Wicket },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) { Text("Wicket") }
                }
            }
            if (ui.canUndo) {
                TextButton(onClick = viewModel::undo) { Text("Undo last ball") }
            }
        }
    }

    when (val current = dialog) {
        null -> Unit
        ScoringDialog.PickStriker, ScoringDialog.PickNonStriker -> {
            val other = if (current == ScoringDialog.PickStriker) ui.nonStrikerId else ui.strikerId
            PlayerPickerDialog(
                title = if (current == ScoringDialog.PickStriker) "Who's on strike?" else "Who's at the non-striker's end?",
                players = innings.yetToBat.filter { it.id != other },
                onPick = {
                    if (current == ScoringDialog.PickStriker) viewModel.selectStriker(it.id) else viewModel.selectNonStriker(it.id)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
        ScoringDialog.PickBowler -> PlayerPickerDialog(
            title = "Who's bowling?",
            // The bowler of the previous over can't bowl two in a row.
            players = innings.bowlingTeam.players.filter { it.id != innings.previousOverBowlerId },
            onPick = {
                viewModel.selectBowler(it.id)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is ScoringDialog.Extra -> when (current.type) {
            ExtraType.WIDE -> RunsPickerDialog(
                title = "Wide",
                message = "Runs taken off the wide (1 wide is added automatically)",
                options = listOf(0, 1, 2, 3, 4),
                onPick = { viewModel.recordExtra(ExtraType.WIDE, it); dialog = null },
                onDismiss = { dialog = null },
            )
            ExtraType.NO_BALL -> RunsPickerDialog(
                title = "No ball",
                message = "Runs off the bat (1 no-ball run is added automatically)",
                options = listOf(0, 1, 2, 3, 4, 6),
                onPick = { viewModel.recordExtra(ExtraType.NO_BALL, it); dialog = null },
                onDismiss = { dialog = null },
            )
            ExtraType.BYE, ExtraType.LEG_BYE -> RunsPickerDialog(
                title = if (current.type == ExtraType.BYE) "Byes" else "Leg byes",
                message = "How many runs?",
                options = listOf(1, 2, 3, 4),
                onPick = { viewModel.recordExtra(current.type, it); dialog = null },
                onDismiss = { dialog = null },
            )
        }
        ScoringDialog.Wicket -> WicketDialog(
            striker = batter(ui.strikerId),
            nonStriker = batter(ui.nonStrikerId),
            onConfirm = { kind, nonStrikerOut ->
                viewModel.recordWicket(kind, nonStrikerOut)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun CreaseRow(label: String, player: Player?, innings: InningsState, isStriker: Boolean, onChoose: () -> Unit) {
    if (player == null) {
        OutlinedButton(onClick = onChoose, modifier = Modifier.fillMaxWidth()) { Text("Choose $label".lowercase().replaceFirstChar { it.uppercase() }) }
        return
    }
    val card = innings.batters.firstOrNull { it.player.id == player.id }
    Text(
        "${player.name}${if (isStriker) " *" else ""}  ${card?.runs ?: 0} (${card?.balls ?: 0})",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (isStriker) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun WicketDialog(
    striker: Player?,
    nonStriker: Player?,
    onConfirm: (DismissalKind, nonStrikerOut: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(DismissalKind.BOWLED) }
    var nonStrikerOut by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wicket") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceChips(DismissalKind.entries, kind, { it.displayName }, { kind = it })
                if (kind == DismissalKind.RUN_OUT) {
                    Text("Who's out?", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !nonStrikerOut, onClick = { nonStrikerOut = false }, label = { Text(striker?.name ?: "Striker") })
                        FilterChip(selected = nonStrikerOut, onClick = { nonStrikerOut = true }, label = { Text(nonStriker?.name ?: "Non-striker") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(kind, kind == DismissalKind.RUN_OUT && nonStrikerOut) }) { Text("Out!") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InningsCard(innings: InningsState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(
                    "${innings.battingTeam.name} innings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text("${innings.scoreText} (${innings.oversText})", style = MaterialTheme.typography.titleMedium)
            }

            StatRow(listOf("Batter", "R", "B", "4s", "6s", "SR"), header = true)
            innings.batters.forEach { card ->
                StatRow(
                    listOf(card.player.name, "${card.runs}", "${card.balls}", "${card.fours}", "${card.sixes}", card.strikeRate.format2()),
                    subtitle = card.dismissal ?: "not out",
                )
            }
            val extras = innings.extras
            Text(
                "Extras ${extras.total} (wd ${extras.wides}, nb ${extras.noBalls}, b ${extras.byes}, lb ${extras.legByes})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (innings.yetToBat.isNotEmpty()) {
                Text(
                    "Yet to bat: ${innings.yetToBat.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            StatRow(listOf("Bowler", "O", "M", "R", "W", "Econ"), header = true)
            innings.bowlers.forEach { card ->
                StatRow(listOf(card.player.name, card.oversText, "${card.maidens}", "${card.runs}", "${card.wickets}", card.economy.format2()))
            }
        }
    }
}

@Composable
private fun StatRow(cells: List<String>, header: Boolean = false, subtitle: String? = null) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(2.6f)) {
            Text(cells.first(), style = style, color = color, fontWeight = if (header) FontWeight.Normal else FontWeight.Medium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        cells.drop(1).forEachIndexed { index, cell ->
            Text(
                cell,
                style = style,
                color = color,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(if (index == cells.size - 2) 1.4f else 0.9f),
            )
        }
    }
}
