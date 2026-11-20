package com.harsh.desicrics

import io.ktor.http.contentType
import io.ktor.http.ContentType
import io.ktor.client.request.setBody
import io.ktor.client.request.delete
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.AbandonReason
import com.harsh.desicrics.model.MatchEnding
import com.harsh.desicrics.api.EndBeforeStartRequest
import com.harsh.desicrics.api.AddTournamentPlayerRequest
import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.api.EnterTeamRequest
import com.harsh.desicrics.api.EntrySettingsRequest
import com.harsh.desicrics.api.PickupTeamsRequest
import com.harsh.desicrics.api.PlacePlayerRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.ReviewEntryRequest
import com.harsh.desicrics.api.ScheduleRequest
import com.harsh.desicrics.api.SquadOfferRequest
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.api.StartFixtureRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.api.AddMemberRequest
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.EntryPolicy
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.PoolMode
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

    private suspend fun HttpClient.addPlayer(uid: String, tournamentId: String, request: AddTournamentPlayerRequest = AddTournamentPlayerRequest()) =
        post("/tournaments/$tournamentId/players") { bearerAuth("test-$uid"); jsonBody(request) }

    private suspend fun HttpClient.place(uid: String, tournamentId: String, profileId: String, teamId: String?) =
        put("/tournaments/$tournamentId/players/$profileId") { bearerAuth("test-$uid"); jsonBody(PlacePlayerRequest(teamId)) }

    private suspend fun HttpClient.inbox(uid: String): List<Notification> = get("/notifications") { bearerAuth("test-$uid") }.body()

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
        val closed = client.tournament(
            "asha",
            CreateTournamentRequest("Closed Cup", format = TournamentFormat.KNOCKOUT, oversPerInnings = 5, teamEntry = EntryPolicy.ORGANISERS),
        )
        assertEquals(HttpStatusCode.Forbidden, client.enter("dev", closed.tournament.id, devs.id).status)

        val open = client.tournament("asha", CreateTournamentRequest("Open Cup", format = TournamentFormat.KNOCKOUT, oversPerInnings = 5, teamEntry = EntryPolicy.APPROVAL))
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
        // Confirmed, the team's members make up its squad.
        assertEquals(listOf("dev"), reviewed.tournament.squad(devs.id).map { it.player.profileId })
        // Asha heard about the request, and Dev about the answer.
        assertEquals(listOf(NotificationKind.ENTRY_REQUEST), client.inbox("asha").map { it.kind })
        assertEquals(listOf(NotificationKind.ENTRY_CONFIRMED), client.inbox("dev").map { it.kind })
        assertEquals(open.tournament.id, client.inbox("dev").single().tournamentId)
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

    @Test
    fun aNoShowIsAWalkoverAndUndoPutsTheFixtureBack() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("ravi", name = "Ravi", phone = "+91 98765 00000")
        val teams = (1..4).map { client.team("asha", "Team $it") }
        val cup = client.tournament("asha", CreateTournamentRequest("Final Day", format = TournamentFormat.KNOCKOUT, oversPerInnings = 1))
        teams.forEach { client.enter("asha", cup.tournament.id, it.id) }
        val semis = client.post("/tournaments/${cup.tournament.id}/start") { bearerAuth("test-asha") }.body<TournamentDetails>().fixtures
        // Team 1 doesn't turn up for its semi against Team 4.
        val final = semis[0]
        val walkover = EndBeforeStartRequest(MatchEnding.WALKOVER, winner = TeamSide.B)

        // Only an organiser can hand out a walkover.
        assertEquals(HttpStatusCode.Forbidden, client.post("/matches/${final.id}/end") { bearerAuth("test-ravi"); jsonBody(walkover) }.status)
        // A walkover needs the team that turned up.
        assertEquals(
            HttpStatusCode.BadRequest,
            client.post("/matches/${final.id}/end") { bearerAuth("test-asha"); jsonBody(EndBeforeStartRequest(MatchEnding.WALKOVER)) }.status,
        )

        val ended = client.post("/matches/${final.id}/end") { bearerAuth("test-asha"); jsonBody(walkover) }.body<MatchDetails>().match
        assertEquals(MatchStatus.COMPLETED, ended.status)
        assertEquals(MatchResult(TeamSide.B, ending = MatchEnding.WALKOVER), ended.result)
        assertTrue(ended.innings.isEmpty())
        assertEquals("Team 4 won by walkover", ended.scoreSummary)

        // Recorded by mistake: undo puts it back on the schedule, waiting for its toss.
        val reopened = client.delete("/matches/${final.id}/last") { bearerAuth("test-asha") }.body<MatchDetails>().match
        assertEquals(MatchStatus.SCHEDULED, reopened.status)
        assertNull(reopened.result)

        // Given again, the walkover sends Team 4 to the final.
        client.post("/matches/${final.id}/end") { bearerAuth("test-asha"); jsonBody(walkover) }
        client.playFixture(semis[1], aWins = true)
        val decider = client.details(cup.tournament.id).fixtures.single { it.fixture!!.stage == Stage.FINAL }
        assertEquals("Team 2" to "Team 4", decider.teamA.name to decider.teamB.name)
    }

    @Test
    fun rainDuringALeagueMatchIsAPointEach() = apiTest { client ->
        client.setUpProfile("asha")
        val teams = (1..4).map { client.team("asha", "Team $it") }
        val league = client.tournament("asha", CreateTournamentRequest("Monsoon League", format = TournamentFormat.ROUND_ROBIN, oversPerInnings = 2))
        teams.forEach { client.enter("asha", league.tournament.id, it.id) }
        val fixture = client.post("/tournaments/${league.tournament.id}/start") { bearerAuth("test-asha") }.body<TournamentDetails>().fixtures.first()
        val squad = { side: TeamSide ->
            val team = fixture.team(side)
            SquadRequest(team.name, listOf(SquadPlayer("${team.name} 1"), SquadPlayer("${team.name} 2")), teamId = team.teamId)
        }
        val started = client.post("/matches/${fixture.id}/start") {
            bearerAuth("test-asha")
            jsonBody(StartFixtureRequest(squad(TeamSide.A), squad(TeamSide.B), TeamSide.A, TossDecision.BAT))
        }.body<MatchDetails>().match
        val (a1, a2) = started.teamA.players
        client.record("asha", started.id, Delivery(0, 1, a1.id, a2.id, started.teamB.players[0].id, runsOffBat = 4))

        val rain: MatchEvent = MatchEvent.EndMatch(1, 1, MatchEnding.ABANDONED, reason = AbandonReason.RAIN)
        val after = client.post("/matches/${started.id}/events") {
            bearerAuth("test-asha")
            contentType(ContentType.Application.Json)
            setBody(rain)
        }.body<MatchDetails>()
        assertEquals(MatchStatus.COMPLETED, after.match.status)
        assertTrue(after.match.result!!.isNoResult)
        assertTrue(after.match.scoreSummary.endsWith("No result (rain)"))
        // Nothing more can be scored once it's over.
        assertEquals(HttpStatusCode.Conflict, client.record("asha", started.id, Delivery(2, 1, a1.id, a2.id, started.teamB.players[0].id)).status)

        val table = client.details(league.tournament.id).tables.single()
        val rained = table.filter { it.teamId == fixture.teamA.teamId || it.teamId == fixture.teamB.teamId }
        assertTrue(rained.all { it.points == 1 && it.played == 1 && it.noResult == 1 && it.ballsFaced == 0 })
    }

    @Test
    fun organisersChangeWhoCanEnterUntilTheStart() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        val teams = (1..2).map { client.team("asha", "Team $it") }
        val devs = client.team("dev", "Dev XI")
        val cup = client.tournament(
            "asha",
            CreateTournamentRequest("Cup", format = TournamentFormat.KNOCKOUT, oversPerInnings = 5, teamEntry = EntryPolicy.ORGANISERS, playerEntry = EntryPolicy.ORGANISERS),
        ).tournament
        assertEquals(HttpStatusCode.Forbidden, client.addPlayer("dev", cup.id).status)
        val settings = EntrySettingsRequest(teamEntry = EntryPolicy.OPEN, playerEntry = EntryPolicy.APPROVAL)
        assertEquals(HttpStatusCode.Forbidden, client.put("/tournaments/${cup.id}/entry") { bearerAuth("test-dev"); jsonBody(settings) }.status)
        val opened = client.put("/tournaments/${cup.id}/entry") { bearerAuth("test-asha"); jsonBody(settings) }.body<TournamentDetails>().tournament
        assertEquals(EntryPolicy.OPEN to EntryPolicy.APPROVAL, opened.teamEntry to opened.playerEntry)
        assertTrue(client.get("/t/${cup.id}").bodyAsText().contains("taking teams and players"))

        // Open entry: Dev's team is in straight away, and Asha hears it joined.
        val joined = client.enter("dev", cup.id, devs.id).body<TournamentDetails>().tournament
        assertEquals(EntryStatus.CONFIRMED, joined.teams.single().status)
        assertEquals(listOf(NotificationKind.ENTRY_JOINED), client.inbox("asha").map { it.kind })

        teams.forEach { client.enter("asha", cup.id, it.id) }
        client.post("/tournaments/${cup.id}/start") { bearerAuth("test-asha") }
        assertEquals(HttpStatusCode.Conflict, client.put("/tournaments/${cup.id}/entry") { bearerAuth("test-asha"); jsonBody(settings) }.status)
    }

    @Test
    fun playersJoinThePoolAndOrganisersPlaceThem() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        client.setUpProfile("kabir", name = "Kabir")
        client.setUpProfile("ravi", name = "Ravi")
        val strikers = client.team("asha", "Strikers")
        val royals = client.team("ravi", "Royals")
        val cup = client.tournament("asha", CreateTournamentRequest("Cup", format = TournamentFormat.KNOCKOUT, oversPerInnings = 5, playerEntry = EntryPolicy.APPROVAL)).tournament
        client.enter("asha", cup.id, strikers.id)
        client.enter("asha", cup.id, royals.id)

        // Dev asks to join; Asha hears, and puts him in the Strikers' squad.
        val asked = client.addPlayer("dev", cup.id).body<TournamentDetails>().tournament
        assertEquals(EntryStatus.PENDING, asked.entryOf("dev")!!.status)
        assertEquals(HttpStatusCode.Conflict, client.addPlayer("dev", cup.id).status)
        assertEquals(NotificationKind.ENTRY_REQUEST, client.inbox("asha").first().kind)
        assertEquals(HttpStatusCode.Forbidden, client.place("dev", cup.id, "dev", strikers.id).status)
        val placed = client.place("asha", cup.id, "dev", strikers.id).body<TournamentDetails>().tournament
        assertEquals(listOf("asha", "dev"), placed.squad(strikers.id).map { it.player.profileId })
        assertEquals(listOf(NotificationKind.SQUAD_PLACED), client.inbox("dev").map { it.kind })
        assertEquals("Strikers", client.inbox("dev").single().playerName)

        // One squad per tournament: Ravi can't put Dev in the Royals too, and can only add his own members.
        client.post("/teams/${royals.id}/members") { bearerAuth("test-ravi"); jsonBody(AddMemberRequest(profileId = "dev")) }
        val twice = client.addPlayer("ravi", cup.id, AddTournamentPlayerRequest("dev", royals.id))
        assertEquals(HttpStatusCode.Conflict, twice.status)
        assertTrue(twice.bodyAsText().contains("Strikers"))
        assertEquals(HttpStatusCode.Forbidden, client.addPlayer("ravi", cup.id, AddTournamentPlayerRequest("kabir", royals.id)).status)

        // Kabir joins and is turned down; the Strikers leaving sends Dev back to the pool, and Asha with them.
        client.addPlayer("kabir", cup.id)
        client.delete("/tournaments/${cup.id}/players/kabir") { bearerAuth("test-asha") }
        assertEquals(listOf(NotificationKind.ENTRY_REJECTED), client.inbox("kabir").map { it.kind })
        val left = client.delete("/tournaments/${cup.id}/teams/${strikers.id}") { bearerAuth("test-asha") }.body<TournamentDetails>().tournament
        assertEquals(listOf("dev"), left.pool.map { it.player.profileId })
        assertNull(left.entryOf("asha"))
        assertNull(left.entryOf("kabir"))
        // Dev's tournaments now include the cup, as a player.
        assertEquals(listOf("Cup"), client.get("/tournaments/mine") { bearerAuth("test-dev") }.body<List<TournamentSummary>>().map { it.name })
    }

    @Test
    fun fixturesOnlyFieldSquadPlayers() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        client.setUpProfile("kabir", name = "Kabir")
        val one = client.team("asha", "Team 1")
        val two = client.team("dev", "Team 2")
        client.post("/teams/${two.id}/members") { bearerAuth("test-dev"); jsonBody(AddMemberRequest(profileId = "kabir")) }
        val series = client.tournament("asha", CreateTournamentRequest("Series", format = TournamentFormat.BEST_OF_THREE, oversPerInnings = 5)).tournament
        client.enter("asha", series.id, one.id)
        client.enter("asha", series.id, two.id)
        // Kabir waits in the pool and never gets a squad; it's dropped when the series starts if still waiting.
        client.delete("/tournaments/${series.id}/players/kabir") { bearerAuth("test-dev") }
        client.addPlayer("kabir", series.id)
        val fixture = client.post("/tournaments/${series.id}/start") { bearerAuth("test-asha") }.body<TournamentDetails>().fixtures.single()
        assertNull(client.details(series.id).tournament.entryOf("kabir"))

        fun start(b: List<SquadPlayer>) = StartFixtureRequest(
            SquadRequest("Team 1", listOf(SquadPlayer("Asha", "asha"), SquadPlayer("Guest")), teamId = one.id),
            SquadRequest("Team 2", b, teamId = two.id),
            TeamSide.A,
            TossDecision.BAT,
        )
        val outsider = client.post("/matches/${fixture.id}/start") { bearerAuth("test-asha"); jsonBody(start(listOf(SquadPlayer("Dev", "dev"), SquadPlayer("Kabir", "kabir")))) }
        assertEquals(HttpStatusCode.BadRequest, outsider.status)
        assertTrue(outsider.bodyAsText().contains("Kabir"))
        // Guests can stand in.
        val started = client.post("/matches/${fixture.id}/start") { bearerAuth("test-asha"); jsonBody(start(listOf(SquadPlayer("Dev", "dev"), SquadPlayer("Guest 2")))) }
        assertEquals(HttpStatusCode.OK, started.status)
    }

    @Test
    fun organisersInviteAndEntrantsAcceptOrWithdraw() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        client.setUpProfile("kabir", name = "Kabir")
        val devs = client.team("dev", "Dev XI")
        val others = (1..2).map { client.team("asha", "Team $it") }
        val cup = client.tournament("asha", CreateTournamentRequest("Cup", format = TournamentFormat.KNOCKOUT, oversPerInnings = 5)).tournament
        suspend fun invite(teamId: String) = client.post("/tournaments/${cup.id}/teams") { bearerAuth("test-asha"); jsonBody(EnterTeamRequest(teamId, invite = true)) }

        // Asha invites Dev XI; only its admin can accept, and then it's in with its squad.
        assertEquals(EntryStatus.INVITED, invite(devs.id).body<TournamentDetails>().tournament.teams.single().status)
        assertEquals(listOf(NotificationKind.ENTRY_INVITED), client.inbox("dev").map { it.kind })
        assertEquals(HttpStatusCode.Forbidden, client.post("/tournaments/${cup.id}/teams/${devs.id}/accept") { bearerAuth("test-kabir") }.status)
        val accepted = client.post("/tournaments/${cup.id}/teams/${devs.id}/accept") { bearerAuth("test-dev") }.body<TournamentDetails>().tournament
        assertEquals(EntryStatus.CONFIRMED, accepted.teams.single().status)
        assertEquals(listOf("dev"), accepted.squad(devs.id).map { it.player.profileId })
        assertEquals(listOf(NotificationKind.ENTRY_JOINED), client.inbox("asha").map { it.kind })

        // Only organisers invite; Kabir accepts his own invite, then leaves.
        assertEquals(HttpStatusCode.Forbidden, client.addPlayer("dev", cup.id, AddTournamentPlayerRequest("kabir", invite = true)).status)
        client.addPlayer("asha", cup.id, AddTournamentPlayerRequest("kabir", invite = true))
        assertEquals(listOf(NotificationKind.ENTRY_INVITED), client.inbox("kabir").map { it.kind })
        assertEquals(HttpStatusCode.Forbidden, client.post("/tournaments/${cup.id}/players/kabir/accept") { bearerAuth("test-dev") }.status)
        val kabir = client.post("/tournaments/${cup.id}/players/kabir/accept") { bearerAuth("test-kabir") }.body<TournamentDetails>().tournament.entryOf("kabir")
        assertEquals(EntryStatus.CONFIRMED, kabir?.status)
        client.delete("/tournaments/${cup.id}/players/kabir") { bearerAuth("test-kabir") }

        // Dev withdraws his team; Asha hears both.
        assertEquals(HttpStatusCode.Forbidden, client.delete("/tournaments/${cup.id}/teams/${devs.id}") { bearerAuth("test-kabir") }.status)
        val withdrawn = client.delete("/tournaments/${cup.id}/teams/${devs.id}") { bearerAuth("test-dev") }.body<TournamentDetails>().tournament
        assertTrue(withdrawn.teams.isEmpty())
        assertEquals(listOf(NotificationKind.ENTRY_WITHDRAWN, NotificationKind.ENTRY_WITHDRAWN), client.inbox("asha").take(2).map { it.kind })

        // An invite nobody accepts doesn't play.
        invite(devs.id)
        others.forEach { client.enter("asha", cup.id, it.id) }
        val started = client.post("/tournaments/${cup.id}/start") { bearerAuth("test-asha") }.body<TournamentDetails>().tournament
        assertEquals(listOf("Team 1", "Team 2"), started.teams.map { it.name })
    }

    @Test
    fun fullTournamentsKeepAWaitingListAndEntriesClose() = apiTest { client ->
        for ((uid, name) in listOf("asha" to "Asha", "dev" to "Dev", "kabir" to "Kabir", "ravi" to "Ravi", "neel" to "Neel", "isha" to "Isha", "om" to "Om")) {
            client.setUpProfile(uid, name = name)
        }
        val devs = client.team("dev", "Dev XI")
        val kabirs = client.team("kabir", "Kabir XI")
        val ravis = client.team("ravi", "Ravi XI")
        val cup = client.tournament(
            "asha",
            CreateTournamentRequest(
                "Cup", format = TournamentFormat.KNOCKOUT, oversPerInnings = 5,
                teamEntry = EntryPolicy.OPEN, playerEntry = EntryPolicy.APPROVAL, maxTeams = 2, maxPlayers = 1,
            ),
        ).tournament
        client.enter("dev", cup.id, devs.id)
        client.enter("kabir", cup.id, kabirs.id)
        val full = client.enter("ravi", cup.id, ravis.id).body<TournamentDetails>().tournament
        assertEquals(EntryStatus.WAITLISTED, full.teams.single { it.teamId == ravis.id }.status)

        // Dev withdraws, so Ravi's team moves up and, entry being open, is straight in.
        val moved = client.delete("/tournaments/${cup.id}/teams/${devs.id}") { bearerAuth("test-dev") }.body<TournamentDetails>().tournament
        assertEquals(EntryStatus.CONFIRMED, moved.teams.single { it.teamId == ravis.id }.status)
        assertEquals(listOf("ravi"), moved.squad(ravis.id).map { it.player.profileId })
        assertEquals(listOf(NotificationKind.ENTRY_CONFIRMED), client.inbox("ravi").map { it.kind })

        // One player place: Neel's request takes it and Isha waits; turning Neel down moves her up to be confirmed.
        client.addPlayer("neel", cup.id)
        assertEquals(EntryStatus.WAITLISTED, client.addPlayer("isha", cup.id).body<TournamentDetails>().tournament.entryOf("isha")!!.status)
        val next = client.delete("/tournaments/${cup.id}/players/neel") { bearerAuth("test-asha") }.body<TournamentDetails>().tournament
        assertEquals(EntryStatus.PENDING, next.entryOf("isha")!!.status)
        assertEquals(listOf(NotificationKind.ENTRY_PROMOTED), client.inbox("isha").map { it.kind })
        assertEquals(NotificationKind.ENTRY_REQUEST, client.inbox("asha").first().kind)

        // Raising the limit lets the next one in; closing entry stops anyone new asking.
        val invalid = EntrySettingsRequest(EntryPolicy.OPEN, EntryPolicy.APPROVAL, maxTeams = 1)
        assertEquals(HttpStatusCode.BadRequest, client.put("/tournaments/${cup.id}/entry") { bearerAuth("test-asha"); jsonBody(invalid) }.status)
        val closed = EntrySettingsRequest(EntryPolicy.OPEN, EntryPolicy.APPROVAL, maxTeams = 2, maxPlayers = 1, entryClosesAt = 1)
        client.put("/tournaments/${cup.id}/entry") { bearerAuth("test-asha"); jsonBody(closed) }
        assertEquals(HttpStatusCode.Conflict, client.addPlayer("om", cup.id).status)
    }

    @Test
    fun tournamentsOpenToJoinAreListedByCitySoonestFirst() = apiTest { client ->
        client.setUpProfile("asha")
        fun request(name: String, city: String, startsAt: Long? = null, teams: EntryPolicy = EntryPolicy.APPROVAL, players: EntryPolicy = EntryPolicy.APPROVAL, closes: Long? = null) =
            CreateTournamentRequest(name, city, TournamentFormat.KNOCKOUT, 5, startsAt = startsAt, teamEntry = teams, playerEntry = players, entryClosesAt = closes)
        client.tournament("asha", request("Later Cup", "Pune", startsAt = 2_000_000_000_000))
        client.tournament("asha", request("Soon Cup", "pune", startsAt = 1_900_000_000_000, teams = EntryPolicy.ORGANISERS))
        client.tournament("asha", request("Undated Cup", "Pune"))
        client.tournament("asha", request("Invite Only", "Pune", teams = EntryPolicy.ORGANISERS, players = EntryPolicy.ORGANISERS))
        client.tournament("asha", request("Closed Cup", "Pune", closes = 1))
        client.tournament("asha", request("Mumbai Cup", "Mumbai"))
        val started = client.tournament("asha", request("Started Cup", "Pune", teams = EntryPolicy.ORGANISERS)).tournament
        (1..2).map { client.team("asha", "Team $it") }.forEach { client.enter("asha", started.id, it.id) }
        client.post("/tournaments/${started.id}/start") { bearerAuth("test-asha") }

        val pune = client.get("/tournaments/open?city=PUNE") { bearerAuth("test-asha") }.body<List<TournamentSummary>>()
        assertEquals(listOf("Soon Cup", "Later Cup", "Undated Cup"), pune.map { it.name })
        assertEquals(false to true, pune.first().takesTeams to pune.first().takesPlayers)
        assertEquals(4, client.get("/tournaments/open") { bearerAuth("test-asha") }.body<List<TournamentSummary>>().size)
    }

    @Test
    fun teamsAskPoolPlayersAndPlayersTakeOneOffer() = apiTest { client ->
        for ((uid, name) in listOf("asha" to "Asha", "dev" to "Dev", "ravi" to "Ravi", "kabir" to "Kabir")) client.setUpProfile(uid, name = name)
        val devs = client.team("dev", "Dev XI")
        val ravis = client.team("ravi", "Ravi XI")
        val cup = client.tournament(
            "asha",
            CreateTournamentRequest("Cup", format = TournamentFormat.KNOCKOUT, oversPerInnings = 5, playerEntry = EntryPolicy.OPEN, poolMode = PoolMode.TEAMS),
        ).tournament
        client.enter("asha", cup.id, devs.id)
        client.enter("asha", cup.id, ravis.id)
        client.addPlayer("kabir", cup.id)
        suspend fun ask(uid: String, teamId: String) =
            client.post("/tournaments/${cup.id}/players/kabir/offers") { bearerAuth("test-$uid"); jsonBody(SquadOfferRequest(teamId)) }

        assertEquals(HttpStatusCode.OK, ask("dev", devs.id).status)
        assertEquals(HttpStatusCode.Conflict, ask("dev", devs.id).status)
        assertEquals(HttpStatusCode.Forbidden, ask("kabir", ravis.id).status)
        val asked = ask("ravi", ravis.id).body<TournamentDetails>().tournament
        assertEquals(listOf(devs.id, ravis.id), asked.offers.map { it.teamId })
        assertEquals(listOf(NotificationKind.SQUAD_OFFER, NotificationKind.SQUAD_OFFER), client.inbox("kabir").map { it.kind })

        // Kabir takes Dev's offer: he's in that squad, Ravi's offer goes, and Dev hears.
        assertEquals(HttpStatusCode.Forbidden, client.post("/tournaments/${cup.id}/players/kabir/offers/${devs.id}/accept") { bearerAuth("test-dev") }.status)
        val taken = client.post("/tournaments/${cup.id}/players/kabir/offers/${devs.id}/accept") { bearerAuth("test-kabir") }.body<TournamentDetails>().tournament
        assertEquals(devs.id, taken.entryOf("kabir")!!.teamId)
        assertTrue(taken.offers.isEmpty())
        assertEquals(NotificationKind.SQUAD_JOINED, client.inbox("dev").first().kind)

        // Where organisers pick, teams can't ask.
        client.put("/tournaments/${cup.id}/entry") { bearerAuth("test-asha"); jsonBody(EntrySettingsRequest(EntryPolicy.APPROVAL, EntryPolicy.OPEN)) }
        client.place("asha", cup.id, "kabir", null)
        assertEquals(HttpStatusCode.Conflict, ask("ravi", ravis.id).status)
    }

    @Test
    fun organisersMakePickupTeamsFromThePool() = apiTest { client ->
        val people = listOf("dev", "kabir", "ravi", "neel")
        client.setUpProfile("asha")
        people.forEach { client.setUpProfile(it, name = it.replaceFirstChar(Char::uppercase)) }
        val cup = client.tournament("asha", CreateTournamentRequest("Sunday Pickup", format = TournamentFormat.ROUND_ROBIN, oversPerInnings = 5, playerEntry = EntryPolicy.OPEN)).tournament
        people.forEach { client.addPlayer(it, cup.id) }
        suspend fun pickup(uid: String, request: PickupTeamsRequest) = client.post("/tournaments/${cup.id}/pickup") { bearerAuth("test-$uid"); jsonBody(request) }

        assertEquals(HttpStatusCode.Forbidden, pickup("dev", PickupTeamsRequest(listOf("Reds", "Blues"))).status)
        assertEquals(HttpStatusCode.BadRequest, pickup("asha", PickupTeamsRequest(listOf("Reds", "reds"))).status)
        val made = pickup("asha", PickupTeamsRequest(listOf("Reds", "Blues"))).body<TournamentDetails>().tournament
        assertEquals(listOf("Reds", "Blues"), made.confirmedTeams.map { it.name })
        assertTrue(made.pool.isEmpty())
        assertEquals(listOf(2, 2), made.confirmedTeams.map { made.squad(it.teamId).size })
        assertEquals(listOf(NotificationKind.SQUAD_PLACED), client.inbox("dev").map { it.kind })
    }
}
