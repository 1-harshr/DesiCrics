package com.harsh.desicrics.ui.newmatch
import com.harsh.desicrics.designsystem.component.BallSpinner
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.component.EmptyState

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.PlayerRow
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.teams.TeamRow
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.stringResource

/** The caller's saved teams; null while they load, or if loading [failed]. */
@Composable
internal fun TeamPickerSheet(
    teams: List<TeamSummary>?,
    failed: Boolean,
    onRetry: () -> Unit,
    search: suspend (String) -> List<TeamSummary>,
    onPick: (TeamSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    DesiSheet(stringResource(Res.string.new_match_pick_team), onDismiss) {
        var query by remember { mutableStateOf("") }
        var hits by remember(query) { mutableStateOf<List<TeamSummary>?>(null) }
        var searchFailed by remember(query) { mutableStateOf(false) }
        val searching = query.trim().length >= 2
        androidx.compose.runtime.LaunchedEffect(query) {
            if (searching) try { kotlinx.coroutines.delay(250); hits = search(query) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { searchFailed = true }
        }
        com.harsh.desicrics.designsystem.component.DesiTextField(query, { query = it }, stringResource(Res.string.setup_find))
        val results = if (searching) hits else teams
        when {
            searching && searchFailed -> ErrorBanner(stringResource(Res.string.teams_load_failed))
            !searching && failed -> ErrorBanner(stringResource(Res.string.teams_load_failed), action = stringResource(Res.string.feed_retry), onAction = onRetry)
            results == null -> BallSpinner()
            results.isEmpty() -> EmptyState(DesiIcons.Shield, stringResource(Res.string.new_match_no_teams))
            else -> results.forEach { TeamRow(it, onClick = { onPick(it) }) }
        }
    }
}

/** Tick who's playing from a saved team, then pick the captain and keeper from them. */
@Composable
internal fun SquadSheet(
    team: SavedTeam,
    initial: Set<String>,
    initialCaptain: String?,
    initialKeeper: String?,
    onDone: (players: List<PlayerSummary>, captainId: String?, keeperId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember(team.id) { mutableStateOf(initial) }
    var captain by remember(team.id) { mutableStateOf(initialCaptain) }
    var keeper by remember(team.id) { mutableStateOf(initialKeeper) }
    // In the team's order, so the batting order starts sensible.
    val squad = team.members.map { it.player }.filter { it.profileId in picked }

    DesiSheet(stringResource(Res.string.new_match_squad_title, team.name), onDismiss, stringResource(Res.string.new_match_squad_hint)) {
        Column(Modifier.fillMaxWidth().clip(DesiTheme.radius.md)) {
            team.members.forEach { member ->
                val player = member.player
                val toggle = {
                    picked = if (player.profileId in picked) picked - player.profileId else picked + player.profileId
                    if (player.profileId !in picked) {
                        if (captain == player.profileId) captain = null
                        if (keeper == player.profileId) keeper = null
                    }
                }
                PlayerRow(
                    name = player.name,
                    subtitle = player.role?.let { stringResource(it.label) },
                    avatar = { PhotoAvatar(player.name, player.photoUrl) },
                    trailing = {
                        Checkbox(
                            checked = player.profileId in picked,
                            onCheckedChange = { toggle() },
                            colors = CheckboxDefaults.colors(checkedColor = DesiTheme.colors.brand),
                        )
                    },
                    onClick = toggle,
                )
            }
        }
        if (squad.isNotEmpty()) {
            SectionHeader(stringResource(Res.string.new_match_squad_captain), Modifier.padding(top = DesiTheme.space.s2), icon = DesiIcons.Star)
            ChoiceChips(squad, selected = squad.firstOrNull { it.profileId == captain }, label = { it.name }, onSelect = { captain = it.profileId })
            SectionHeader(stringResource(Res.string.new_match_squad_keeper), Modifier.padding(top = DesiTheme.space.s2), icon = DesiIcons.Glove)
            ChoiceChips(squad, selected = squad.firstOrNull { it.profileId == keeper }, label = { it.name }, onSelect = { keeper = it.profileId })
        }
        PrimaryButton(
            stringResource(Res.string.new_match_squad_done, squad.size),
            onClick = { onDone(squad, captain, keeper) },
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
