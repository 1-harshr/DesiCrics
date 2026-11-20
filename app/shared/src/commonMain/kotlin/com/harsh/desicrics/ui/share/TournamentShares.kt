package com.harsh.desicrics.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.harsh.desicrics.model.FixtureInfo
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Stage
import com.harsh.desicrics.model.StatLeader
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.tournament.TournamentEngine
import com.harsh.desicrics.ui.stats.mvpText
import com.harsh.desicrics.ui.text.oversText
import com.harsh.desicrics.ui.tournament.groupName
import com.harsh.desicrics.ui.tournament.label
import com.harsh.desicrics.ui.tournament.stageText
import com.harsh.desicrics.util.format2
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * What a tournament offers the studio, most useful first: before the start the announcement; while it's on
 * the next fixtures, the table and the knockouts; once there's a champion, the player of the tournament
 * and the road to the final.
 */
fun tournamentShareContents(details: TournamentDetails, zone: TimeZone = TimeZone.currentSystemDefault()): List<ShareContent> {
    val announcement = AnnouncementShare(details)
    if (!details.tournament.started) return listOf(announcement)
    val fixtures = nextMatchday(details.fixtures, zone)?.let { FixturesShare(details.tournament, it) }
    val table = TableShare(details).takeIf { it.lines.isNotEmpty() }
    val bracket = bracketRounds(details).takeIf { it.isNotEmpty() }?.let { BracketShare(details, it) }
    val player = details.playerOfTournament?.let { PlayerOfTournamentShare(details, it) }
    return if (details.championId != null) listOfNotNull(player, bracket, table, fixtures, announcement)
    else listOfNotNull(fixtures, table, bracket, player, announcement)
}

/**
 * A template for one look of a poster that may run over several pages of [perPage] items each, spread
 * evenly so seven fixtures make pages of four and three rather than six and one.
 */
private fun paged(
    key: String,
    name: StringResource,
    kind: TemplateKind,
    items: Int,
    perPage: Int,
    draw: @Composable (format: ShareFormat, range: IntRange, page: PosterPage?) -> Unit,
): ShareTemplate {
    val count = maxOf(1, (items + perPage - 1) / perPage)
    val size = maxOf(1, (items + count - 1) / count)
    return ShareTemplate.paged(key, name, kind, pages = { count }) { format, i ->
        draw(format, i * size until minOf(items, (i + 1) * size), if (count > 1) PosterPage(i + 1, count) else null)
    }
}

// ---------------------------------------------------------------------------------------------------------
// Fixtures

/** The fixtures a fixtures poster shows: one day's matches, or when nothing has a date, one round's. */
@Immutable
data class Matchday(val day: LocalDate?, val round: FixtureInfo?, val fixtures: List<Match>)

/**
 * The next day with fixtures still to play, in [zone], with that day's fixtures in time order. When no
 * fixture to play has a date, the earliest round with one to play instead. Null when nothing's left.
 */
@OptIn(ExperimentalTime::class)
fun nextMatchday(fixtures: List<Match>, zone: TimeZone): Matchday? {
    val toPlay = fixtures.filter { it.status == MatchStatus.SCHEDULED && it.fixture != null }
    if (toPlay.isEmpty()) return null
    fun dayOf(match: Match) = match.scheduledAt?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date }
    val day = toPlay.mapNotNull(::dayOf).minOrNull()
    if (day != null) {
        val those = toPlay.filter { dayOf(it) == day }.sortedWith(compareBy({ it.scheduledAt }, { it.fixture!!.round }, { it.fixture!!.number }))
        // A day of one round says so ("Semi-finals"); a mixed day just gives the date.
        return Matchday(day, those.map { it.fixture!! }.distinctBy { it.round }.singleOrNull(), those)
    }
    val round = toPlay.minOf { it.fixture!!.round }
    val those = toPlay.filter { it.fixture!!.round == round }.sortedBy { it.fixture!!.number }
    return Matchday(null, those.first().fixture, those)
}

/** A fixture as posters print it. */
@Immutable
data class PosterFixture(val teamA: String, val teamB: String, val stage: String, val time: String?, val venue: String)

/** A fixtures poster's words: the tournament, the day or round, and this page's fixtures. */
@Immutable
data class FixturesPoster(
    val tournament: String,
    /** "Sat 22 Nov", when the fixtures have a date. */
    val day: String?,
    /** "Matchday 3" or "Semi-final" when the fixtures are all one round, else null. */
    val round: String?,
    val city: String,
    val fixtures: List<PosterFixture>,
    val page: PosterPage?,
)

class FixturesShare(private val tournament: Tournament, private val matchday: Matchday) : ShareContent {
    override val key = "fixtures"

    @Composable
    override fun label() = stringResource(Res.string.poster_content_fixtures)

    private val count = matchday.fixtures.size

    override val templates: List<ShareTemplate> = listOf(
        paged("matchday", Res.string.poster_template_matchday, TemplateKind.OPAQUE, count, MATCHDAY_PER_PAGE) { f, r, p -> MatchdayCardPoster(poster(r, p), f) },
        paged("broadcast", Res.string.poster_template_broadcast_list, TemplateKind.OPAQUE, count, LIST_PER_PAGE) { f, r, p -> BroadcastListPoster(poster(r, p), f) },
        paged("minimal", Res.string.poster_template_minimal_list, TemplateKind.OPAQUE, count, LIST_PER_PAGE) { f, r, p -> MinimalListPoster(poster(r, p), f) },
        paged("versus", Res.string.poster_template_versus, TemplateKind.OPAQUE, count, VERSUS_PER_PAGE) { f, r, p -> VersusTilesPoster(poster(r, p), f) },
    )

    @Composable
    private fun poster(range: IntRange, page: PosterPage?): FixturesPoster {
        return FixturesPoster(
            tournament = tournament.name,
            day = matchday.day?.let { posterDay(it) },
            round = matchday.round?.let { roundTitle(it) },
            city = tournament.city,
            fixtures = matchday.fixtures.slice(range).map { match ->
                PosterFixture(
                    teamA = match.teamA.name,
                    teamB = match.teamB.name,
                    stage = stageText(match.fixture!!),
                    time = match.scheduledAt?.let { posterTime(it) },
                    venue = match.venue,
                )
            },
            page = page,
        )
    }

    companion object {
        const val MATCHDAY_PER_PAGE = 4
        const val LIST_PER_PAGE = 6
        const val VERSUS_PER_PAGE = 4
    }
}

/** A round as a poster heading: a league round is a matchday; knockout rounds are named by stage. */
@Composable
private fun roundTitle(fixture: FixtureInfo): String =
    if (fixture.stage == Stage.LEAGUE) stringResource(Res.string.poster_matchday_number, fixture.round) else stageText(fixture.copy(group = null))

// ---------------------------------------------------------------------------------------------------------
// Draw / bracket

/** One knockout round: its fixtures so far, or for a round still to come, how many there will be. */
@Immutable
data class BracketRound(val stage: Stage, val round: Int, val fixtures: List<Match>, val slots: Int)

private val KnockoutStages = setOf(Stage.QUARTERFINAL, Stage.SEMIFINAL, Stage.FINAL)

private fun isKnockout(format: TournamentFormat, fixture: FixtureInfo) =
    fixture.stage in KnockoutStages || (format == TournamentFormat.KNOCKOUT && fixture.stage == Stage.ROUND)

/**
 * The knockout rounds made so far, then the ones still to come down to the final. Rounds aren't made until
 * the one before finishes (see ADR 0003), so later rounds are only counted, not filled in. Empty for
 * formats without a knockout and before the knockouts start.
 */
fun bracketRounds(details: TournamentDetails): List<BracketRound> {
    val t = details.tournament
    if (t.format == TournamentFormat.DOUBLE_OUT || t.format == TournamentFormat.BEST_OF_THREE || t.format == TournamentFormat.CUSTOM) return emptyList()
    val knockouts = details.fixtures.filter { it.fixture?.let { f -> isKnockout(t.format, f) } == true }
    if (knockouts.isEmpty()) return emptyList()
    val made = knockouts.groupBy { it.fixture!!.round }.toSortedMap().map { (round, matches) ->
        val sorted = matches.sortedBy { it.fixture!!.number }
        BracketRound(sorted.first().fixture!!.stage, round, sorted, sorted.size)
    }
    val last = made.last()
    if (last.stage == Stage.FINAL) return made
    // Who's left after the latest round: its winners, plus in a knockout's first round the seeds with byes.
    var left = last.slots + if (t.format == TournamentFormat.KNOCKOUT && last.round == 1) byes(t.confirmedTeams.size) else 0
    var round = last.round
    val toCome = mutableListOf<BracketRound>()
    while (left >= 2) {
        round++
        val stage = when (left) {
            2 -> Stage.FINAL
            in 3..4 -> Stage.SEMIFINAL
            in 5..8 -> Stage.QUARTERFINAL
            else -> Stage.ROUND
        }
        toCome += BracketRound(stage, round, emptyList(), left / 2)
        left = (left + 1) / 2
    }
    return made + toCome
}

/** Byes in a first knockout round of [teams]: the gap up to the next power of two. */
private fun byes(teams: Int): Int {
    var size = 1
    while (size < teams) size *= 2
    return size - teams
}

/** A knockout match as a bracket box. [winner] is 0 for [teamA], 1 for [teamB]. Null teams aren't known yet. */
@Immutable
data class BracketSlot(val teamA: String?, val teamB: String?, val scoreA: String?, val scoreB: String?, val winner: Int?, val time: String?)

@Immutable
data class BracketStage(val name: String, val slots: List<BracketSlot>)

@Immutable
data class BracketPoster(val tournament: String, val stages: List<BracketStage>, val champion: String?, val page: PosterPage?)

/** The innings score of [side] in a finished [match], e.g. "27/1". */
internal fun Match.scoreOf(side: TeamSide): String? =
    innings.firstOrNull { it.battingSide == side }?.let { if (it.wickets >= 10) "${it.runs}" else "${it.runs}/${it.wickets}" }

class BracketShare(private val details: TournamentDetails, private val rounds: List<BracketRound>) : ShareContent {
    override val key = "bracket"

    @Composable
    override fun label() = stringResource(Res.string.poster_content_bracket)

    /**
     * Pages of rounds. A round of more than [BRACKET_ROUND_MAX] matches gets pages of its own; the rest of
     * the bracket, from the quarter-finals on, shares the last.
     */
    private val pages: List<List<Pair<BracketRound, IntRange>>> = buildList {
        val (big, small) = rounds.partition { it.slots > BRACKET_ROUND_MAX }
        big.forEach { round ->
            (0 until round.slots step BRACKET_ROUND_MAX).forEach { from -> add(listOf(round to (from until minOf(round.slots, from + BRACKET_ROUND_MAX)))) }
        }
        if (small.isNotEmpty()) add(small.map { it to (0 until it.slots) })
    }

    private val final: Match? = details.fixtures.firstOrNull { it.fixture?.stage == Stage.FINAL }

    override val templates: List<ShareTemplate> =
        listOfNotNull(
            paged("bracket", Res.string.poster_template_bracket, TemplateKind.OPAQUE, pages.size, 1) { f, r, p -> BracketTreePoster(poster(r.first, p), f) },
            final?.let { match ->
                ShareTemplate("road", Res.string.poster_template_road, TemplateKind.OPAQUE) { f -> RoadToFinalPoster(roadPoster(match), f) }
            },
        )

    @Composable
    private fun poster(index: Int, page: PosterPage?) = BracketPoster(
        tournament = details.tournament.name,
        stages = pages[index].map { (round, range) ->
            val info = FixtureInfo(details.tournament.id, round.stage, round.round, 1)
            BracketStage(
                stageText(info),
                range.map { i ->
                    val match = round.fixtures.getOrNull(i)
                    if (match == null) BracketSlot(null, null, null, null, null, null)
                    else {
                        val done = match.status == MatchStatus.COMPLETED
                        val through = if (done) winnerId(match) else null
                        BracketSlot(
                            match.teamA.name, match.teamB.name,
                            if (done) match.scoreOf(TeamSide.A) else null,
                            if (done) match.scoreOf(TeamSide.B) else null,
                            through?.let { if (it == match.teamA.teamId) 0 else 1 },
                            if (!done) match.scheduledAt?.let { posterDateTime(it) } else null,
                        )
                    }
                },
            )
        },
        champion = if (index == pages.lastIndex) championName(details) else null,
        page = page,
    )

    private fun winnerId(match: Match): String? = runCatching { TournamentEngine.winner(details.tournament, match).teamId }.getOrNull()

    @Composable
    private fun roadPoster(final: Match): RoadPoster {
        val sides = listOf(TeamSide.A, TeamSide.B).map { side ->
            val team = final.team(side)
            val path = details.fixtures
                .filter { it.id != final.id && it.status == MatchStatus.COMPLETED && it.fixture?.stage != Stage.LEAGUE }
                .filter { it.teamA.teamId == team.teamId || it.teamB.teamId == team.teamId }
                .sortedBy { it.fixture!!.round }
                .map { match ->
                    val mine = if (match.teamA.teamId == team.teamId) TeamSide.A else TeamSide.B
                    val won = winnerId(match) == team.teamId
                    RoadStep(
                        stage = stageText(match.fixture!!),
                        line = stringResource(if (won) Res.string.poster_road_beat else Res.string.poster_road_lost_to, match.team(mine.other).name),
                        score = listOfNotNull(match.scoreOf(mine), match.scoreOf(mine.other)).joinToString(" · ").ifEmpty { null },
                    )
                }
            RoadSide(team.name, leagueLine(team.teamId), path)
        }
        val done = final.status == MatchStatus.COMPLETED
        return RoadPoster(
            tournament = details.tournament.name,
            sides = sides,
            finalLine = when {
                done -> final.result?.let { com.harsh.desicrics.ui.text.resultText(final, it) }
                else -> final.scheduledAt?.let { posterDateTime(it) }
            },
            champion = championName(details),
        )
    }

    /** "2nd in Group A · 8 pts", or for a single league "2nd · 8 pts". */
    @Composable
    private fun leagueLine(teamId: String?): String? {
        details.tables.forEachIndexed { group, table ->
            val index = table.indexOfFirst { it.teamId == teamId }
            if (index >= 0) {
                val place = ordinal(index + 1)
                val points = table[index].points
                return if (details.tables.size > 1) stringResource(Res.string.poster_road_league_group, place, groupName(group), points)
                else stringResource(Res.string.poster_road_league, place, points)
            }
        }
        return null
    }

    companion object {
        const val BRACKET_ROUND_MAX = 4
    }
}

@Immutable
data class RoadStep(val stage: String, val line: String, val score: String?)

@Immutable
data class RoadSide(val team: String, val league: String?, val steps: List<RoadStep>)

@Immutable
data class RoadPoster(val tournament: String, val sides: List<RoadSide>, val finalLine: String?, val champion: String?)

private fun championName(details: TournamentDetails): String? =
    details.championId?.let { id -> details.tournament.teams.firstOrNull { it.teamId == id }?.name }

/** "1st", "2nd", "3rd", "4th"… */
@Composable
private fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> stringResource(Res.string.poster_ordinal_th, n)
    n % 10 == 1 -> stringResource(Res.string.poster_ordinal_st, n)
    n % 10 == 2 -> stringResource(Res.string.poster_ordinal_nd, n)
    n % 10 == 3 -> stringResource(Res.string.poster_ordinal_rd, n)
    else -> stringResource(Res.string.poster_ordinal_th, n)
}

// ---------------------------------------------------------------------------------------------------------
// Points table

/** A line of a table poster: a group heading ([group] set) or a team. */
@Immutable
data class TableLine(
    val group: String? = null,
    val rank: Int = 0,
    val team: String = "",
    val played: Int = 0,
    val won: Int = 0,
    val lost: Int = 0,
    val points: Int = 0,
    val nrr: String = "",
    val qualifies: Boolean = false,
)

@Immutable
data class TablePoster(val tournament: String, val lines: List<TableLine>, val advance: Int, val page: PosterPage?)

class TableShare(private val details: TournamentDetails) : ShareContent {
    override val key = "table"

    @Composable
    override fun label() = stringResource(Res.string.poster_content_table)

    private val advance = when (details.tournament.format) {
        TournamentFormat.BOX_LEAGUE -> 4
        else -> details.tournament.advance
    }

    /** Every team, with a heading before each group when there's more than one; group names are filled in when drawn. */
    internal val lines: List<TableLine> = buildList {
        val grouped = details.tables.size > 1
        details.tables.forEachIndexed { group, table ->
            if (table.isEmpty()) return@forEachIndexed
            if (grouped) add(TableLine(group = groupName(group)))
            table.forEachIndexed { i, row ->
                val nrr = row.netRunRate
                add(
                    TableLine(
                        rank = i + 1, team = row.name, played = row.played, won = row.won, lost = row.lost, points = row.points,
                        nrr = (if (nrr >= 0) "+" else "-") + abs(nrr).format2(), qualifies = i < advance,
                    ),
                )
            }
        }
    }

    /**
     * Pages of up to [TABLE_PER_PAGE] lines. A group starts a new page rather than split across two, unless
     * it's too long for any page; then its lines carry on with their heading repeated.
     */
    private val pages: List<List<TableLine>> = buildList {
        var page = mutableListOf<TableLine>()
        fun turn() {
            if (page.isNotEmpty()) add(page)
            page = mutableListOf()
        }
        // Each group is its heading (if any) and its teams.
        val groups = lines.fold(mutableListOf<MutableList<TableLine>>()) { acc, line ->
            if (line.group != null || acc.isEmpty()) acc += mutableListOf(line) else acc.last() += line
            acc
        }
        for (group in groups) {
            if (page.size + group.size > TABLE_PER_PAGE) turn()
            val heading = group.first().takeIf { it.group != null }
            group.forEach { line ->
                if (page.size >= TABLE_PER_PAGE) {
                    turn()
                    if (heading != null && line !== heading) page += heading
                }
                page += line
            }
        }
        turn()
    }

    override val templates: List<ShareTemplate> = listOf(
        paged("table", Res.string.poster_template_table, TemplateKind.OPAQUE, pages.size, 1) { f, r, p -> TablePosterCard(poster(r.first, p), f) },
        paged("minimal-table", Res.string.poster_template_minimal_table, TemplateKind.OPAQUE, pages.size, 1) { f, r, p -> MinimalTablePoster(poster(r.first, p), f) },
    )

    @Composable
    private fun poster(index: Int, page: PosterPage?) = TablePoster(
        tournament = details.tournament.name,
        lines = pages[index].map { line -> line.group?.let { line.copy(group = stringResource(Res.string.tournament_group, it)) } ?: line },
        advance = if (lines.count { it.group == null } > advance) advance else 0,
        page = page,
    )

    companion object {
        const val TABLE_PER_PAGE = 8
    }
}

// ---------------------------------------------------------------------------------------------------------
// Player of the tournament

@Immutable
data class PlayerPoster(
    val tournament: String,
    val name: String,
    val team: String,
    /** MVP points, then runs and wickets when they made the tournament's top lists. */
    val stats: List<ShareStat>,
    val champion: Boolean,
)

class PlayerOfTournamentShare(private val details: TournamentDetails, private val best: StatLeader) : ShareContent {
    override val key = "player-of-tournament"

    @Composable
    override fun label() = stringResource(Res.string.tournament_player_of_tournament_title)

    override val templates: List<ShareTemplate> = listOf(
        ShareTemplate("spotlight", Res.string.poster_template_spotlight, TemplateKind.OPAQUE) { SpotlightPoster(poster(), it) },
        ShareTemplate("overlay", Res.string.share_template_overlay, TemplateKind.OVERLAY) { PlayerOverlayPoster(poster(), it) },
    )

    @Composable
    private fun poster(): PlayerPoster {
        fun List<StatLeader>.mine() = firstOrNull { it.name == best.name && it.team == best.team && (best.profileId == null || it.profileId == best.profileId) }
        val stats = details.keyStats
        return PlayerPoster(
            tournament = details.tournament.name,
            name = best.name,
            team = best.team,
            stats = listOfNotNull(
                ShareStat(mvpText(best.value), stringResource(Res.string.stats_mvp_points)),
                stats.mostRuns.mine()?.let { ShareStat("${it.value}", stringResource(Res.string.stats_runs)) },
                stats.mostWickets.mine()?.let { ShareStat("${it.value}", stringResource(Res.string.share_wickets_label)) },
                stats.mostSixes.mine()?.let { ShareStat("${it.value}", stringResource(Res.string.stats_sixes)) },
            ).take(3),
            champion = details.championId != null,
        )
    }
}

// ---------------------------------------------------------------------------------------------------------
// Announcement

@Immutable
data class AnnouncementPoster(
    val name: String,
    val city: String,
    /** "Sat 22 Nov", or null before a start date is set. */
    val starts: String?,
    val format: String,
    val overs: String,
    val teams: Int,
    val entriesOpen: Boolean,
    val organisers: String?,
)

class AnnouncementShare(private val details: TournamentDetails) : ShareContent {
    override val key = "announcement"

    @Composable
    override fun label() = stringResource(Res.string.poster_content_announcement)

    override val templates: List<ShareTemplate> = listOf(
        ShareTemplate("hero", Res.string.poster_template_hero, TemplateKind.OPAQUE) { HeroAnnouncementPoster(poster(), it) },
        ShareTemplate("minimal", Res.string.share_template_minimal, TemplateKind.OPAQUE) { MinimalAnnouncementPoster(poster(), it) },
    )

    @OptIn(ExperimentalTime::class)
    @Composable
    private fun poster(): AnnouncementPoster {
        val t = details.tournament
        val firstFixture = details.fixtures.mapNotNull { it.scheduledAt }.minOrNull()
        return AnnouncementPoster(
            name = t.name,
            city = t.city,
            starts = (t.startsAt ?: firstFixture)?.let {
                posterDay(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date)
            },
            format = stringResource(t.format.label),
            overs = oversText(t.oversPerInnings),
            teams = t.confirmedTeams.size,
            entriesOpen = t.registrationOpen && !t.started,
            organisers = t.organisers.map { it.name }.filter { it.isNotBlank() }.joinToString().ifEmpty { null },
        )
    }
}
