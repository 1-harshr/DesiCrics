package com.harsh.desicrics.ui.profile
import com.harsh.desicrics.designsystem.component.ErrorBanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.api.ProfileError
import com.harsh.desicrics.designsystem.component.BrandHeader
import com.harsh.desicrics.designsystem.component.BrandStatusBar
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.FullScreenLoading
import com.harsh.desicrics.designsystem.component.OptionCard
import com.harsh.desicrics.designsystem.component.PhoneNumberField
import com.harsh.desicrics.designsystem.component.PlayerCard
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.system.LightStatusBarIcons
import com.harsh.desicrics.ui.system.rememberImagePicker
import com.harsh.desicrics.ui.text.label
import com.harsh.desicrics.ui.text.icon
import org.jetbrains.compose.resources.StringResource
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
    val pickPhoto = rememberImagePicker(viewModel::uploadPhoto)
    LightStatusBarIcons()
    EditProfileContent(
        state = state,
        isOnboarding = isOnboarding,
        onUpdate = viewModel::update,
        onSave = { viewModel.save(onDone) },
        onBack = onBack,
        // A photo needs a profile to belong to, so it waits until after onboarding.
        onChangePhoto = if (isOnboarding) null else pickPhoto,
    )
}

/** How far the player card hangs below the green header. */
private val CardOverhang = 64.dp
private const val PHONE_DIGITS = 10

/**
 * The profile form without its ViewModel, so previews and tests can render any state.
 *
 * A green header holds a live [PlayerCard] that fills in as the form changes, so people see what
 * teammates will see. The save button stays pinned above the keyboard.
 */
@Composable
fun EditProfileContent(
    state: ProfileFormState,
    isOnboarding: Boolean,
    onUpdate: ((ProfileFormState) -> ProfileFormState) -> Unit,
    onSave: () -> Unit,
    onBack: (() -> Unit)?,
    onChangePhoto: (() -> Unit)? = null,
) {
    val colors = DesiTheme.colors
    if (state.loading) {
        FullScreenLoading(Modifier.background(colors.bg))
        return
    }
    Column(Modifier.fillMaxSize().background(colors.bg).imePadding()) {
        BrandStatusBar()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Header(state, isOnboarding, onBack)
            Column(
                Modifier.padding(horizontal = DesiTheme.space.screen, vertical = DesiTheme.space.s6),
                verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s8),
            ) {
                if (onChangePhoto != null) PhotoSection(state, onChangePhoto)
                AboutSection(state, onUpdate)
                GameSection(state, onUpdate)
            }
        }
        SaveBar(state, isOnboarding, onSave)
    }
}

@Composable
private fun PhotoSection(state: ProfileFormState, onChangePhoto: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s4)) {
        PhotoAvatar(state.name, state.photoUrl, size = 72)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1)) {
            TonalButton(stringResource(Res.string.profile_photo_change), onClick = onChangePhoto, loading = state.uploadingPhoto)
            if (state.photoFailed) ErrorBanner(stringResource(Res.string.profile_photo_failed))
        }
    }
}

@Composable
private fun Header(state: ProfileFormState, isOnboarding: Boolean, onBack: (() -> Unit)?) {
    val title = if (isOnboarding) Res.string.profile_setup_title else Res.string.profile_edit_title
    val role = stringResource(state.role.label)
    val city = state.city.trim()
    val tags = buildList {
        add(stringResource(state.battingStyle.label))
        if (state.bowlingStyle != BowlingStyle.NONE) add(stringResource(state.bowlingStyle.label))
    }
    BrandHeader(
        title = stringResource(title),
        subtitle = stringResource(Res.string.profile_setup_intro),
        onBack = onBack,
        overhang = CardOverhang,
    ) {
        PlayerCard(
            name = state.name,
            namePlaceholder = stringResource(Res.string.profile_name_placeholder),
            subtitle = if (city.isEmpty()) role else stringResource(Res.string.common_joined, role, city),
            tags = tags,
            modifier = Modifier.fillMaxWidth().padding(top = DesiTheme.space.s4),
        )
    }
}

@Composable
private fun AboutSection(state: ProfileFormState, onUpdate: ((ProfileFormState) -> ProfileFormState) -> Unit) {
    val prefix = stringResource(Res.string.common_phone_prefix)
    val invalid = (state.error as? ProfileFormError.Invalid)?.error

    Section(Res.string.profile_section_about) {
        DesiTextField(
            value = state.name,
            onValueChange = { value -> onUpdate { it.copy(name = value) } },
            label = stringResource(Res.string.profile_name_label),
            error = invalid?.takeIf { it == ProfileError.NAME_MISSING || it == ProfileError.NAME_TOO_LONG }?.let { stringResource(it.label) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            PhoneNumberField(
                value = state.phone.removePrefix(prefix).filter { it.isDigit() }.take(PHONE_DIGITS),
                onValueChange = { digits -> onUpdate { it.copy(phone = if (digits.isEmpty()) "" else prefix + digits) } },
                label = stringResource(Res.string.profile_phone_label),
                countryCode = prefix,
                countryLabel = stringResource(Res.string.common_phone_country),
                placeholder = stringResource(Res.string.common_phone_placeholder),
                length = PHONE_DIGITS,
                enabled = !state.phoneVerified,
                error = invalid?.takeIf { it == ProfileError.PHONE_INVALID }?.let { stringResource(it.label) },
            )
            Text(
                stringResource(if (state.phoneVerified) Res.string.profile_phone_verified else Res.string.profile_phone_help),
                style = DesiTheme.type.caption,
                color = DesiTheme.colors.muted,
            )
        }
        DesiTextField(
            value = state.city,
            onValueChange = { value -> onUpdate { it.copy(city = value) } },
            label = stringResource(Res.string.profile_city_label),
            leadingIcon = DesiIcons.Location,
            error = invalid?.takeIf { it == ProfileError.CITY_TOO_LONG }?.let { stringResource(it.label) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
    }
}

@Composable
private fun GameSection(state: ProfileFormState, onUpdate: ((ProfileFormState) -> ProfileFormState) -> Unit) {
    Section(Res.string.profile_section_game) {
        SectionHeader(stringResource(Res.string.profile_role_section), icon = DesiIcons.Person)
        RoleGrid(state.role) { role -> onUpdate { it.copy(role = role) } }

        SectionHeader(stringResource(Res.string.profile_batting_section), Modifier.padding(top = DesiTheme.space.s2), icon = DesiIcons.Bat)
        SegmentedControl(
            options = BattingStyle.entries,
            selected = state.battingStyle,
            label = { stringResource(it.label) },
            onSelect = { style -> onUpdate { it.copy(battingStyle = style) } },
        )

        SectionHeader(stringResource(Res.string.profile_bowling_section), Modifier.padding(top = DesiTheme.space.s2), icon = DesiIcons.Ball)
        BowlingPicker(state.bowlingStyle) { style -> onUpdate { it.copy(bowlingStyle = style) } }
    }
}

/** The four roles as a 2×2 grid of cards. */
@Composable
private fun RoleGrid(selected: PlayerRole, onSelect: (PlayerRole) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        PlayerRole.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
                row.forEach { role ->
                    OptionCard(
                        title = stringResource(role.label),
                        description = stringResource(role.description),
                        icon = role.icon,
                        selected = role == selected,
                        onClick = { onSelect(role) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Pace or spin, then the arm, then the type; later steps only appear once they apply. */
@Composable
private fun BowlingPicker(style: BowlingStyle, onSelect: (BowlingStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        SegmentedControl(
            options = BowlingKind.entries,
            selected = style.kind,
            label = { stringResource(it.label) },
            onSelect = { kind -> onSelect(style.withChoice(kind = kind)) },
        )
        AnimatedVisibility(
            visible = style.kind != BowlingKind.NONE,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
                SegmentedControl(
                    options = BowlingArm.entries,
                    selected = style.arm,
                    label = { stringResource(it.label) },
                    onSelect = { arm -> onSelect(style.withChoice(arm = arm)) },
                )
                // Keep the last real choice on screen while "Don't bowl" collapses this.
                val options = bowlingStyles(style.kind, style.arm).ifEmpty { bowlingStyles(BowlingKind.PACE, BowlingArm.RIGHT) }
                SegmentedControl(
                    options = options,
                    selected = style.takeIf { it in options } ?: options.first(),
                    label = { stringResource(it.typeLabel) },
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun Section(title: StringResource, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s4)) {
        Text(stringResource(title), style = DesiTheme.type.titleMd, color = DesiTheme.colors.ink)
        content()
    }
}

/** Pinned under the form, above the keyboard. Errors that don't belong to a field show here. */
@Composable
private fun SaveBar(state: ProfileFormState, isOnboarding: Boolean, onSave: () -> Unit) {
    val colors = DesiTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.line)
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = DesiTheme.space.screen, vertical = DesiTheme.space.s3),
            verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
        ) {
            if (state.error == ProfileFormError.SaveFailed) {
                ErrorBanner(stringResource(Res.string.profile_error_save_failed))
            }
            PrimaryButton(
                text = stringResource(if (isOnboarding) Res.string.profile_finish_setup else Res.string.profile_save),
                onClick = onSave,
                loading = state.saving,
                trailingIcon = if (isOnboarding) DesiIcons.Forward else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val PlayerRole.description: StringResource
    get() = when (this) {
        PlayerRole.BATTER -> Res.string.profile_role_batter_description
        PlayerRole.BOWLER -> Res.string.profile_role_bowler_description
        PlayerRole.ALL_ROUNDER -> Res.string.profile_role_all_rounder_description
        PlayerRole.WICKET_KEEPER -> Res.string.profile_role_wicket_keeper_description
    }

private val BowlingKind.label: StringResource
    get() = when (this) {
        BowlingKind.NONE -> Res.string.profile_bowling_none
        BowlingKind.PACE -> Res.string.profile_bowling_pace
        BowlingKind.SPIN -> Res.string.profile_bowling_spin
    }

private val BowlingArm.label: StringResource
    get() = when (this) {
        BowlingArm.RIGHT -> Res.string.profile_bowling_right_arm
        BowlingArm.LEFT -> Res.string.profile_bowling_left_arm
    }

/** The short type name once pace/spin and the arm are already chosen: "Fast", "Off spin". */
private val BowlingStyle.typeLabel: StringResource
    get() = when (this) {
        BowlingStyle.NONE -> Res.string.profile_bowling_none
        BowlingStyle.RIGHT_ARM_FAST, BowlingStyle.LEFT_ARM_FAST -> Res.string.profile_bowling_fast
        BowlingStyle.RIGHT_ARM_MEDIUM, BowlingStyle.LEFT_ARM_MEDIUM -> Res.string.profile_bowling_medium
        BowlingStyle.RIGHT_ARM_OFF_SPIN -> Res.string.profile_bowling_off_spin
        BowlingStyle.RIGHT_ARM_LEG_SPIN -> Res.string.profile_bowling_leg_spin
        BowlingStyle.LEFT_ARM_ORTHODOX -> Res.string.profile_bowling_orthodox
        BowlingStyle.LEFT_ARM_WRIST_SPIN -> Res.string.profile_bowling_wrist_spin
    }

// Previews: sample player data only; all labels come from string resources.

private val PreviewForm = ProfileFormState(
    loading = false,
    name = "Rohan Iyer",
    phone = "+919876543210",
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
private fun OnboardingVerifiedPhonePreview() = DesiTheme(darkTheme = false) {
    EditProfileContent(
        ProfileFormState(loading = false, phone = "+919876543210", phoneVerified = true),
        isOnboarding = true,
        onUpdate = {},
        onSave = {},
        onBack = null,
    )
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
