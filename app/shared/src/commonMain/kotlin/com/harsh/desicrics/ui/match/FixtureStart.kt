package com.harsh.desicrics.ui.match
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import com.harsh.desicrics.designsystem.component.TagTone
import com.harsh.desicrics.designsystem.component.Tag
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.HeroChip
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.model.AbandonReason
import com.harsh.desicrics.model.MatchEnding
import com.harsh.desicrics.designsystem.component.ErrorBanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.newmatch.SquadSheet
import com.harsh.desicrics.ui.text.formatDayAndTime
import com.harsh.desicrics.ui.tournament.stageText
import org.jetbrains.compose.resources.stringResource

/**
 * A tournament fixture before its first ball: where it sits and when, and for organisers, picking both
 * squads and the toss to start it.
 */
@Composable
internal fun FixturePending(
    match: Match,
    setup: FixtureSetup?,
    onStart: (SquadRequest, SquadRequest, TeamSide, TossDecision) -> Unit,
    onBack: () -> Unit,
    onEnd: (ending: MatchEnding, winner: TeamSide?, reason: AbandonReason?) -> Unit = { _, _, _ -> },
) {
    var ending by remember { mutableStateOf(false) }
    if (ending) EndMatchSheet(match, beforeStart = true, onEnd = { e, w, r -> ending = false; onEnd(e, w, r) }, onDismiss = { ending = false })
    val space = DesiTheme.space
    val colors = DesiTheme.colors
    var squads by remember { mutableStateOf(if (match.fixture == null) TeamSide.entries.associateWith { side ->
        val t = match.team(side)
        SquadRequest(t.name, t.players.map { SquadPlayer(it.name, it.profileId) }, t.teamId,
            t.players.indexOfFirst { it.id == t.captainId }.takeIf { it >= 0 }, t.players.indexOfFirst { it.id == t.keeperId }.takeIf { it >= 0 })
    } else emptyMap()) }
    var picking by remember { mutableStateOf<TeamSide?>(null) }
    var tossWinner by remember { mutableStateOf(TeamSide.A) }
    var tossDecision by remember { mutableStateOf(TossDecision.BAT) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MatchupHero(
            match,
            onBack,
            chips = listOf(
                HeroChip(match.fixture?.let { stageText(it) }.orEmpty(), DesiIcons.Trophy),
                HeroChip(match.scheduledAt?.let { formatDayAndTime(it) } ?: stringResource(Res.string.fixture_not_scheduled), DesiIcons.Calendar),
                HeroChip(match.venue, DesiIcons.Location),
            ),
        )
        Column(Modifier.padding(horizontal = space.screen, vertical = space.s5), verticalArrangement = Arrangement.spacedBy(space.s5)) {
            if (setup?.canStart != true) {
                EmptyState(DesiIcons.Clock, stringResource(Res.string.fixture_waiting))
                return@Column
            }
            Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                SectionHeader(stringResource(Res.string.fixture_squads), icon = DesiIcons.Teams)
                ListCard {
                    TeamSide.entries.forEachIndexed { index, side ->
                        val team = match.team(side)
                        val squad = squads[side]
                        val enabled = match.fixture == null || (if (side == TeamSide.A) setup.teamA else setup.teamB) != null
                        ListRow(
                            title = team.name,
                            subtitle = if (squad == null) stringResource(Res.string.fixture_squad_pick)
                            else pluralStringResource(Res.plurals.new_match_player_count, squad.players.size, squad.players.size),
                            leading = { Avatar(team.name, size = 40) },
                            trailing = {
                                if (squad != null && squad.players.size >= 2) Tag(stringResource(Res.string.fixture_squad_ready_tag), tone = TagTone.BRAND, icon = DesiIcons.CheckCircle)
                                Icon(painterResource(DesiIcons.ChevronRight), null, tint = colors.muted, modifier = Modifier.size(DesiTheme.size.iconSmall))
                            },
                            divider = index > 0,
                            onClick = if (enabled) ({ picking = side }) else null,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                SectionHeader(stringResource(Res.string.new_match_section_toss), icon = DesiIcons.Swap)
                Text(stringResource(Res.string.new_match_toss_won), style = DesiTheme.type.body, color = colors.muted)
                SegmentedControl(TeamSide.entries, selected = tossWinner, label = { match.team(it).name }, onSelect = { tossWinner = it })
                Text(stringResource(Res.string.new_match_toss_chose), style = DesiTheme.type.body, color = colors.muted)
                SegmentedControl(
                    TossDecision.entries,
                    selected = tossDecision,
                    label = { stringResource(if (it == TossDecision.BAT) Res.string.new_match_toss_bat else Res.string.new_match_toss_bowl) },
                    onSelect = { tossDecision = it },
                )
                Row(
                    Modifier.fillMaxWidth().clip(DesiTheme.radius.md).background(colors.brandSoft).padding(space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.s2),
                ) {
                    Icon(painterResource(DesiIcons.Bat), null, tint = colors.brand, modifier = Modifier.size(DesiTheme.size.iconSmall))
                    Text(
                        stringResource(Res.string.toss_first, match.team(if (tossDecision == TossDecision.BAT) tossWinner else tossWinner.other).name),
                        style = DesiTheme.type.bodyStrong,
                        color = colors.brand,
                    )
                }
            }
            if (setup.failed) ErrorBanner(stringResource(Res.string.fixture_start_failed))
            val a = squads[TeamSide.A]
            val b = squads[TeamSide.B]
            PrimaryButton(
                stringResource(Res.string.fixture_start),
                onClick = { if (a != null && b != null) onStart(a, b, tossWinner, tossDecision) },
                enabled = a != null && b != null && a.players.size >= 2 && b.players.size >= 2,
                loading = setup.starting,
                leadingIcon = DesiIcons.Play,
                modifier = Modifier.fillMaxWidth(),
            )
            // No-shows and wash-outs happen: the organiser records them here instead of leaving the fixture hanging.
            ListCard {
                ListRow(
                    title = stringResource(Res.string.end_title_before),
                    subtitle = stringResource(Res.string.end_didnt_happen_hint),
                    leading = { IconTile(DesiIcons.Flag) },
                    onClick = { ending = true },
                )
            }
        }
    }

    picking?.let { side ->
        val team = if (side == TeamSide.A) setup?.teamA else setup?.teamB
        if (team == null || match.fixture == null) {
            val current = squads[side] ?: SquadRequest(match.team(side).name, emptyList(), match.team(side).teamId)
            var players by remember(side) { mutableStateOf(current.players) }
            var name by remember { mutableStateOf("") }
            DesiSheet(current.name, { picking = null }, stringResource(Res.string.fixture_squad_hint)) {
                if (players.isNotEmpty()) ListCard {
                    players.forEachIndexed { i, player ->
                        ListRow(
                            title = player.name,
                            leading = { Avatar(player.name, size = 36) },
                            trailing = {
                                IconButton(onClick = { players = players.filterIndexed { index, _ -> index != i } }) {
                                    Icon(painterResource(DesiIcons.Close), stringResource(Res.string.new_match_remove_player, player.name), tint = colors.muted, modifier = Modifier.size(18.dp))
                                }
                            },
                            divider = i > 0,
                        )
                    }
                }
                val add = { players = players + SquadPlayer(name.trim()); name = "" }
                DesiTextField(
                    name,
                    { name = it.take(60) },
                    stringResource(Res.string.team_add_placeholder_name),
                    leadingIcon = DesiIcons.Person,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (name.isNotBlank() && players.size < 20) add() }),
                )
                TonalButton(stringResource(Res.string.new_match_add_player), add, Modifier.fillMaxWidth(), leadingIcon = DesiIcons.PersonAdd, enabled = name.isNotBlank() && players.size < 20)
                PrimaryButton(stringResource(Res.string.teams_save_action), {
                    fun role(index: Int?) = index?.let { current.players.getOrNull(it) }?.let { players.indexOf(it).takeIf { n -> n >= 0 } }
                    squads = squads + (side to current.copy(players = players, captain = role(current.captain), keeper = role(current.keeper)))
                    picking = null
                }, Modifier.fillMaxWidth())
            }
            return@let
        }
        val current = squads[side]
        SquadSheet(
            team = team,
            initial = current?.players?.mapNotNull { it.profileId }?.toSet()
                ?: team.lastSquad.ifEmpty { team.members.map { it.player.profileId } }.toSet(),
            initialCaptain = current?.captain?.let { current.players[it].profileId },
            initialKeeper = current?.keeper?.let { current.players[it].profileId },
            onDone = { players, captain, keeper ->
                squads = squads + (side to SquadRequest(
                    name = team.name,
                    players = players.map { SquadPlayer(it.name, it.profileId) },
                    teamId = team.id,
                    captain = players.indexOfFirst { it.profileId == captain }.takeIf { it >= 0 },
                    keeper = players.indexOfFirst { it.profileId == keeper }.takeIf { it >= 0 },
                ))
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}
