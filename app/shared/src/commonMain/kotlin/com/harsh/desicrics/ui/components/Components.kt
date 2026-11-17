package com.harsh.desicrics.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.model.Player

// Match-specific dialogs, to be replaced by design-system sheets when scoring is rebuilt in Phase 1.

@Composable
fun PlayerPickerDialog(
    title: String,
    players: List<Player>,
    onPick: (Player) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (players.isEmpty()) {
                Text("No players available")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(players, key = { it.id }) { player ->
                        TextButton(onClick = { onPick(player) }, modifier = Modifier.fillMaxWidth()) {
                            Text(player.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks "how many runs?" for extras. */
@Composable
fun RunsPickerDialog(
    title: String,
    message: String,
    options: List<Int>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { runs ->
                        FilterChip(selected = false, onClick = { onPick(runs) }, label = { Text("$runs") })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
