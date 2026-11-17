package com.harsh.desicrics.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.FullScreenLoading
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onNewMatch: () -> Unit,
    onOpenMatch: (matchId: String) -> Unit,
    onProfile: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DesiScaffold(
        title = "DesiCrics",
        actions = { TextButton(onClick = onProfile) { Text("Profile") } },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewMatch) { Text("Start a match") }
        },
    ) { padding ->
        when {
            state.loading -> FullScreenLoading()
            state.error != null && state.matches.isEmpty() -> CenteredMessage(state.error!!, Modifier.padding(padding))
            state.matches.isEmpty() -> CenteredMessage(
                "No matches yet.\nStart one and score it ball by ball.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.matches, key = { it.id }) { match ->
                    MatchCard(match, isMine = match.createdBy == viewModel.currentUid, onClick = { onOpenMatch(match.id) })
                }
            }
        }
    }
}

@Composable
private fun MatchCard(match: Match, isMine: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${match.teamA.name} vs ${match.teamB.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                val live = match.status == MatchStatus.IN_PROGRESS
                AssistChip(
                    onClick = onClick,
                    label = { Text(if (live) "LIVE" else "Result") },
                    colors = if (live) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    },
                )
            }
            Text(
                match.scoreSummary.ifBlank { "${match.oversPerInnings} overs · yet to start" },
                style = MaterialTheme.typography.bodyMedium,
            )
            match.result?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            if (isMine) {
                Text("You're the scorer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
