package com.harsh.desicrics.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.common_empty_value
import com.harsh.desicrics.resources.common_joined
import com.harsh.desicrics.resources.profile_batting
import com.harsh.desicrics.resources.profile_bowling
import com.harsh.desicrics.resources.profile_edit
import com.harsh.desicrics.resources.profile_email
import com.harsh.desicrics.resources.profile_mobile
import com.harsh.desicrics.resources.profile_sign_out
import com.harsh.desicrics.resources.profile_title
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileScreen(
    profile: UserProfile,
    onEdit: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    DesiScaffold(title = stringResource(Res.string.profile_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.screen, vertical = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s6),
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

            Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                PrimaryButton(stringResource(Res.string.profile_edit), onClick = onEdit, modifier = Modifier.fillMaxWidth())
                GhostButton(stringResource(Res.string.profile_sign_out), onClick = onSignOut, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ProfileRow(label: StringResource, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1)) {
        Text(stringResource(label).uppercase(), style = DesiTheme.type.label, color = DesiTheme.colors.muted)
        Text(value, style = DesiTheme.type.bodyLg, color = DesiTheme.colors.ink)
    }
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
    ProfileScreen(PreviewProfile, onEdit = {}, onSignOut = {}, onBack = {})
}

@Preview
@Composable
private fun ProfileDarkPreview() = DesiTheme(darkTheme = true) {
    ProfileScreen(PreviewProfile.copy(city = "", bowlingStyle = BowlingStyle.NONE), onEdit = {}, onSignOut = {}, onBack = {})
}
