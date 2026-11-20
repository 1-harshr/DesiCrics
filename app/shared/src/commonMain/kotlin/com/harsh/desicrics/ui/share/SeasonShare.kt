package com.harsh.desicrics.ui.share

import androidx.compose.runtime.Composable
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.stats.label
import com.harsh.desicrics.util.format2
import org.jetbrains.compose.resources.stringResource

/**
 * A player's numbers for a season (or all time): runs and wickets, matches, best figures and recent
 * form. [season] names the period, e.g. "2026 season" or "All time". Bowlers lead with wickets, everyone
 * else with runs.
 */
class SeasonShare(private val name: String, private val stats: CareerStats, private val season: String) : ShareContent {
    override val key = "season"

    @Composable
    override fun label() = stringResource(Res.string.share_content_season)

    /** A wicket is worth about 20 runs, the same as in MVP points. */
    private val leadsWithWickets = stats.bowling.wickets * 20 > stats.batting.runs

    override val templates: List<ShareTemplate> = listOf(
        ShareTemplate("overlay", Res.string.share_template_overlay, TemplateKind.OVERLAY) { format ->
            val best = bestText()
            SeasonOverlay(
                SeasonFigures(
                    name = name,
                    lead = if (leadsWithWickets) ShareStat("${stats.bowling.wickets}", stringResource(Res.string.share_card_wickets))
                    else ShareStat("${stats.batting.runs}", stringResource(Res.string.share_card_runs)),
                    stats = listOf(
                        ShareStat("${stats.matches}", stringResource(Res.string.stats_matches)),
                        ShareStat(best, stringResource(Res.string.stats_best)),
                        if (leadsWithWickets) ShareStat(stats.bowling.economy.format2(), stringResource(Res.string.stats_economy))
                        else ShareStat(stats.batting.strikeRate.format2(), stringResource(Res.string.stats_strike_rate)),
                    ),
                    form = stats.form.filter { if (leadsWithWickets) true else it.batted }.map { if (leadsWithWickets) it.wickets else it.runs },
                    caption = season,
                ),
                format,
            )
        },
    ) + summaryTemplates { summary() }

    @Composable
    private fun bestText() =
        if (leadsWithWickets) "${stats.bowling.bestWickets}/${stats.bowling.bestRuns}"
        else "${stats.batting.highest}${if (stats.batting.highestNotOut) "*" else ""}"

    @Composable
    private fun summary(): ShareSummary {
        val runs = ShareHeadline(stringResource(Res.string.stats_runs), "${stats.batting.runs}")
        val wickets = ShareHeadline(stringResource(Res.string.share_wickets_label), "${stats.bowling.wickets}")
        return ShareSummary(
            title = name,
            headlines = if (leadsWithWickets) listOf(wickets, runs) else listOf(runs, wickets),
            stats = listOf(
                ShareStat("${stats.matches}", stringResource(Res.string.stats_matches)),
                ShareStat("${stats.batting.highest}${if (stats.batting.highestNotOut) "*" else ""}", stringResource(Res.string.stats_highest)),
                ShareStat("${stats.bowling.bestWickets}/${stats.bowling.bestRuns}", stringResource(Res.string.stats_best)),
            ),
            lines = stats.badges.take(3).map { stringResource(it.label) },
            caption = season,
        )
    }
}
