package com.harsh.desicrics.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.harsh.desicrics.designsystem.component.BallSpinner
import com.harsh.desicrics.designsystem.component.BrandHeader
import com.harsh.desicrics.designsystem.component.BrandStatusBar
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.FullScreenLoading
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.PillButton
import com.harsh.desicrics.designsystem.component.PlayerCard
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.profile.AboutSection
import com.harsh.desicrics.ui.profile.EditProfileViewModel
import com.harsh.desicrics.ui.profile.GameSection
import com.harsh.desicrics.ui.profile.ProfileFormError
import com.harsh.desicrics.ui.profile.ProfileCityField
import com.harsh.desicrics.ui.profile.ProfileFormState
import com.harsh.desicrics.ui.system.LightStatusBarIcons
import com.harsh.desicrics.ui.system.rememberImagePicker
import com.harsh.desicrics.ui.system.rememberNotificationPermission
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * First run after sign-up, one question per screen: the profile over three steps (saved on leaving the
 * third), then a photo, claiming past matches, a team, people to follow and notifications, each of
 * which can be skipped. [joinCode] comes from a team link the app was opened with; [onJoinedFromLink]
 * clears that link once it's used here. The view models are keyed to the account, so signing out and
 * up again as someone else starts afresh.
 */
@Composable
fun OnboardingScreen(uid: String, joinCode: String?, onJoinedFromLink: () -> Unit, onFinished: () -> Unit) {
    val profile = koinViewModel<EditProfileViewModel>(key = "onboarding-profile-$uid")
    val viewModel = koinViewModel<OnboardingViewModel>(key = "onboarding-$uid")
    val form by profile.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickPhoto = rememberImagePicker(profile::uploadPhoto)
    // Whatever they answer, the step is done.
    val askNotifications = rememberNotificationPermission { viewModel.next() }
    LaunchedEffect(askNotifications == null) { viewModel.setNeedsNotifications(askNotifications != null) }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    LightStatusBarIcons()
    OnboardingContent(
        form = form,
        state = state,
        joinCode = joinCode,
        actions = OnboardingActions(
            onUpdate = profile::update,
            onContinue = {
                when (state.step) {
                    OnboardingStep.ABOUT, OnboardingStep.CITY -> if (profile.check()) viewModel.next()
                    OnboardingStep.GAME -> profile.save {
                        viewModel.loadSuggestions(profile.state.value.city)
                        viewModel.next()
                    }
                    OnboardingStep.NOTIFY -> askNotifications?.invoke() ?: viewModel.next()
                    else -> viewModel.next()
                }
            },
            onSkip = viewModel::next,
            onBack = viewModel::back,
            onPickPhoto = pickPhoto,
            onClaim = viewModel::claim,
            onClaimCode = viewModel::claimByCode,
            onJoin = { code -> viewModel.join(code) { if (code == joinCode) onJoinedFromLink() } },
            onCreateTeam = { name -> viewModel.createTeam(name, form.city) },
            onSearchTeams = viewModel::searchTeams,
            onToggleRequest = viewModel::toggleJoinRequest,
            onToggleFollow = viewModel::toggleFollow,
        ),
    )
}

class OnboardingActions(
    val onUpdate: ((ProfileFormState) -> ProfileFormState) -> Unit = {},
    val onContinue: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onClaim: (profileId: String) -> Unit = {},
    val onClaimCode: (code: String) -> Unit = {},
    val onJoin: (code: String) -> Unit = {},
    val onCreateTeam: (name: String) -> Unit = {},
    val onSearchTeams: (query: String) -> Unit = {},
    val onToggleRequest: (teamId: String) -> Unit = {},
    val onToggleFollow: (Suggestion) -> Unit = {},
)

/** Onboarding without its view models, so previews and tests can render any step. */
@Composable
fun OnboardingContent(form: ProfileFormState, state: OnboardingUiState, actions: OnboardingActions, joinCode: String? = null) {
    val colors = DesiTheme.colors
    if (form.loading) {
        FullScreenLoading(Modifier.background(colors.bg))
        return
    }
    val steps = state.steps
    val index = steps.indexOf(state.step).coerceAtLeast(0)
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = index > 0, onBackCompleted = actions.onBack)

    Column(Modifier.fillMaxSize().background(colors.bg).imePadding()) {
        BrandStatusBar()
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                // Forward slides in from the right, back from the left.
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                val spec = tween<androidx.compose.ui.unit.IntOffset>(DesiTheme.motion.BASE_MS * 2, easing = DesiTheme.motion.standard)
                (slideInHorizontally(spec) { it / 4 * direction } + fadeIn(tween(DesiTheme.motion.BASE_MS)))
                    .togetherWith(slideOutHorizontally(spec) { -it / 4 * direction } + fadeOut(tween(DesiTheme.motion.QUICK_MS)))
            },
            label = "onboarding step",
            modifier = Modifier.weight(1f),
        ) { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                BrandHeader(
                    title = stringResource(step.title),
                    subtitle = if (step == OnboardingStep.FOLLOW) {
                        stringResource(Res.string.onboarding_follow_subtitle, form.city.trim())
                    } else {
                        stringResource(step.subtitle)
                    },
                    onBack = if (index > 0) actions.onBack else null,
                ) { StepProgress(steps.indexOf(step).coerceAtLeast(0), steps.size) }
                Column(
                    Modifier.padding(horizontal = DesiTheme.space.screen, vertical = DesiTheme.space.s6),
                    verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s6),
                ) {
                    when (step) {
                        OnboardingStep.ABOUT -> AboutSection(form, actions.onUpdate, titled = false, withCity = false)
                        OnboardingStep.CITY -> ProfileCityField(form, actions.onUpdate, popularWhenEmpty = true)
                        OnboardingStep.GAME -> GameStep(form, actions)
                        OnboardingStep.PHOTO -> PhotoStep(form, actions)
                        OnboardingStep.CLAIM -> ClaimStep(state, actions)
                        OnboardingStep.TEAM -> TeamStep(state, actions, joinCode)
                        OnboardingStep.FOLLOW -> FollowStep(state, actions)
                        OnboardingStep.NOTIFY -> NotifyStep()
                    }
                }
            }
        }
        BottomBar(form, state, isLast = index == steps.lastIndex, actions = actions)
    }
}

/** "Step 2 of 6" over one bar per step, done and current ones filled. */
@Composable
private fun StepProgress(index: Int, total: Int) {
    val colors = DesiTheme.colors
    Column(Modifier.padding(top = DesiTheme.space.s2), verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            repeat(total) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(DesiTheme.radius.full)
                        .background(colors.onBrandStrong.copy(alpha = if (i <= index) 1f else 0.25f)),
                )
            }
        }
        Text(
            stringResource(Res.string.onboarding_step, index + 1, total),
            style = DesiTheme.type.caption,
            color = colors.onBrandStrong.copy(alpha = 0.78f),
        )
    }
}

/** The player card fills in above the choices, so people see what teammates will. */
@Composable
private fun GameStep(form: ProfileFormState, actions: OnboardingActions) {
    val role = stringResource(form.role.label)
    val city = form.city.trim()
    val tags = buildList {
        add(stringResource(form.battingStyle.label))
        if (form.bowlingStyle != BowlingStyle.NONE) add(stringResource(form.bowlingStyle.label))
    }
    PlayerCard(
        name = form.name,
        namePlaceholder = stringResource(Res.string.profile_name_placeholder),
        subtitle = if (city.isEmpty()) role else stringResource(Res.string.common_joined, role, city),
        tags = tags,
        modifier = Modifier.fillMaxWidth(),
    )
    GameSection(form, actions.onUpdate, titled = false)
}

@Composable
private fun PhotoStep(form: ProfileFormState, actions: OnboardingActions) {
    Column(
        Modifier.fillMaxWidth().padding(top = DesiTheme.space.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s5),
    ) {
        PhotoAvatar(form.name, form.photoUrl, size = 144, modifier = Modifier.clickable(onClick = actions.onPickPhoto))
        Text(form.name.trim(), style = DesiTheme.type.titleLg, color = DesiTheme.colors.ink, textAlign = TextAlign.Center)
        TonalButton(
            stringResource(if (form.photoUrl == null) Res.string.onboarding_photo_choose else Res.string.profile_photo_change),
            onClick = actions.onPickPhoto,
            leadingIcon = DesiIcons.Edit,
            loading = form.uploadingPhoto,
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.photoFailed) ErrorBanner(stringResource(Res.string.profile_photo_failed))
    }
}

@Composable
private fun ClaimStep(state: OnboardingUiState, actions: OnboardingActions) {
    val claimed = state.claimed
    if (claimed != null) {
        DoneCard(stringResource(Res.string.onboarding_claim_done, claimed.name))
        return
    }
    ListCard {
        state.claimable.orEmpty().forEachIndexed { i, player ->
            ListRow(
                title = player.name,
                subtitle = player.city.ifBlank { null },
                leading = { PhotoAvatar(player.name, player.photoUrl) },
                trailing = { PillButton(stringResource(Res.string.profile_claimable_action), { actions.onClaim(player.profileId) }, enabled = !state.working) },
                divider = i > 0,
            )
        }
    }
    var code by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        Text(stringResource(Res.string.onboarding_claim_code_hint), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        DesiTextField(
            value = code,
            onValueChange = { new -> code = new.filter { it.isLetterOrDigit() }.uppercase().take(8) },
            label = stringResource(Res.string.profile_claim_code),
            error = stringResource(Res.string.profile_claim_failed).takeIf { state.claimFailed },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        )
        TonalButton(
            stringResource(Res.string.profile_claim_action),
            onClick = { actions.onClaimCode(code) },
            enabled = code.length >= 6 && !state.working,
            loading = state.working,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Find the team by name and ask to join, or create it if it isn't there. A code still works, behind a
 * link, and is filled in and showing when the app was opened from a team link.
 */
@Composable
private fun TeamStep(state: OnboardingUiState, actions: OnboardingActions, joinCode: String?) {
    val teamName = state.teamName
    if (teamName != null) {
        DoneCard(stringResource(Res.string.onboarding_team_done, teamName))
        return
    }
    val query = state.teamQuery.trim()
    DesiTextField(
        value = state.teamQuery,
        onValueChange = { actions.onSearchTeams(it.take(60)) },
        label = stringResource(Res.string.onboarding_team_search),
        leadingIcon = DesiIcons.Search,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
    )
    if (state.requestFailed || state.createFailed) ErrorBanner(stringResource(Res.string.teams_action_failed))
    if (query.length >= 2) {
        val results = state.teamResults.orEmpty()
        val noMatch = state.teamResults != null && results.isEmpty()
        ListCard {
            results.forEachIndexed { i, team ->
                val asked = team.id in state.requestedTeams
                ListRow(
                    title = team.name,
                    subtitle = listOf(team.city, pluralStringResource(Res.plurals.teams_member_count, team.memberCount, team.memberCount))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    leading = { PhotoAvatar(team.name, team.logoUrl) },
                    trailing = {
                        PillButton(
                            stringResource(if (asked) Res.string.onboarding_team_requested else Res.string.onboarding_team_ask),
                            { actions.onToggleRequest(team.id) },
                            selected = asked,
                            enabled = !state.working,
                        )
                    },
                    divider = i > 0,
                )
            }
            if (noMatch) {
                ListRow(stringResource(Res.string.onboarding_team_none, query), leading = { IconTile(DesiIcons.SearchOff) })
            }
            ListRow(
                title = stringResource(Res.string.onboarding_team_create, query),
                subtitle = stringResource(Res.string.onboarding_team_create_hint),
                leading = { IconTile(DesiIcons.GroupAdd) },
                divider = results.isNotEmpty() || noMatch,
                onClick = if (state.working) null else ({ actions.onCreateTeam(query) }),
            )
        }
    }
    if (state.requestedTeams.isNotEmpty()) {
        Text(stringResource(Res.string.onboarding_team_requested_hint), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
    }
    CodeEntry(state, actions, joinCode)
}

@Composable
private fun CodeEntry(state: OnboardingUiState, actions: OnboardingActions, joinCode: String?) {
    var open by rememberSaveable { mutableStateOf(joinCode != null) }
    var code by rememberSaveable { mutableStateOf(joinCode.orEmpty()) }
    if (!open) {
        GhostButton(stringResource(Res.string.onboarding_team_have_code), onClick = { open = true }, leadingIcon = DesiIcons.QrCode, modifier = Modifier.fillMaxWidth())
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        DesiTextField(
            value = code,
            onValueChange = { new -> code = new.filter { it.isLetterOrDigit() }.uppercase().take(8) },
            label = stringResource(Res.string.teams_join_code),
            leadingIcon = DesiIcons.QrCode,
            error = stringResource(Res.string.teams_join_failed).takeIf { state.teamFailed },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        )
        TonalButton(
            stringResource(Res.string.teams_join_action),
            onClick = { actions.onJoin(code) },
            leadingIcon = DesiIcons.PersonAdd,
            enabled = code.length >= 6 && !state.working,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FollowStep(state: OnboardingUiState, actions: OnboardingActions) {
    val suggestions = state.suggestions
    if (suggestions == null) {
        Box(Modifier.fillMaxWidth().padding(DesiTheme.space.s8), contentAlignment = Alignment.Center) {
            BallSpinner(size = 28.dp)
        }
        return
    }
    FollowGroup(Res.string.onboarding_follow_teams, DesiIcons.Teams, suggestions.filter { it.type == FollowType.TEAM }, actions)
    FollowGroup(Res.string.onboarding_follow_players, DesiIcons.Person, suggestions.filter { it.type == FollowType.PLAYER }, actions)
}

@Composable
private fun FollowGroup(title: StringResource, icon: org.jetbrains.compose.resources.DrawableResource, items: List<Suggestion>, actions: OnboardingActions) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        SectionHeader(stringResource(title), icon = icon)
        ListCard {
            items.forEachIndexed { i, item ->
                ListRow(
                    title = item.name,
                    subtitle = item.subtitle.ifBlank { null },
                    leading = { PhotoAvatar(item.name, item.photoUrl) },
                    trailing = {
                        PillButton(stringResource(if (item.following) Res.string.following else Res.string.follow), { actions.onToggleFollow(item) }, selected = item.following)
                    },
                    divider = i > 0,
                )
            }
        }
    }
}

/** What turning notifications on gets them, before the system asks. */
@Composable
private fun NotifyStep() {
    ListCard {
        ListRow(stringResource(Res.string.onboarding_notify_start), leading = { IconTile(DesiIcons.Play) })
        ListRow(stringResource(Res.string.onboarding_notify_live), leading = { IconTile(DesiIcons.Notifications) }, divider = true)
        ListRow(stringResource(Res.string.onboarding_notify_result), leading = { IconTile(DesiIcons.Trophy) }, divider = true)
    }
}

@Composable
private fun DoneCard(text: String) {
    DesiCard(Modifier.fillMaxWidth()) {
        IconTile(DesiIcons.CheckCircle)
        Text(text, style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
    }
}

/**
 * Pinned under the step, above the keyboard. Required steps only go forward; optional ones offer Skip
 * until something is done there. The last step's button finishes.
 */
@Composable
private fun BottomBar(form: ProfileFormState, state: OnboardingUiState, isLast: Boolean, actions: OnboardingActions) {
    val colors = DesiTheme.colors
    val step = state.step
    val done = when (step) {
        OnboardingStep.ABOUT, OnboardingStep.GAME -> true
        OnboardingStep.CITY -> form.city.isNotBlank()
        OnboardingStep.PHOTO -> form.photoUrl != null
        OnboardingStep.CLAIM -> state.claimed != null
        OnboardingStep.TEAM -> state.teamName != null || state.requestedTeams.isNotEmpty()
        OnboardingStep.FOLLOW -> state.suggestions.orEmpty().any { it.following }
        OnboardingStep.NOTIFY -> true
    }
    val primary = when {
        step == OnboardingStep.NOTIFY -> Res.string.onboarding_notify_allow
        isLast -> Res.string.profile_finish_setup
        else -> Res.string.onboarding_continue
    }
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.line)
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = DesiTheme.space.screen, vertical = DesiTheme.space.s3),
            verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
        ) {
            if (step == OnboardingStep.GAME && form.error == ProfileFormError.SaveFailed) {
                ErrorBanner(stringResource(Res.string.profile_error_save_failed))
            }
            if (done) {
                PrimaryButton(
                    text = stringResource(primary),
                    onClick = actions.onContinue,
                    loading = form.saving,
                    trailingIcon = if (step == OnboardingStep.NOTIFY) null else DesiIcons.Forward,
                    leadingIcon = if (step == OnboardingStep.NOTIFY) DesiIcons.Notifications else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (step == OnboardingStep.NOTIFY) {
                GhostButton(stringResource(Res.string.onboarding_notify_later), onClick = actions.onSkip, modifier = Modifier.fillMaxWidth())
            } else if (!done) {
                GhostButton(stringResource(Res.string.onboarding_skip), onClick = actions.onSkip, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private val OnboardingStep.title: StringResource
    get() = when (this) {
        OnboardingStep.ABOUT -> Res.string.onboarding_about_title
        OnboardingStep.CITY -> Res.string.onboarding_city_title
        OnboardingStep.GAME -> Res.string.onboarding_game_title
        OnboardingStep.PHOTO -> Res.string.onboarding_photo_title
        OnboardingStep.CLAIM -> Res.string.onboarding_claim_title
        OnboardingStep.TEAM -> Res.string.onboarding_team_title
        OnboardingStep.FOLLOW -> Res.string.onboarding_follow_title
        OnboardingStep.NOTIFY -> Res.string.onboarding_notify_title
    }

private val OnboardingStep.subtitle: StringResource
    get() = when (this) {
        OnboardingStep.ABOUT -> Res.string.onboarding_about_subtitle
        OnboardingStep.CITY -> Res.string.onboarding_city_subtitle
        OnboardingStep.GAME -> Res.string.onboarding_game_subtitle
        OnboardingStep.PHOTO -> Res.string.onboarding_photo_subtitle
        OnboardingStep.CLAIM -> Res.string.onboarding_claim_subtitle
        OnboardingStep.TEAM -> Res.string.onboarding_team_subtitle
        OnboardingStep.FOLLOW -> Res.string.onboarding_follow_subtitle
        OnboardingStep.NOTIFY -> Res.string.onboarding_notify_subtitle
    }

// Previews: sample player data only; all labels come from string resources.

internal val PreviewOnboardingForm = ProfileFormState(
    loading = false,
    name = "Rohan Iyer",
    phone = "+919876543210",
    phoneVerified = true,
    city = "Mumbai",
    role = PlayerRole.ALL_ROUNDER,
    bowlingStyle = BowlingStyle.RIGHT_ARM_OFF_SPIN,
)

internal val PreviewOnboardingState = OnboardingUiState(
    claimable = listOf(PlayerSummary("p1", "Rohan I.", "Mumbai", isPlaceholder = true)),
    suggestions = listOf(
        Suggestion(FollowType.TEAM, "t1", "Andheri Strikers", "Mumbai", null, following = true),
        Suggestion(FollowType.TEAM, "t2", "Powai XI", "Mumbai", null),
        Suggestion(FollowType.PLAYER, "p2", "Aarav Shah", "Mumbai", null),
        Suggestion(FollowType.PLAYER, "p3", "Kabir Mehta", "Mumbai", null),
    ),
    suggestionsCity = "Mumbai",
    teamQuery = "Andheri",
    teamResults = listOf(TeamSummary("t1", "Andheri Strikers", "Mumbai", 14), TeamSummary("t2", "Andheri Royals", "Mumbai", 9)),
    requestedTeams = setOf("t1"),
)

@Composable
private fun StepPreview(step: OnboardingStep, dark: Boolean = false) = DesiTheme(darkTheme = dark) {
    OnboardingContent(PreviewOnboardingForm, PreviewOnboardingState.copy(step = step), OnboardingActions())
}

@Preview @Composable private fun OnboardingAboutPreview() = StepPreview(OnboardingStep.ABOUT)
@Preview @Composable private fun OnboardingCityPreview() = StepPreview(OnboardingStep.CITY)
@Preview @Composable private fun OnboardingGamePreview() = StepPreview(OnboardingStep.GAME)
@Preview @Composable private fun OnboardingGameDarkPreview() = StepPreview(OnboardingStep.GAME, dark = true)
@Preview @Composable private fun OnboardingPhotoPreview() = StepPreview(OnboardingStep.PHOTO)
@Preview @Composable private fun OnboardingClaimPreview() = StepPreview(OnboardingStep.CLAIM)
@Preview @Composable private fun OnboardingTeamPreview() = StepPreview(OnboardingStep.TEAM)
@Preview @Composable private fun OnboardingFollowPreview() = StepPreview(OnboardingStep.FOLLOW)
@Preview @Composable private fun OnboardingNotifyPreview() = StepPreview(OnboardingStep.NOTIFY)
