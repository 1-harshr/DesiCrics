package com.harsh.desicrics.tournament

import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PlayerSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SquadSplitTest {

    private fun player(id: String, role: PlayerRole = PlayerRole.BATTER) = PlayerSummary(id, id, role = role)

    @Test
    fun sidesComeOutTheSameSizeAndCloseInStrength() {
        val strengths = mapOf("a" to 90, "b" to 80, "c" to 70, "d" to 60, "e" to 50, "f" to 40, "g" to 30)
        val placed = SquadSplit.split(strengths.keys.map { player(it) }, listOf("x", "y"), strength = { strengths.getValue(it.profileId) })
        val sides = placed.entries.groupBy({ it.value }, { strengths.getValue(it.key) })
        assertEquals(listOf(4, 3), listOf("x", "y").map { sides.getValue(it).size }.sortedDescending())
        assertTrue(kotlin.math.abs(sides.getValue("x").sum() - sides.getValue("y").sum()) <= 50)
    }

    @Test
    fun eachSideGetsAKeeperWhereThereAreEnough() {
        val players = listOf(player("k1", PlayerRole.WICKET_KEEPER), player("k2", PlayerRole.WICKET_KEEPER)) + (1..6).map { player("p$it") }
        val placed = SquadSplit.split(players, listOf("x", "y"), strength = { 0 })
        assertEquals(setOf("x", "y"), setOf(placed.getValue("k1"), placed.getValue("k2")))
        assertEquals(4, placed.values.count { it == "x" })
    }
}
