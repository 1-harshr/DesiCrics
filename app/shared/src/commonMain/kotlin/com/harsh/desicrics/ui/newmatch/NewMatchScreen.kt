package com.harsh.desicrics.ui.newmatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.ui.components.AppScaffold
import com.harsh.desicrics.ui.components.ChoiceChips
import com.harsh.desicrics.ui.components.SectionLabel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NewMatchScreen(
    onCreated: (matchId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: NewMatchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScaffold(title = "New match", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TeamFields(
                label = "Team A",
                name = state.teamAName,
                players = state.teamAPlayers,
                onNameChange = { value -> viewModel.update { it.copy(teamAName = value) } },
                onPlayersChange = { value -> viewModel.update { it.copy(teamAPlayers = value) } },
            )
            TeamFields(
                label = "Team B",
                name = state.teamBName,
                players = state.teamBPlayers,
                onNameChange = { value -> viewModel.update { it.copy(teamBName = value) } },
                onPlayersChange = { value -> viewModel.update { it.copy(teamBPlayers = value) } },
            )

            OutlinedTextField(
                value = state.overs,
                onValueChange = { value -> viewModel.update { it.copy(overs = value.filter(Char::isDigit).take(2)) } },
                label = { Text("Overs per innings") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Toss won by")
            ChoiceChips(
                options = TeamSide.entries,
                selected = state.tossWinner,
                label = { side ->
                    val name = if (side == TeamSide.A) state.teamAName else state.teamBName
                    name.ifBlank { "Team ${side.name}" }
                },
                onSelect = { value -> viewModel.update { it.copy(tossWinner = value) } },
            )
            SectionLabel("Elected to")
            ChoiceChips(
                options = TossDecision.entries,
                selected = state.tossDecision,
                label = { it.displayName },
                onSelect = { value -> viewModel.update { it.copy(tossDecision = value) } },
            )

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { viewModel.create(onCreated) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (state.saving) "Creating…" else "Start match")
            }
        }
    }
}

@Composable
private fun TeamFields(
    label: String,
    name: String,
    players: String,
    onNameChange: (String) -> Unit,
    onPlayersChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Team name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = players,
            onValueChange = onPlayersChange,
            label = { Text("Players (one per line)") },
            minLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
