package com.harsh.desicrics.ui.match

import androidx.compose.foundation.layout.Arrangement
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
) {
    val space = DesiTheme.space
    val colors = DesiTheme.colors
    var squads by remember { mutableStateOf(mapOf<TeamSide, SquadRequest>()) }
    var picking by remember { mutableStateOf<TeamSide?>(null) }
    var tossWinner by remember { mutableStateOf(TeamSide.A) }
    var tossDecision by remember { mutableStateOf(TossDecision.BAT) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = space.screen, vertical = space.s4),
        verticalArrangement = Arrangement.spacedBy(space.s3),
    ) {
        GhostButton(stringResource(Res.string.common_back), onClick = onBack)
        Text(stringResource(Res.string.match_title, match.teamA.name, match.teamB.name), style = DesiTheme.type.titleLg, color = colors.ink)
        val where = listOfNotNull(
            match.fixture?.let { stageText(it) },
            match.scheduledAt?.let { formatDayAndTime(it) } ?: stringResource(Res.string.fixture_not_scheduled),
        )
        Text(where.joinToString(" · "), style = DesiTheme.type.body, color = colors.muted)
        Text(stringResource(Res.string.fixture_waiting), style = DesiTheme.type.body, color = colors.muted)

        if (setup?.canStart != true) return@Column
        DesiCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(Res.string.fixture_start))
            Text(stringResource(Res.string.fixture_start_hint), style = DesiTheme.type.caption, color = colors.muted)
            for (side in TeamSide.entries) {
                val team = match.team(side)
                val squad = squads[side]
                TonalButton(
                    if (squad == null) stringResource(Res.string.fixture_pick_squad, team.name)
                    else stringResource(Res.string.fixture_squad_ready, team.name, squad.players.size),
                    onClick = { picking = side },
                    enabled = (if (side == TeamSide.A) setup.teamA else setup.teamB) != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(stringResource(Res.string.new_match_toss_won), style = DesiTheme.type.body, color = colors.muted)
            SegmentedControl(TeamSide.entries, selected = tossWinner, label = { match.team(it).name }, onSelect = { tossWinner = it })
            Text(stringResource(Res.string.new_match_toss_chose), style = DesiTheme.type.body, color = colors.muted)
            SegmentedControl(
                TossDecision.entries,
                selected = tossDecision,
                label = { stringResource(if (it == TossDecision.BAT) Res.string.new_match_toss_bat else Res.string.new_match_toss_bowl) },
                onSelect = { tossDecision = it },
            )
            if (setup.failed) Text(stringResource(Res.string.fixture_start_failed), style = DesiTheme.type.body, color = colors.event.wicket)
            val a = squads[TeamSide.A]
            val b = squads[TeamSide.B]
            PrimaryButton(
                stringResource(Res.string.fixture_start),
                onClick = { if (a != null && b != null) onStart(a, b, tossWinner, tossDecision) },
                enabled = a != null && b != null,
                loading = setup.starting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    picking?.let { side ->
        val team: SavedTeam = (if (side == TeamSide.A) setup?.teamA else setup?.teamB) ?: return@let
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
