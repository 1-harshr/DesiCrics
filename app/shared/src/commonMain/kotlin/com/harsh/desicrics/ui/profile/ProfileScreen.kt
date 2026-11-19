package com.harsh.desicrics.ui.profile
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.ErrorState

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
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.home.HomeMatchCard
import com.harsh.desicrics.ui.stats.CareerSection
import com.harsh.desicrics.ui.teams.TeamList
import com.harsh.desicrics.ui.text.icon
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.HeroChip
import com.harsh.desicrics.designsystem.component.HeroChips
import com.harsh.desicrics.designsystem.component.HeroIconButton
import com.harsh.desicrics.designsystem.component.HeroIdentity
import com.harsh.desicrics.designsystem.component.HeroScaffold
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.icon.DesiIcons
import org.jetbrains.compose.resources.DrawableResource
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private enum class ProfileTab { ABOUT, STATS, MATCHES, TEAMS }

private enum class SettingsSheet { CLAIM, SUPPORT, DELETE }

@Composable
fun ProfileScreen(
    profile: UserProfile,
    onEdit: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    onOpenMatch: (String) -> Unit = {},
    onOpenTeam: (String) -> Unit = {},
    onCompare: () -> Unit = {},
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
        stats = { CareerSection(profile.uid, onCompare = onCompare, name = profile.name, onOpenMatch = onOpenMatch) },
        onRetry = viewModel::refresh,
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
    stats: @Composable () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    var tab by rememberSaveable { mutableStateOf(ProfileTab.ABOUT) }
    HeroScaffold(
        title = profile.name,
        onBack = null,
        actions = { HeroIconButton(DesiIcons.Edit, stringResource(Res.string.profile_edit), onEdit) },
        hero = {
            HeroIdentity(
                name = profile.name,
                eyebrow = stringResource(Res.string.profile_eyebrow),
                avatar = { PhotoAvatar(profile.name, profile.photoUrl, size = 72) },
            )
            HeroChips(
                listOf(
                    HeroChip(stringResource(profile.role.label), profile.role.icon),
                    HeroChip(profile.city, DesiIcons.Location),
                    HeroChip(stringResource(profile.battingStyle.label), DesiIcons.Bat),
                    HeroChip(if (profile.bowlingStyle == BowlingStyle.NONE) "" else stringResource(profile.bowlingStyle.label), DesiIcons.Ball),
                ),
            )
        },
    ) {
        state.claimable.forEach { ClaimSuggestion(it, onClaim = { onClaimSuggested(it.profileId) }) }
        SegmentedControl(
            options = ProfileTab.entries,
            selected = tab,
            label = { stringResource(it.label) },
            onSelect = { tab = it },
        )
        when (tab) {
            ProfileTab.ABOUT -> AboutTab(profile, onEdit, onSignOut, onSettings)
            ProfileTab.STATS -> stats()
            ProfileTab.MATCHES -> ListTab(state.matches, Res.string.profile_no_matches, state.loadFailed, DesiIcons.History, onRetry) { matches ->
                Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                    matches.forEach { HomeMatchCard(it, isMine = it.scorerUid == profile.uid, onClick = { onOpenMatch(it.id) }) }
                }
            }
            ProfileTab.TEAMS -> ListTab(state.teams, Res.string.teams_empty, state.loadFailed, DesiIcons.Shield, onRetry) { teams ->
                TeamList(teams, onOpenTeam)
            }
        }
    }
}

@Composable
private fun AboutTab(profile: UserProfile, onEdit: () -> Unit, onSignOut: () -> Unit, onSettings: (SettingsSheet) -> Unit) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    val empty = stringResource(Res.string.common_empty_value)
    Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
        SectionHeader(stringResource(Res.string.profile_section_details), icon = DesiIcons.Person, action = stringResource(Res.string.profile_edit_short), onAction = onEdit)
        ListCard {
            ListRow(profile.phone.ifBlank { empty }, subtitle = stringResource(Res.string.profile_mobile), leading = { IconTile(DesiIcons.Phone) })
            ListRow(profile.email ?: empty, subtitle = stringResource(Res.string.profile_email), leading = { IconTile(DesiIcons.Mail) }, divider = true)
            ListRow(stringResource(profile.battingStyle.label), subtitle = stringResource(Res.string.profile_batting), leading = { IconTile(DesiIcons.Bat) }, divider = true)
            if (profile.bowlingStyle != BowlingStyle.NONE) {
                ListRow(stringResource(profile.bowlingStyle.label), subtitle = stringResource(Res.string.profile_bowling), leading = { IconTile(DesiIcons.Ball) }, divider = true)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
        SectionHeader(stringResource(Res.string.profile_settings), icon = DesiIcons.Settings)
        ListCard {
            ListRow(stringResource(Res.string.profile_claim), leading = { IconTile(DesiIcons.Badge) }, onClick = { onSettings(SettingsSheet.CLAIM) })
            ListRow(stringResource(Res.string.profile_support), leading = { IconTile(DesiIcons.Support) }, divider = true, onClick = { onSettings(SettingsSheet.SUPPORT) })
            ListRow(stringResource(Res.string.profile_sign_out), leading = { IconTile(DesiIcons.Logout) }, divider = true, onClick = onSignOut)
            ListRow(
                stringResource(Res.string.profile_delete),
                leading = { IconTile(DesiIcons.Delete, tint = colors.event.wicket, container = colors.event.wicket.copy(alpha = 0.12f)) },
                titleColor = colors.event.wicket,
                divider = true,
                onClick = { onSettings(SettingsSheet.DELETE) },
            )
        }
    }
}

/** A list that's still loading (null), empty, or ready. */
@Composable
private fun <T> ColumnScope.ListTab(items: List<T>?, empty: StringResource, failed: Boolean, icon: DrawableResource, onRetry: () -> Unit, content: @Composable ColumnScope.(List<T>) -> Unit) {
    when {
        items == null && failed -> ErrorState(stringResource(Res.string.error_list_title), stringResource(Res.string.error_connection_body), onRetry = onRetry, compact = true)
        items == null -> Unit
        items.isEmpty() -> EmptyState(icon, stringResource(empty))
        else -> content(items)
    }
}

/** A team added this user, by their verified number, before they had the app. */
@Composable
private fun ClaimSuggestion(player: PlayerSummary, onClaim: () -> Unit) {
    DesiCard(Modifier.fillMaxWidth()) {
        IconTile(DesiIcons.Badge)
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
            ErrorBanner(stringResource(Res.string.profile_delete_failed))
        }
        PrimaryButton(stringResource(Res.string.profile_delete_confirm), onClick = onDelete, loading = state.saving, modifier = Modifier.fillMaxWidth())
        GhostButton(stringResource(Res.string.common_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

private val ProfileTab.label: StringResource
    get() = when (this) {
        ProfileTab.ABOUT -> Res.string.profile_tab_about
        ProfileTab.STATS -> Res.string.profile_tab_stats
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
