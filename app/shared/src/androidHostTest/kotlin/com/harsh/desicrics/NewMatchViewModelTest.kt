package com.harsh.desicrics

import com.harsh.desicrics.data.Analytics
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.ui.newmatch.NewMatchFormState
import com.harsh.desicrics.ui.newmatch.NewMatchViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The new match form against a fake server: each test answers the requests it cares about. */
@OptIn(ExperimentalCoroutinesApi::class)
class NewMatchViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val strikers = SavedTeam("t1", "Andheri Strikers", "Mumbai", "me", emptyList())

    private fun viewModel(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): NewMatchViewModel {
        val http = HttpClient(MockEngine(handler)) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val config = ApiConfig("http://test")
        val api = DesiCricsApi(http, { null }, config)
        return NewMatchViewModel(MatchRepository(api, Analytics.None), TeamRepository(api, config, Analytics.None))
    }

    private fun MockRequestHandleScope.json(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    /** The fake server answers off the test dispatcher, so this waits in real time. */
    private suspend fun NewMatchViewModel.awaitState(check: (NewMatchFormState) -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { state.first(check) } }

    @Test
    fun changeSquadOpensThatTeamsSquad() = runTest {
        val vm = viewModel { request ->
            assertEquals("/teams/t1", request.url.encodedPath)
            json(Json.encodeToString(SavedTeam.serializer(), strikers))
        }
        vm.update { it.copy(teamA = it.teamA.copy(name = strikers.name, teamId = strikers.id)) }
        vm.changeSquad(TeamSide.A)
        val state = vm.awaitState { it.squadTeam != null }
        assertEquals(strikers.id, state.squadTeam?.id)
        assertEquals(TeamSide.A, state.squadSide)
    }
}
