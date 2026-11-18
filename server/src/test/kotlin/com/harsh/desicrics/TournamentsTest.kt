package com.harsh.desicrics

import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.api.EnterTeamRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.ReviewEntryRequest
import com.harsh.desicrics.api.ScheduleRequest
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.api.StartFixtureRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Notification
import com.harsh.desicrics.model.NotificationKind
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.Stage
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.model.TournamentSummary
import com.harsh.desicrics.model.Wicket
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TournamentsTest {

    private suspend fun HttpClient.team(uid: String, name: String): SavedTeam =
        post("/teams") { bearerAuth("test-$uid"); jsonBody(TeamRequest(name)) }.body()

    private suspend fun HttpClient.tournament(uid: String, request: CreateTournamentRequest): TournamentDetails =
        post("/tournaments") { bearerAuth("test-$uid"); jsonBody(request) }.body()

    private suspend fun HttpClient.details(id: String): TournamentDetails = get("/tournaments/$id") { bearerAuth("test-asha") }.body()

    private suspend fun HttpClient.enter(uid: String, tournamentId: String, teamId: String) =
        post("/tournaments/$tournamentId/teams") { bearerAuth("test-$uid"); jsonBody(EnterTeamRequest(teamId)) }

    /**
     * Starts [fixture] with two-player squads and plays it out in three balls: side A makes 4 and is bowled,
     * then side B is bowled first ball (A wins) or hits a four (B wins).
     */
    private suspend fun HttpClient.playFixture(fixture: Match, aWins: Boolean) {
        val squad = { side: TeamSide ->
            val team = fixture.team(side)
            SquadRequest(team.name, listOf(SquadPlayer("${team.name} 1"), SquadPlayer("${team.name} 2")), teamId = team.teamId)
        }
        val started = post("/matches/${fixture.id}/start") {
            bearerAuth("test-asha")
            jsonBody(StartFixtureRequest(squad(TeamSide.A), squad(TeamSide.B), TeamSide.A, TossDecision.BAT))
        }.body<MatchDetails>().match
        val (a1, a2) = started.teamA.players
        val (b1, b2) = started.teamB.players
        record("asha", started.id, Delivery(0, 1, a1.id, a2.id, b1.id, runsOffBat = 4))
        record("asha", started.id, Delivery(1, 1, a1.id, a2.id, b1.id, wicket = Wicket(DismissalKind.BOWLED, a1.id)))
        val last = if (aWins) Delivery(2, 2, b1.id, b2.id, a1.id, wicket = Wicket(DismissalKind.BOWLED, b1.id))
        else Delivery(2, 2, b1.id, b2.id, a1.id, runsOffBat = 6)
        assertEquals(HttpStatusCode.OK, record("asha", started.id, last).status)
    }

    @Test
    fun aKnockoutRunsFromEntriesToChampion() = apiTest { client ->
        client.setUpProfile("asha")
        val teams = (1..4).map { client.team("asha", "Team $it") }
        val cup = client.tournament("asha", CreateTournamentRequest("Weekend Cup", format = TournamentFormat.KNOCKOUT, oversPerInnings = 1))
        teams.forEach { client.enter("asha", cup.tournament.id, it.id) }
        assertEquals(HttpStatusCode.Conflict, client.enter("asha", cup.tournament.id, teams[0].id).status)

        val started = client.post("/tournaments/${cup.tournament.id}/start") { bearerAuth("test-asha") }.body<TournamentDetails>()
        // Seeds 1v4 and 2v3, scheduled until someone starts them.
        assertEquals(listOf("Team 1" to "Team 4", "Team 2" to "Team 3"), started.fixtures.map { it.teamA.name to it.teamB.name })
        assertTrue(started.fixtures.all { it.status == MatchStatus.SCHEDULED && it.fixture!!.stage == Stage.SEMIFINAL })
        // A fixture without squads isn't over just because nobody's batting.
        val unstarted = client.get("/matches/${started.fixtures[0].id}") { bearerAuth("test-asha") }.body<MatchDetails>()
        assertEquals(MatchStatus.SCHEDULED, unstarted.match.status)

        client.playFixture(started.fixtures[0], aWins = true)
        // The final waits for the other semi.
        assertEquals(2, client.details(cup.tournament.id).fixtures.size)
        client.playFixture(started.fixtures[1], aWins = false)

        val withFinal = client.details(cup.tournament.id)
        val final = withFinal.fixtures.single { it.fixture!!.stage == Stage.FINAL }
        assertEquals("Team 1" to "Team 3", final.teamA.name to final.teamB.name)
        assertNull(withFinal.championId)
        client.playFixture(final, aWins = false)

        val done = client.details(cup.tournament.id)
        assertEquals(teams[2].id, done.championId)
        assertEquals(3, done.fixtures.size)
        // Team 1 and Team 3 batters made fours; Team 3 also hit sixes.
        assertTrue(done.keyStats.mostSixes.first().team == "Team 3")
        assertTrue(client.get("/t/${cup.tournament.id}").bodyAsText().contains("Weekend Cup"))
    }

    @Test
    fun aLeagueKeepsAPointsTable() = apiTest { client ->
        client.setUpProfile("asha")
        val teams = (1..3).map { client.team("asha", "Team $it") }
        val league = client.tournament("asha", CreateTournamentRequest("Sunday League", format = TournamentFormat.ROUND_ROBIN, oversPerInnings = 1))
        teams.forEach { client.enter("asha", league.tournament.id, it.id) }
        val started = client.post("/tournaments/${league.tournament.id}/start") { bearerAuth("test-asha") }.body<TournamentDetails>()
        assertEquals(3, started.fixtures.size)
        started.fixtures.forEach { client.playFixture(it, aWins = true) }

        val done = client.details(league.tournament.id)
        val table = done.tables.single()
        assertEquals(3, table.size)
        assertTrue(table.all { it.played == 2 })
        assertEquals(table.first().teamId, done.championId)
    }

    @Test
    fun teamAdminsRegisterAndOrganisersReview() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        val devs = client.team("dev", "Dev XI")
        val closed = client.tournament("asha", CreateTournamentRequest("Closed Cup", format = TournamentFormat.KNOCKOUT, oversPerInnings = 5))
        assertEquals(HttpStatusCode.Forbidden, client.enter("dev", closed.tournament.id, devs.id).status)

        val open = client.tournament("asha", CreateTournamentRequest("Open Cup", format = TournamentFormat.KNOCKOUT, oversPerInnings = 5, registrationOpen = true))
        val pending = client.enter("dev", open.tournament.id, devs.id).body<TournamentDetails>()
        assertEquals(EntryStatus.PENDING, pending.tournament.teams.single().status)
        assertEquals(listOf("Open Cup"), client.get("/tournaments/mine") { bearerAuth("test-dev") }.body<List<TournamentSummary>>().map { it.name })
        // Only organisers review entries or start the tournament.
        assertEquals(HttpStatusCode.Forbidden, client.post("/tournaments/${open.tournament.id}/start") { bearerAuth("test-dev") }.status)
        val reviewed = client.put("/tournaments/${open.tournament.id}/teams/${devs.id}") {
            bearerAuth("test-asha")
            jsonBody(ReviewEntryRequest(confirm = true))
        }.body<TournamentDetails>()
        assertEquals(EntryStatus.CONFIRMED, reviewed.tournament.teams.single().status)
        // One team isn't enough for a knockout.
        assertEquals(HttpStatusCode.Conflict, client.post("/tournaments/${open.tournament.id}/start") { bearerAuth("test-asha") }.status)
    }

    @Test
    fun reschedulingTellsFollowers() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        val one = client.team("asha", "Team 1")
        val two = client.team("asha", "Team 2")
        client.put("/follows/team/${one.id}") { bearerAuth("test-dev") }
        val series = client.tournament("asha", CreateTournamentRequest("Series", format = TournamentFormat.BEST_OF_THREE, oversPerInnings = 5))
        client.enter("asha", series.tournament.id, one.id)
        client.enter("asha", series.tournament.id, two.id)
        val fixture = client.post("/tournaments/${series.tournament.id}/start") { bearerAuth("test-asha") }.body<TournamentDetails>().fixtures.single()

        // Scheduling a fixture isn't "started".
        assertTrue(client.get("/notifications") { bearerAuth("test-dev") }.body<List<Notification>>().isEmpty())
        val moved = client.put("/matches/${fixture.id}/schedule") { bearerAuth("test-asha"); jsonBody(ScheduleRequest(1_800_000_000_000)) }
        assertEquals(1_800_000_000_000, moved.body<MatchDetails>().match.scheduledAt)
        assertEquals(HttpStatusCode.Forbidden, client.put("/matches/${fixture.id}/schedule") { bearerAuth("test-dev"); jsonBody(ScheduleRequest(null)) }.status)
        val inbox = client.get("/notifications") { bearerAuth("test-dev") }.body<List<Notification>>()
        assertEquals(listOf(NotificationKind.FIXTURE_RESCHEDULED), inbox.map { it.kind })
        assertNotNull(inbox.single().detail)
    }
}
