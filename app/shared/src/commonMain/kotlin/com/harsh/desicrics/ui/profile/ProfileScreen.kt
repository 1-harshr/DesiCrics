package com.harsh.desicrics.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.api.SupportCategory
import com.harsh.desicrics.api.SupportRequest
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.home.HomeMatchCard
import com.harsh.desicrics.ui.teams.TeamRow
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private enum class ProfileTab { ABOUT, MATCHES, TEAMS }

private enum class SettingsSheet { CLAIM, SUPPORT, DELETE }

@Composable
fun ProfileScreen(
    profile: UserProfile,
    onEdit: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    onOpenMatch: (String) -> Unit = {},
    onOpenTeam: (String) -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }
    val close = {
        sheet = null
        viewModel.clearResult()
    }
    ProfileContent(
        profile = profile,
        state = state,
        onEdit = onEdit,
        onSignOut = onSignOut,
        onBack = onBack,
        onOpenMatch = onOpenMatch,
        onOpenTeam = onOpenTeam,
        onClaimSuggested = viewModel::claim,
        onSettings = { sheet = it },
    )
    when (sheet) {
        null -> Unit
        SettingsSheet.CLAIM -> ClaimSheet(state, onClaim = viewModel::claimByCode, onDismiss = close)
        SettingsSheet.SUPPORT -> SupportSheet(state, onSend = { category, message -> viewModel.contactSupport(category, message, "") }, onDismiss = close)
        SettingsSheet.DELETE -> DeleteAccountSheet(state, onDelete = { viewModel.deleteAccount(onSignOut) }, onDismiss = close)
    }
}

@Composable
private fun ProfileContent(
    profile: UserProfile,
    state: ProfileUiState,
    onEdit: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    onOpenMatch: (String) -> Unit,
    onOpenTeam: (String) -> Unit,
    onClaimSuggested: (String) -> Unit,
    onSettings: (SettingsSheet) -> Unit,
) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    var tab by rememberSaveable { mutableStateOf(ProfileTab.ABOUT) }
    DesiScaffold(title = stringResource(Res.string.profile_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.screen, vertical = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s5),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s4)) {
                Avatar(profile.name, size = 64)
                Column(verticalArrangement = Arrangement.spacedBy(space.s1)) {
                    Text(profile.name, style = DesiTheme.type.titleLg, color = colors.ink)
                    val role = stringResource(profile.role.label)
                    Text(
                        if (profile.city.isBlank()) role else stringResource(Res.string.common_joined, role, profile.city),
                        style = DesiTheme.type.body,
                        color = colors.muted,
                    )
                }
            }
            state.claimable.forEach { ClaimSuggestion(it, onClaim = { onClaimSuggested(it.profileId) }) }
            SegmentedControl(
                options = ProfileTab.entries,
                selected = tab,
                label = { stringResource(it.label) },
                onSelect = { tab = it },
            )
            when (tab) {
                ProfileTab.ABOUT -> AboutTab(profile, onEdit, onSignOut, onSettings)
                ProfileTab.MATCHES -> ListTab(state.matches, Res.string.profile_no_matches, state.loadFailed) { matches ->
                    matches.forEach { HomeMatchCard(it, isMine = it.scorerUid == profile.uid, onClick = { onOpenMatch(it.id) }) }
                }
                ProfileTab.TEAMS -> ListTab(state.teams, Res.string.teams_empty, state.loadFailed) { teams ->
                    teams.forEach { TeamRow(it, onClick = { onOpenTeam(it.id) }) }
                }
            }
        }
    }
}

@Composable
private fun AboutTab(profile: UserProfile, onEdit: () -> Unit, onSignOut: () -> Unit, onSettings: (SettingsSheet) -> Unit) {
    val colors = DesiTheme.colors
    DesiCard(Modifier.fillMaxWidth()) {
        val empty = stringResource(Res.string.common_empty_value)
        ProfileRow(Res.string.profile_mobile, profile.phone.ifBlank { empty })
        HorizontalDivider(color = colors.line)
        ProfileRow(Res.string.profile_email, profile.email ?: empty)
        HorizontalDivider(color = colors.line)
        ProfileRow(Res.string.profile_batting, stringResource(profile.battingStyle.label))
        if (profile.bowlingStyle != BowlingStyle.NONE) {
            HorizontalDivider(color = colors.line)
            ProfileRow(Res.string.profile_bowling, stringResource(profile.bowlingStyle.label))
        }
    }
    PrimaryButton(stringResource(Res.string.profile_edit), onClick = onEdit, modifier = Modifier.fillMaxWidth())
    SectionLabel(stringResource(Res.string.profile_settings))
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        GhostButton(stringResource(Res.string.profile_claim), onClick = { onSettings(SettingsSheet.CLAIM) }, modifier = Modifier.fillMaxWidth())
        GhostButton(stringResource(Res.string.profile_support), onClick = { onSettings(SettingsSheet.SUPPORT) }, modifier = Modifier.fillMaxWidth())
        GhostButton(stringResource(Res.string.profile_sign_out), onClick = onSignOut, modifier = Modifier.fillMaxWidth())
        GhostButton(stringResource(Res.string.profile_delete), onClick = { onSettings(SettingsSheet.DELETE) }, modifier = Modifier.fillMaxWidth())
    }
}

/** A list that's still loading (null), empty, or ready. */
@Composable
private fun <T> ColumnScope.ListTab(items: List<T>?, empty: StringResource, failed: Boolean, content: @Composable ColumnScope.(List<T>) -> Unit) {
    when {
        items == null && failed -> Text(stringResource(Res.string.teams_load_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket)
        items == null -> Unit
        items.isEmpty() -> Text(stringResource(empty), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        else -> content(items)
    }
}

/** A team added this user, by their verified number, before they had the app. */
@Composable
private fun ClaimSuggestion(player: PlayerSummary, onClaim: () -> Unit) {
    DesiCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.profile_claimable_title), style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
        Text(stringResource(Res.string.profile_claimable_body, player.name), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        PrimaryButton(stringResource(Res.string.profile_claimable_action), onClick = onClaim, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ClaimSheet(state: ProfileUiState, onClaim: (String) -> Unit, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    DesiSheet(stringResource(Res.string.profile_claim), onDismiss, stringResource(Res.string.profile_claim_hint)) {
        if (state.result == SettingsResult.CLAIMED) {
            Text(stringResource(Res.string.profile_claim_done), style = DesiTheme.type.body, color = DesiTheme.colors.brand)
            PrimaryButton(stringResource(Res.string.rules_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            return@DesiSheet
        }
        DesiTextField(
            value = code,
            onValueChange = { new -> code = new.filter { it.isLetterOrDigit() }.uppercase().take(8) },
            label = stringResource(Res.string.profile_claim_code),
            error = stringResource(Res.string.profile_claim_failed).takeIf { state.result == SettingsResult.CLAIM_FAILED },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        )
        PrimaryButton(
            stringResource(Res.string.profile_claim_action),
            onClick = { onClaim(code) },
            enabled = code.length >= 6,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SupportSheet(state: ProfileUiState, onSend: (SupportCategory, String) -> Unit, onDismiss: () -> Unit) {
    var category by rememberSaveable { mutableStateOf(SupportCategory.BUG) }
    var message by rememberSaveable { mutableStateOf("") }
    DesiSheet(stringResource(Res.string.profile_support), onDismiss, stringResource(Res.string.profile_support_hint)) {
        if (state.result == SettingsResult.SUPPORT_SENT) {
            Text(stringResource(Res.string.profile_support_sent), style = DesiTheme.type.body, color = DesiTheme.colors.brand)
            PrimaryButton(stringResource(Res.string.rules_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            return@DesiSheet
        }
        SectionLabel(stringResource(Res.string.profile_support_category))
        ChoiceChips(SupportCategory.entries, selected = category, label = { stringResource(it.label) }, onSelect = { category = it })
        DesiTextField(
            value = message,
            onValueChange = { message = it.take(SupportRequest.MAX_MESSAGE) },
            label = stringResource(Res.string.profile_support_message),
            singleLine = false,
            error = stringResource(Res.string.teams_action_failed).takeIf { state.result == SettingsResult.SUPPORT_FAILED },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        PrimaryButton(
            stringResource(Res.string.profile_support_send),
            onClick = { onSend(category, message) },
            enabled = message.isNotBlank(),
            loading = state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeleteAccountSheet(state: ProfileUiState, onDelete: () -> Unit, onDismiss: () -> Unit) {
    DesiSheet(stringResource(Res.string.profile_delete_title), onDismiss) {
        Text(stringResource(Res.string.profile_delete_body), style = DesiTheme.type.body, color = DesiTheme.colors.ink)
        if (state.result == SettingsResult.DELETE_FAILED) {
            Text(stringResource(Res.string.profile_delete_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket)
        }
        PrimaryButton(stringResource(Res.string.profile_delete_confirm), onClick = onDelete, loading = state.saving, modifier = Modifier.fillMaxWidth())
        GhostButton(stringResource(Res.string.common_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProfileRow(label: StringResource, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1)) {
        Text(stringResource(label).uppercase(), style = DesiTheme.type.label, color = DesiTheme.colors.muted)
        Text(value, style = DesiTheme.type.bodyLg, color = DesiTheme.colors.ink)
    }
}

private val ProfileTab.label: StringResource
    get() = when (this) {
        ProfileTab.ABOUT -> Res.string.profile_tab_about
        ProfileTab.MATCHES -> Res.string.profile_tab_matches
        ProfileTab.TEAMS -> Res.string.profile_tab_teams
    }

private val SupportCategory.label: StringResource
    get() = when (this) {
        SupportCategory.BUG -> Res.string.support_bug
        SupportCategory.IDEA -> Res.string.support_idea
        SupportCategory.ACCOUNT -> Res.string.support_account
        SupportCategory.OTHER -> Res.string.support_other
    }

// Previews: sample player data only; all labels come from string resources.

private val PreviewProfile = UserProfile(
    uid = "preview",
    name = "Rohan Iyer",
    phone = "+91 98765 43210",
    email = null,
    city = "Mumbai",
    role = PlayerRole.ALL_ROUNDER,
    battingStyle = BattingStyle.LEFT_HAND,
    bowlingStyle = BowlingStyle.RIGHT_ARM_OFF_SPIN,
    createdAt = 0,
)

@Preview
@Composable
private fun ProfilePreview() = DesiTheme(darkTheme = false) {
    ProfileContent(PreviewProfile, ProfileUiState(), {}, {}, {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun ProfileClaimDarkPreview() = DesiTheme(darkTheme = true) {
    ProfileContent(
        PreviewProfile.copy(city = "", bowlingStyle = BowlingStyle.NONE),
        ProfileUiState(claimable = listOf(PlayerSummary("p1", "Rohan", isPlaceholder = true))),
        {}, {}, {}, {}, {}, {}, {},
    )
}
