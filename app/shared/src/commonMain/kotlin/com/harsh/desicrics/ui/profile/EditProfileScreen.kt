package com.harsh.desicrics.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.api.ProfileError
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.FullScreenLoading
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.profile_batting_section
import com.harsh.desicrics.resources.profile_bowling_section
import com.harsh.desicrics.resources.profile_city_label
import com.harsh.desicrics.resources.profile_edit_title
import com.harsh.desicrics.resources.profile_error_save_failed
import com.harsh.desicrics.resources.profile_finish_setup
import com.harsh.desicrics.resources.profile_name_label
import com.harsh.desicrics.resources.profile_phone_label
import com.harsh.desicrics.resources.profile_phone_placeholder
import com.harsh.desicrics.resources.profile_role_section
import com.harsh.desicrics.resources.profile_save
import com.harsh.desicrics.resources.profile_setup_intro
import com.harsh.desicrics.resources.profile_setup_title
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.stringResource
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
    EditProfileContent(
        state = state,
        isOnboarding = isOnboarding,
        onUpdate = viewModel::update,
        onSave = { viewModel.save(onDone) },
        onBack = onBack,
    )
}

/** The profile form without its ViewModel, so previews and tests can render any state. */
@Composable
fun EditProfileContent(
    state: ProfileFormState,
    isOnboarding: Boolean,
    onUpdate: ((ProfileFormState) -> ProfileFormState) -> Unit,
    onSave: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    val title = if (isOnboarding) Res.string.profile_setup_title else Res.string.profile_edit_title

    DesiScaffold(title = stringResource(title), onBack = onBack) { padding ->
        if (state.loading) {
            FullScreenLoading(Modifier.padding(padding))
            return@DesiScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.screen, vertical = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s4),
        ) {
            if (isOnboarding) {
                Text(stringResource(Res.string.profile_setup_intro), style = DesiTheme.type.body, color = colors.muted)
            }
            DesiTextField(
                value = state.name,
                onValueChange = { value -> onUpdate { it.copy(name = value) } },
                label = stringResource(Res.string.profile_name_label),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            DesiTextField(
                value = state.phone,
                onValueChange = { value -> onUpdate { it.copy(phone = value) } },
                label = stringResource(Res.string.profile_phone_label),
                placeholder = stringResource(Res.string.profile_phone_placeholder),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            DesiTextField(
                value = state.city,
                onValueChange = { value -> onUpdate { it.copy(city = value) } },
                label = stringResource(Res.string.profile_city_label),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )

            Column(verticalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.padding(top = space.s2)) {
                SectionLabel(stringResource(Res.string.profile_role_section))
                ChoiceChips(PlayerRole.entries, state.role, { stringResource(it.label) }, { value -> onUpdate { it.copy(role = value) } })
            }
            Column(verticalArrangement = Arrangement.spacedBy(space.s2)) {
                SectionLabel(stringResource(Res.string.profile_batting_section))
                ChoiceChips(BattingStyle.entries, state.battingStyle, { stringResource(it.label) }, { value -> onUpdate { it.copy(battingStyle = value) } })
            }
            Column(verticalArrangement = Arrangement.spacedBy(space.s2)) {
                SectionLabel(stringResource(Res.string.profile_bowling_section))
                ChoiceChips(BowlingStyle.entries, state.bowlingStyle, { stringResource(it.label) }, { value -> onUpdate { it.copy(bowlingStyle = value) } })
            }

            state.error?.let { error ->
                val text = when (error) {
                    is ProfileFormError.Invalid -> stringResource(error.error.label)
                    ProfileFormError.SaveFailed -> stringResource(Res.string.profile_error_save_failed)
                }
                Text(text, style = DesiTheme.type.body, color = colors.event.wicket)
            }

            PrimaryButton(
                text = stringResource(if (isOnboarding) Res.string.profile_finish_setup else Res.string.profile_save),
                onClick = onSave,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth().padding(top = space.s2),
            )
        }
    }
}

// Previews: sample player data only; all labels come from string resources.

private val PreviewForm = ProfileFormState(
    loading = false,
    name = "Rohan Iyer",
    phone = "+91 98765 43210",
    city = "Mumbai",
    role = PlayerRole.ALL_ROUNDER,
    bowlingStyle = BowlingStyle.RIGHT_ARM_OFF_SPIN,
)

@Preview
@Composable
private fun OnboardingPreview() = DesiTheme(darkTheme = false) {
    EditProfileContent(ProfileFormState(loading = false), isOnboarding = true, onUpdate = {}, onSave = {}, onBack = null)
}

@Preview
@Composable
private fun EditProfilePreview() = DesiTheme(darkTheme = false) {
    EditProfileContent(PreviewForm, isOnboarding = false, onUpdate = {}, onSave = {}, onBack = {})
}

@Preview
@Composable
private fun EditProfileDarkPreview() = DesiTheme(darkTheme = true) {
    EditProfileContent(PreviewForm, isOnboarding = false, onUpdate = {}, onSave = {}, onBack = {})
}

@Preview
@Composable
private fun EditProfileErrorPreview() = DesiTheme(darkTheme = false) {
    EditProfileContent(
        PreviewForm.copy(name = "", error = ProfileFormError.Invalid(ProfileError.NAME_MISSING)),
        isOnboarding = true,
        onUpdate = {},
        onSave = {},
        onBack = null,
    )
}

@Preview
@Composable
private fun EditProfileSavingPreview() = DesiTheme(darkTheme = false) {
    EditProfileContent(PreviewForm.copy(saving = true), isOnboarding = false, onUpdate = {}, onSave = {}, onBack = {})
}
