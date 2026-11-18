package com.harsh.desicrics

import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.navigation.JoinTeamRoute
import com.harsh.desicrics.navigation.MatchRoute
import com.harsh.desicrics.navigation.routeFor
import com.harsh.desicrics.ui.newmatch.SideForm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedLogicAndroidHostTest {

    @Test
    fun linksOpenTheRightScreen() {
        assertEquals(JoinTeamRoute("AB3XY9"), routeFor("/join/AB3XY9"))
        assertEquals(MatchRoute("m-1"), routeFor("/m/m-1/"))
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
}
