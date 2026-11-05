package com.harsh.desicrics.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.ui.components.AppScaffold

@Composable
fun ProfileScreen(
    profile: UserProfile,
    onEdit: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    AppScaffold(title = "Profile", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(profile.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(profile.role.displayName, profile.city.ifBlank { null }).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileRow("Mobile", profile.phone.ifBlank { "—" })
                    ProfileRow("Email", profile.email ?: "—")
                    ProfileRow("Batting", profile.battingStyle.displayName)
                    if (profile.bowlingStyle != BowlingStyle.NONE) {
                        ProfileRow("Bowling", profile.bowlingStyle.displayName)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit profile") }
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
