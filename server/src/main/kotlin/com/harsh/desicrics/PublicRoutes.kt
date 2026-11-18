package com.harsh.desicrics

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.scoring.BatterCard
import com.harsh.desicrics.scoring.Retirement
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.service.ApiException
import com.harsh.desicrics.service.MatchService
import com.harsh.desicrics.service.TournamentService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Pages anyone can open without signing in, for links shared outside the app. Phones with the app open
 * these links in it directly; this is what everyone else sees.
 */
fun Route.publicRoutes(matches: MatchService, tournaments: TournamentService) {
    get("/t/{id}") {
        val details = try {
            tournaments.details(call.parameters["id"].orEmpty())
        } catch (e: ApiException) {
            call.respondText(page("Tournament not found", "<h1>Tournament not found</h1>"), ContentType.Text.Html, HttpStatusCode.NotFound)
            return@get
        }
        call.respondText(tournamentPage(details), ContentType.Text.Html)
    }

    get("/m/{id}") {
        val details = try {
            matches.details(call.parameters["id"].orEmpty())
        } catch (e: ApiException) {
            call.respondText(page("Match not found", "<h1>Match not found</h1>"), ContentType.Text.Html, HttpStatusCode.NotFound)
            return@get
        }
        call.respondText(scorecardPage(details), ContentType.Text.Html)
    }

    get("/join/{code}") {
        val code = call.parameters["code"].orEmpty().filter { it.isLetterOrDigit() }.take(8).uppercase()
        call.respondText(
            page(
                title = "Join a team on DesiCrics",
                body = """
                    <h1>Join a team on DesiCrics</h1>
                    <p>Install DesiCrics, then open <b>Teams</b> and tap <b>Join with a code</b>.</p>
                    <p class="code">$code</p>
                """,
            ),
            ContentType.Text.Html,
        )
    }
}

/**
 * A read-only scorecard for anyone with the link. A match still in play reloads itself every 15 seconds;
 * the app's followers get every ball live instead.
 */
internal fun scorecardPage(details: MatchDetails): String {
    val match = details.match
    val state = ScoringEngine.compute(match, details.deliveries, details.events)
    val title = "${match.teamA.name} vs ${match.teamB.name}"
    val status = when {
        match.status == MatchStatus.SCHEDULED -> "Not started yet"
        state.isComplete -> match.scoreSummary
        else -> "Live · ${state.current.battingTeam.name} ${state.current.scoreText} (${state.current.oversText} ov)"
    }
    val body = buildString {
        append("<h1>${title.escapeHtml()}</h1><p class=\"muted\">${status.escapeHtml()}</p>")
        for (innings in state.innings) {
            append("<div class=\"card\"><h2>${innings.battingTeam.name.escapeHtml()} ${innings.scoreText} <span class=\"muted\">(${innings.oversText} ov)</span></h2>")
            append("<table><tr><th>Batter</th><th>R</th><th>B</th><th>4s</th><th>6s</th></tr>")
            for (card in innings.batters) {
                append("<tr><td>${card.player.name.escapeHtml()}<br><span class=\"muted\">${howOut(card).escapeHtml()}</span></td>")
                append("<td>${card.runs}</td><td>${card.balls}</td><td>${card.fours}</td><td>${card.sixes}</td></tr>")
            }
            append("</table><p class=\"muted\">Extras ${innings.extras.total}</p>")
            append("<table><tr><th>Bowler</th><th>O</th><th>M</th><th>R</th><th>W</th></tr>")
            for (card in innings.bowlers) {
                append("<tr><td>${card.player.name.escapeHtml()}</td><td>${card.oversText}</td><td>${card.maidens}</td><td>${card.runs}</td><td>${card.wickets}</td></tr>")
            }
            append("</table></div>")
        }
        append("<p class=\"muted\">Scored with DesiCrics</p>")
    }
    return page(title, body, refreshSeconds = if (state.isComplete) null else 15)
}

private fun howOut(card: BatterCard): String {
    card.retired?.let { return if (it == Retirement.OUT) "retired out" else "retired not out" }
    val dismissal = card.dismissal ?: return "not out"
    val bowler = dismissal.bowler?.name
    val fielder = dismissal.fielder?.name
    return when (dismissal.kind) {
        DismissalKind.BOWLED -> "b $bowler"
        DismissalKind.CAUGHT -> if (fielder == null) "c ? b $bowler" else if (fielder == bowler) "c & b $bowler" else "c $fielder b $bowler"
        DismissalKind.LBW -> "lbw b $bowler"
        DismissalKind.RUN_OUT -> if (fielder == null) "run out" else "run out ($fielder)"
        DismissalKind.STUMPED -> if (fielder == null) "st ? b $bowler" else "st $fielder b $bowler"
        DismissalKind.HIT_WICKET -> "hit wicket b $bowler"
        DismissalKind.OBSTRUCTING_FIELD -> "obstructing the field"
        DismissalKind.HIT_BALL_TWICE -> "hit the ball twice"
        DismissalKind.OTHER -> "out"
    }
}

/** The tournament for anyone with the link: champion, tables, fixtures and top performers. */
internal fun tournamentPage(details: TournamentDetails): String {
    val t = details.tournament
    val names = t.teams.associate { it.teamId to it.name }
    val body = buildString {
        append("<h1>${t.name.escapeHtml()}</h1><p class=\"muted\">${listOf(t.city, "${t.oversPerInnings} overs").filter { it.isNotBlank() }.joinToString(" · ").escapeHtml()}</p>")
        details.championId?.let { append("<p class=\"code\">🏆 ${names[it].orEmpty().escapeHtml()}</p>") }
        details.tables.forEachIndexed { group, table ->
            val title = if (details.tables.size > 1) "Group ${'A' + group}" else "Points table"
            append("<div class=\"card\"><h2>$title</h2><table><tr><th>Team</th><th>P</th><th>W</th><th>L</th><th>Pts</th><th>NRR</th></tr>")
            for (row in table) {
                append("<tr><td>${row.name.escapeHtml()}</td><td>${row.played}</td><td>${row.won}</td><td>${row.lost}</td><td>${row.points}</td>")
                append("<td>${"%+.3f".format(row.netRunRate)}</td></tr>")
            }
            append("</table></div>")
        }
        append("<div class=\"card\"><h2>Fixtures</h2><table>")
        for (match in details.fixtures) {
            val status = when (match.status) {
                MatchStatus.SCHEDULED -> "To play"
                MatchStatus.COMPLETED -> match.scoreSummary
                else -> "Live · ${match.scoreSummary}"
            }
            append("<tr><td><a href=\"/m/${match.id}\">${match.teamA.name.escapeHtml()} vs ${match.teamB.name.escapeHtml()}</a>")
            append("<br><span class=\"muted\">${match.fixture?.stage?.name.orEmpty().lowercase().replaceFirstChar { it.uppercase() }} · ${status.escapeHtml()}</span></td></tr>")
        }
        append("</table></div>")
        val stats = details.keyStats
        for ((title, leaders) in listOf("Most runs" to stats.mostRuns, "Most wickets" to stats.mostWickets, "Most sixes" to stats.mostSixes)) {
            if (leaders.isEmpty()) continue
            append("<div class=\"card\"><h2>$title</h2><table>")
            leaders.forEach { append("<tr><td>${it.name.escapeHtml()} <span class=\"muted\">${it.team.escapeHtml()}</span></td><td>${it.value}</td></tr>") }
            append("</table></div>")
        }
        append("<p class=\"muted\">Run on DesiCrics</p>")
    }
    return page(t.name, body, refreshSeconds = if (details.fixtures.any { it.status == MatchStatus.IN_PROGRESS }) 30 else null)
}

/** A small mobile-first page in the app's colours. [body] must already be HTML-escaped. */
internal fun page(title: String, body: String, refreshSeconds: Int? = null): String = """
    <!doctype html>
    <html lang="en">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>${title.escapeHtml()}</title>
      ${refreshSeconds?.let { "<meta http-equiv=\"refresh\" content=\"$it\">" }.orEmpty()}
      <style>
        body { font-family: system-ui, sans-serif; margin: 0; padding: 24px 16px; background: #f6f7f4; color: #14201a; }
        h1 { font-size: 22px; } .muted { color: #5c6b62; }
        .code { font: 700 32px ui-monospace, monospace; letter-spacing: 4px; color: #0b6b3a; }
        table { width: 100%; border-collapse: collapse; font-size: 14px; }
        th, td { text-align: right; padding: 6px 4px; border-bottom: 1px solid #dde3dc; }
        th:first-child, td:first-child { text-align: left; }
        .card { background: #fff; border-radius: 12px; padding: 16px; margin: 12px 0; }
        @media (prefers-color-scheme: dark) {
          body { background: #0f1512; color: #e7ede9; } .card { background: #18211c; } .muted { color: #9fb0a6; }
          .code { color: #5fd197; } th, td { border-color: #2a352f; }
        }
      </style>
    </head>
    <body>$body</body>
    </html>
""".trimIndent()

internal fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
