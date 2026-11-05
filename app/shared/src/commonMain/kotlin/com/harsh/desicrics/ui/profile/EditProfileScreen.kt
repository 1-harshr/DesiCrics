package com.harsh.desicrics.ui.profile

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
import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.ui.components.AppScaffold
import com.harsh.desicrics.ui.components.ChoiceChips
import com.harsh.desicrics.ui.components.FullScreenLoading
import com.harsh.desicrics.ui.components.SectionLabel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Onboarding and profile editing. During onboarding [onDone] has nothing to do: saving the profile
 * flips the session to signed-in, which swaps this screen out.
 */
@Composable
fun EditProfileScreen(
    isOnboarding: Boolean,
    onDone: () -> Unit,
    onBack: (() -> Unit)?,
    viewModel: EditProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScaffold(title = if (isOnboarding) "Set up your profile" else "Edit profile", onBack = onBack) { padding ->
        if (state.loading) {
            FullScreenLoading()
            return@AppScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isOnboarding) {
                Text(
                    "Tell us a bit about your game. Teammates will find you by your mobile number.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = state.name,
                onValueChange = { value -> viewModel.update { it.copy(name = value) } },
                label = { Text("Full name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.phone,
                onValueChange = { value -> viewModel.update { it.copy(phone = value) } },
                label = { Text("Mobile number") },
                placeholder = { Text("+91 98765 43210") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.city,
                onValueChange = { value -> viewModel.update { it.copy(city = value) } },
                label = { Text("City (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Playing role")
            ChoiceChips(PlayerRole.entries, state.role, { it.displayName }, { value -> viewModel.update { it.copy(role = value) } })

            SectionLabel("Batting")
            ChoiceChips(BattingStyle.entries, state.battingStyle, { it.displayName }, { value -> viewModel.update { it.copy(battingStyle = value) } })

            SectionLabel("Bowling")
            ChoiceChips(BowlingStyle.entries, state.bowlingStyle, { it.displayName }, { value -> viewModel.update { it.copy(bowlingStyle = value) } })

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { viewModel.save(onDone) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (state.saving) "Saving…" else if (isOnboarding) "Let's play" else "Save")
            }
        }
    }
}
