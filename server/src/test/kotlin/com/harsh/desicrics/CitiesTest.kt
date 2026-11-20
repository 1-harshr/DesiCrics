package com.harsh.desicrics

import com.harsh.desicrics.api.CitySuggestion
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.cities.Cities
import com.harsh.desicrics.cities.City
import com.harsh.desicrics.cities.standardiseCities
import com.harsh.desicrics.db.DatabaseFactory
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.UserProfile
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CitiesTest {

    private val cities = Cities.India

    @Test
    fun otherNamesBecomeTheStandardOne() {
        assertEquals("Mumbai", cities.canonical("Bombay"))
        assertEquals("Mumbai", cities.canonical("  mumbai "))
        assertEquals("Bengaluru", cities.canonical("bangalore"))
        assertEquals("Gurugram", cities.canonical("Gurgaon"))
        assertEquals("Chennai", cities.canonical("Madras"))
    }

    @Test
    fun anUnknownPlaceIsKeptAsTyped() {
        assertEquals("Shivaji Park", cities.canonical("  Shivaji   Park "))
        assertEquals("", cities.canonical("   "))
    }

    @Test
    fun aCitysOwnNameBeatsAnotherCitysOtherName() {
        val small = Cities(listOf(City("Big", "", 1000, listOf("Small")), City("Small", "", 10, emptyList())))
        assertEquals("Small", small.canonical("small"))
    }

    @Test
    fun suggestionsPutNamesFirstThenOtherNamesBiggestFirst() {
        assertEquals("Pune", cities.suggest("pun").first().name)
        assertEquals(CitySuggestion("Mumbai", "Maharashtra"), cities.suggest("bomb").first())
        // The same name in two states: both, the bigger one first.
        assertEquals(listOf("Maharashtra", "Bihar"), cities.suggest("aurangabad").filter { it.name == "Aurangabad" }.map { it.state })
        assertTrue(cities.suggest("m").isEmpty())
        assertEquals("Mumbai", cities.suggest("").first().name)
        assertEquals(CitySuggestion.LIMIT, cities.suggest(" ").size)
        assertTrue(cities.suggest("ma").size <= CitySuggestion.LIMIT)
    }

    @Test
    fun theAppGetsSuggestionsAndSavesTheStandardName() = apiTest { client ->
        client.setUpProfile("asha")
        val suggested = client.get("/cities?q=bangal") { bearerAuth("test-asha") }.body<List<CitySuggestion>>()
        assertEquals("Bengaluru", suggested.first().name)
        client.put("/me") { bearerAuth("test-asha"); jsonBody(com.harsh.desicrics.api.UpdateProfileRequest(name = "Asha", phone = "+91 98765 43210", city = "bombay")) }
        assertEquals("Mumbai", client.get("/me") { bearerAuth("test-asha") }.body<UserProfile>().city)
        val team = client.post("/teams") { bearerAuth("test-asha"); jsonBody(TeamRequest("Strikers", "Poona")) }.body<SavedTeam>()
        assertEquals("Pune", team.city)
    }

    @Test
    fun citiesSavedBeforeTheListAreStandardised() {
        val config = freshDatabase()
        val db = DatabaseFactory.connect(config)
        try {
            DriverManager.getConnection(config.url, config.user, config.password).use { connection ->
                connection.createStatement().use {
                    it.execute("INSERT INTO users (uid, name, phone, city, role, batting_style, bowling_style, created_at, code) VALUES ('u1', 'Asha', '', 'bombay ', 'BATTER', 'RIGHT_HAND', 'NONE', 0, '10000001')")
                    it.execute("INSERT INTO users (uid, name, phone, city, role, batting_style, bowling_style, created_at, code) VALUES ('u2', 'Dev', '', 'Kothrud', 'BATTER', 'RIGHT_HAND', 'NONE', 0, '10000002')")
                }
            }
            assertEquals(1, runBlocking { standardiseCities(db.database) })
            assertEquals(0, runBlocking { standardiseCities(db.database) })
            val saved = DriverManager.getConnection(config.url, config.user, config.password).use { connection ->
                connection.createStatement().use { s -> s.executeQuery("SELECT city FROM users ORDER BY uid").use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } } }
            }
            assertEquals(listOf("Mumbai", "Kothrud"), saved)
        } finally {
            db.close()
        }
    }
}
