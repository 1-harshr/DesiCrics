package com.harsh.desicrics

import com.harsh.desicrics.api.AddMemberRequest
import com.harsh.desicrics.api.ClaimRequest
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.LeaderboardRow
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.Wicket
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsTest {

    /**
     * A one-over match: [batter] (linked) opens for Strikers with a guest, hits [runs] in fours (and a
     * single for any remainder), then is bowled; Royals' linked [bowler] takes the wicket. Royals are then
     * bowled out first ball, so it's complete.
     */
    private suspend fun HttpClient.playMatch(batter: Pair<String, String>, bowler: Pair<String, String>, runs: Int): String {
        val match = createMatch(
            "asha",
            teamA = SquadRequest("Strikers", listOf(SquadPlayer(batter.second, batter.first), SquadPlayer("Guest"))),
            teamB = SquadRequest("Royals", listOf(SquadPlayer(bowler.second, bowler.first), SquadPlayer("Other"))),
        ).match
        val (a1, a2) = match.teamA.players
        val (b1, b2) = match.teamB.players
        var seq = 0
        repeat(runs / 4) { record("asha", match.id, Delivery(seq++, 1, a1.id, a2.id, b1.id, runsOffBat = 4)) }
        record("asha", match.id, Delivery(seq++, 1, a1.id, a2.id, b1.id, wicket = Wicket(DismissalKind.BOWLED, a1.id)))
        assertEquals(HttpStatusCode.OK, record("asha", match.id, Delivery(seq, 2, b1.id, b2.id, a1.id, wicket = Wicket(DismissalKind.BOWLED, b1.id))).status)
        return match.id
    }

    private suspend fun HttpClient.stats(id: String, query: String = ""): CareerStats = get("/players/$id/stats$query") { bearerAuth("test-asha") }.body()

    @Test
    fun careerStatsAddUpCompletedMatches() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        client.playMatch("asha" to "Asha", "dev" to "Dev", runs = 12)
        client.playMatch("asha" to "Asha", "dev" to "Dev", runs = 4)

        val asha = client.stats("asha")
        assertEquals(2, asha.matches)
        assertEquals(16, asha.batting.runs)
        assertEquals(2, asha.batting.innings)
        assertEquals(12, asha.batting.highest)
        assertEquals(4, asha.batting.fours)
        // Asha bowled Royals' opener in each match too.
        assertEquals(2, asha.bowling.wickets)
        val dev = client.stats("dev")
        assertEquals(2, dev.bowling.wickets)
        assertEquals(2, asha.form.size)

        // Filters: these were one-over matches, so "short", and played this year.
        assertEquals(0, client.stats("asha", "?format=LONG").matches)
        assertEquals(2, client.stats("asha", "?format=SHORT").matches)
        assertEquals(0, client.stats("asha", "?season=1999").matches)

        val compared = client.get("/stats/compare?a=asha&b=dev") { bearerAuth("test-asha") }.body<List<CareerStats>>()
        assertEquals(listOf("asha", "dev"), compared.map { it.profileId })
    }

    @Test
    fun undoingTheLastBallTakesTheMatchOutOfStats() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        val match = client.playMatch("asha" to "Asha", "dev" to "Dev", runs = 8)
        assertEquals(1, client.stats("asha").matches)
        client.delete("/matches/$match/last") { bearerAuth("test-asha") }
        assertEquals(0, client.stats("asha").matches)
    }

    @Test
    fun leaderboardsByFriendsAndCity() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        client.setUpProfile("zoya", name = "Zoya")
        client.playMatch("dev" to "Dev", "zoya" to "Zoya", runs = 20)
        client.playMatch("zoya" to "Zoya", "dev" to "Dev", runs = 8)

        // Asha follows Dev only, so her friends board is Dev (and herself, with no runs).
        client.put("/follows/player/dev") { bearerAuth("test-asha") }
        val friends = client.get("/leaderboards?scope=FRIENDS&metric=RUNS") { bearerAuth("test-asha") }.body<List<LeaderboardRow>>()
        assertEquals(listOf("dev" to 20), friends.map { it.player.profileId to it.value })

        // Everyone set up a profile without a city; give two of them one.
        client.put("/me") { bearerAuth("test-dev"); jsonBody(UpdateProfileRequest("Dev", "+91 98765 43210", city = "Pune")) }
        client.put("/me") { bearerAuth("test-zoya"); jsonBody(UpdateProfileRequest("Zoya", "+91 98765 43210", city = "pune")) }
        val pune = client.get("/leaderboards?scope=CITY&id=Pune&metric=RUNS") { bearerAuth("test-asha") }.body<List<LeaderboardRow>>()
        assertEquals(listOf("dev", "zoya"), pune.map { it.player.profileId })
        val wickets = client.get("/leaderboards?scope=CITY&id=Pune&metric=wickets") { bearerAuth("test-asha") }.body<List<LeaderboardRow>>()
        // Each took a wicket in both matches: as Royals' bowler in one, bowling Royals out in the other.
        assertTrue(wickets.all { it.value == 2 })
        assertEquals(HttpStatusCode.BadRequest, client.get("/leaderboards?scope=MOON") { bearerAuth("test-asha") }.status)
    }

    @Test
    fun claimingAPlaceholderBringsItsStats() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        val team = client.post("/teams") { bearerAuth("test-asha"); jsonBody(TeamRequest("Strikers")) }.body<SavedTeam>()
        val kabir = client.post("/teams/${team.id}/members") { bearerAuth("test-asha"); jsonBody(AddMemberRequest(placeholderName = "Kabir")) }
            .body<SavedTeam>().members.single { it.player.isPlaceholder }
        client.playMatch(kabir.player.profileId to "Kabir", "dev" to "Dev", runs = 16)
        assertEquals(16, client.stats(kabir.player.profileId).batting.runs)

        client.setUpProfile("kabir", name = "Kabir Shah")
        client.post("/players/claim") { bearerAuth("test-kabir"); jsonBody(ClaimRequest(code = kabir.claimCode)) }
        assertEquals(16, client.stats("kabir").batting.runs)
    }
}
