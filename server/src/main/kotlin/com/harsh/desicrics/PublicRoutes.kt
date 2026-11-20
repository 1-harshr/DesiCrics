package com.harsh.desicrics

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.live.LiveMatches
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.scoring.BatterCard
import com.harsh.desicrics.scoring.Retirement
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.service.ApiException
import com.harsh.desicrics.service.CommunityService
import com.harsh.desicrics.service.MatchService
import com.harsh.desicrics.service.TournamentService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.flow.onSubscription

/**
 * Pages anyone can open without signing in, for links shared outside the app. Phones with the app open
 * these links in it directly; this is what everyone else sees.
 */
fun Route.publicRoutes(
    matches: MatchService,
    tournaments: TournamentService,
    community: CommunityService,
    live: LiveMatches,
    androidCertFingerprints: List<String> = emptyList(),
) {
    // JSON and the live feed for the web app's shared-link pages (docs/web.md), with account IDs left out.
    get("/public/matches/{id}") { call.respond(matches.details(call.parameters["id"].orEmpty()).forPublic()) }
    get("/public/tournaments/{id}") { call.respond(tournaments.details(call.parameters["id"].orEmpty()).forPublic()) }
    webSocket("/public/matches/{id}/live") {
        val matchId = call.parameters["id"].orEmpty()
        try {
            live.updates(matchId)
                .onSubscription { emit(matches.details(matchId)) }
                .collect { sendSerialized(it.forPublic()) }
        } catch (e: ApiException) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.message))
        }
    }

    // Livestream overlay: an OBS / Larix browser source polls the JSON and draws a score bar.
    get("/m/{id}/overlay.json") { call.respond(community.overlay(matches.details(call.parameters["id"].orEmpty()))) }
    get("/overlay/{id}") {
        val id = call.parameters["id"].orEmpty().filter { it.isLetterOrDigit() || it == '-' }
        matches.details(id)
        call.respondText(overlayPage(id), ContentType.Text.Html)
    }

    // Digital Asset Links: lets Android verify that this domain's /join, /m and /t links belong to the app.
    get("/.well-known/assetlinks.json") {
        val fingerprints = androidCertFingerprints.joinToString(",") { "\"${it.escapeJson()}\"" }
        val body = """[{"relation":["delegate_permission/common.handle_all_urls"],""" +
            """"target":{"namespace":"android_app","package_name":"com.harsh.desicrics","sha256_cert_fingerprints":[$fingerprints]}}]"""
        call.respondText(body, ContentType.Application.Json)
    }

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

/** Who created and scores a match are Firebase account IDs, which nobody outside the app needs. */
internal fun MatchDetails.forPublic(): MatchDetails = copy(match = match.forPublic())

internal fun Match.forPublic(): Match = copy(createdBy = "", scorerUid = "")

/**
 * The tournament as its public page shows it: no account IDs, and none of the entry workings (player pool,
 * squad offers and changes) that only organisers and entrants see.
 */
internal fun TournamentDetails.forPublic(): TournamentDetails = copy(
    tournament = tournament.copy(
        createdBy = "",
        organisers = tournament.organisers.map { it.copy(uid = "") },
        players = emptyList(),
        offers = emptyList(),
        squadChanges = emptyList(),
    ),
    fixtures = fixtures.map { it.forPublic() },
)

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
        val now = System.currentTimeMillis()
        if (t.takesTeams(now) || t.takesPlayers(now)) {
            val who = listOfNotNull("teams".takeIf { t.takesTeams(now) }, "players".takeIf { t.takesPlayers(now) }).joinToString(" and ")
            append("<div class=\"card\"><h2>Entries are open</h2><p>This tournament is taking $who. Open this link on your phone with the DesiCrics app to enter.</p></div>")
        }
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

/**
 * A transparent score bar for the bottom of a livestream. Add the page as a browser source (1920×1080) in
 * OBS or Larix; it fetches the score every 2 seconds and keeps the last one if a fetch fails.
 */
internal fun overlayPage(matchId: String): String = """
    <!doctype html>
    <html><head><meta charset="utf-8"><title>DesiCrics overlay</title>
    <style>
      html, body { margin: 0; background: transparent; font-family: system-ui, sans-serif; }
      .bar { position: fixed; left: 40px; right: 40px; bottom: 40px; display: flex; align-items: stretch;
             border-radius: 14px; overflow: hidden; box-shadow: 0 6px 24px rgba(0,0,0,.35); font-size: 28px; }
      .team { background: #0b6b3a; color: #fff; padding: 14px 22px; font-weight: 800; }
      .score { background: #fff; color: #14201a; padding: 14px 22px; font-weight: 800; font-variant-numeric: tabular-nums; }
      .score small { font-weight: 600; color: #5c6b62; margin-left: 10px; }
      .mid { flex: 1; background: rgba(20,32,26,.92); color: #fff; padding: 10px 22px; font-size: 22px; line-height: 1.35; }
      .detail { background: #f4b400; color: #14201a; padding: 14px 22px; font-weight: 700; }
      .ball { display: inline-block; min-width: 30px; margin-right: 6px; text-align: center; border-radius: 15px; background: #2a352f; }
    </style></head>
    <body><div class="bar" id="bar" hidden>
      <div class="team" id="team"></div>
      <div class="score"><span id="score"></span><small id="overs"></small></div>
      <div class="mid"><div id="batters"></div><div id="bowler"></div></div>
      <div class="detail" id="detail"></div>
    </div>
    <script>
      const text = (id, value) => { document.getElementById(id).textContent = value || ""; };
      async function refresh() {
        try {
          const r = await fetch("/m/$matchId/overlay.json", { cache: "no-store" });
          if (!r.ok) return;
          const s = await r.json();
          text("team", s.battingTeam); text("score", s.score); text("overs", s.overs + " ov"); text("detail", s.detail);
          text("batters", [s.striker ? s.striker + "*" : null, s.nonStriker].filter(Boolean).join("  ·  "));
          const bowler = document.getElementById("bowler");
          bowler.textContent = s.bowler ? s.bowler + "   " : "";
          (s.thisOver || []).forEach(b => { const e = document.createElement("span"); e.className = "ball"; e.textContent = b; bowler.appendChild(e); });
          document.getElementById("bar").hidden = false;
        } catch (e) { /* keep the last score on screen */ }
      }
      refresh(); setInterval(refresh, 2000);
    </script></body></html>
""".trimIndent()

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

private fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"")

internal fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
