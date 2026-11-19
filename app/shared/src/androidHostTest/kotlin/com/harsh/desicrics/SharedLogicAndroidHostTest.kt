package com.harsh.desicrics

import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.data.api.ApiException
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.navigation.JoinTeamRoute
import com.harsh.desicrics.navigation.MatchRoute
import com.harsh.desicrics.navigation.TournamentRoute
import com.harsh.desicrics.navigation.routeFor
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.ui.components.publicCodeIn
import com.harsh.desicrics.ui.match.Selection
import com.harsh.desicrics.ui.match.stillValid
import com.harsh.desicrics.ui.newmatch.NewMatchFormState
import com.harsh.desicrics.ui.newmatch.SideForm
import com.harsh.desicrics.ui.teams.refusal
import com.harsh.desicrics.ui.text.PickerMoment
import com.harsh.desicrics.ui.text.epochMillisAt
import com.harsh.desicrics.ui.text.pickerMoment
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedLogicAndroidHostTest {

    @Test
    fun linksOpenTheRightScreen() {
        assertEquals(JoinTeamRoute("AB3XY9"), routeFor("/join/AB3XY9"))
        assertEquals(MatchRoute("m-1"), routeFor("/m/m-1/"))
        assertEquals(TournamentRoute("t-1"), routeFor("/t/t-1"))
        assertNull(routeFor("/join/"))
        assertNull(routeFor("/somewhere/else"))
    }

    @Test
    fun removingAPlayerKeepsTheCaptainAndKeeper() {
        val side = SideForm("Strikers", listOf("A", "B", "C", "D").map { SquadPlayer(it) }, captain = 3, keeper = 1)
        val withoutA = side.without(0)
        assertEquals("D", withoutA.players[withoutA.captain!!].name)
        assertEquals("B", withoutA.players[withoutA.keeper!!].name)
        assertNull(side.without(3).captain)
    }

    @Test
    fun aSavedTeamIsntOfferedToBothSides() {
        val teams = listOf(TeamSummary("t1", "Andheri Strikers", "Mumbai", 11), TeamSummary("t2", "Powai XI", "Mumbai", 9))
        val form = NewMatchFormState(teamA = SideForm("Andheri Strikers", teamId = "t1"))
        assertEquals(listOf("t2"), form.pickable(TeamSide.B, teams).map { it.id })
        assertEquals(listOf("t1", "t2"), form.pickable(TeamSide.A, teams).map { it.id })
        val other = NewMatchFormState(teamB = SideForm("Powai XI", teamId = "t2"))
        assertEquals(listOf("t1"), other.pickable(TeamSide.A, teams).map { it.id })
    }

    @Test
    fun onlyTheServersRefusalsAreShownAsTheyAre() {
        assertEquals("Make someone else an admin first", ApiException(HttpStatusCode.Conflict, "Make someone else an admin first").refusal())
        assertEquals("Only team admins can do that", ApiException(HttpStatusCode.Forbidden, "Only team admins can do that").refusal())
        assertNull(ApiException(HttpStatusCode.InternalServerError, "Something went wrong on our side").refusal())
        assertNull(IOException("timeout").refusal())
    }

    @Test
    fun aTeamsPublicCodeIsToldApartFromAnInviteCode() {
        assertEquals("02407967", publicCodeIn("0240 7967"))
        assertEquals("02407967", publicCodeIn("0240-7967"))
        assertNull(publicCodeIn("AB3XY9"))
        assertNull(publicCodeIn("0240796"))
        assertNull(publicCodeIn("AB3XY967"))
    }

    @Test
    fun aScheduledTimeOpensThePickersOnItsOwnDayAndTime() {
        val day = 1_795_305_600_000L // 22 Nov 2026, as a date picker gives it (UTC midnight).
        assertEquals(PickerMoment(day, 18, 45), pickerMoment(epochMillisAt(day, 18, 45)))
        // Just after midnight, where the UTC day would be the one before east of Greenwich.
        assertEquals(PickerMoment(day, 0, 15), pickerMoment(epochMillisAt(day, 0, 15)))
    }

    @Test
    fun undoingABallHandsBackTheBowlerItCarriedWhenNobodyIsBowling() {
        val strikers = Team("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir").mapIndexed { i, n -> Player("a$i", n) })
        val royals = Team("Powai XI", listOf("Esha", "Farhan", "Gaurav").mapIndexed { i, n -> Player("b$i", n) })
        val match = Match("m", "me", 0, strikers, royals, 5, TeamSide.A, TossDecision.BAT, scorerUid = "me")
        val first = Delivery(0, 1, "a0", "a1", "b0")
        // A bowler change from before changes carried the new bowler leaves nobody bowling.
        val cleared = listOf(MatchEvent.ReplaceBowler(1, 1))
        val second = Delivery(2, 1, "a0", "a1", "b1")
        val afterUndo = ScoringEngine.compute(match, listOf(first), cleared).current
        assertNull(afterUndo.bowlerId)
        assertEquals("b1", Selection().stillValid(afterUndo, ballAdded = false, undone = second).bowlerId)
        // A pick made since wins, and a bowler the scorecard already has needs no pick.
        assertEquals("b2", Selection(bowlerId = "b2").stillValid(afterUndo, ballAdded = false, undone = second).bowlerId)
        val bowling = ScoringEngine.compute(match, listOf(first)).current
        assertNull(Selection().stillValid(bowling, ballAdded = false, undone = second).bowlerId)
    }
}
