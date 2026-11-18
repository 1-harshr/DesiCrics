package com.harsh.desicrics.ui.tournament

import androidx.compose.runtime.Composable
import com.harsh.desicrics.model.FixtureInfo
import com.harsh.desicrics.model.Stage
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal val TournamentFormat.label: StringResource
    get() = when (this) {
        TournamentFormat.KNOCKOUT -> Res.string.format_knockout
        TournamentFormat.ROUND_ROBIN -> Res.string.format_round_robin
        TournamentFormat.GROUPS_KNOCKOUT -> Res.string.format_groups_knockout
        TournamentFormat.BOX_LEAGUE -> Res.string.format_box_league
        TournamentFormat.DOUBLE_OUT -> Res.string.format_double_out
        TournamentFormat.BEST_OF_THREE -> Res.string.format_best_of_three
        TournamentFormat.CUSTOM -> Res.string.format_custom
    }

internal val TournamentFormat.hint: StringResource
    get() = when (this) {
        TournamentFormat.KNOCKOUT -> Res.string.format_knockout_hint
        TournamentFormat.ROUND_ROBIN -> Res.string.format_round_robin_hint
        TournamentFormat.GROUPS_KNOCKOUT -> Res.string.format_groups_knockout_hint
        TournamentFormat.BOX_LEAGUE -> Res.string.format_box_league_hint
        TournamentFormat.DOUBLE_OUT -> Res.string.format_double_out_hint
        TournamentFormat.BEST_OF_THREE -> Res.string.format_best_of_three_hint
        TournamentFormat.CUSTOM -> Res.string.format_custom_hint
    }

/** "Semi-final", "Round 3", "Group B", "Match 2". */
@Composable
fun stageText(fixture: FixtureInfo): String = when (fixture.stage) {
    Stage.LEAGUE -> fixture.group?.let { stringResource(Res.string.tournament_group, groupName(it)) } ?: stringResource(Res.string.stage_league)
    Stage.ROUND -> stringResource(Res.string.stage_round, fixture.round)
    Stage.QUARTERFINAL -> stringResource(Res.string.stage_quarterfinal)
    Stage.SEMIFINAL -> stringResource(Res.string.stage_semifinal)
    Stage.FINAL -> stringResource(Res.string.stage_final)
    Stage.MATCH -> stringResource(Res.string.stage_match, fixture.round)
}

/** Groups are lettered: 0 is "A". */
internal fun groupName(group: Int): String = ('A' + group).toString()
