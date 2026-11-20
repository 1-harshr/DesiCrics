package com.harsh.desicrics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harsh.desicrics.data.CityRepository
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.ui.onboarding.OnboardingActions
import com.harsh.desicrics.ui.onboarding.OnboardingContent
import com.harsh.desicrics.ui.onboarding.OnboardingStep
import com.harsh.desicrics.ui.onboarding.OnboardingUiState
import com.harsh.desicrics.ui.profile.ProfileFormState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The onboarding city step lists popular cities before anything's typed, then matches as you type; tapping one fills it in. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class OnboardingCityTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        val http = HttpClient(MockEngine { request ->
            val body = if (request.url.parameters["q"].isNullOrBlank()) {
                """[{"name":"Mumbai","state":"Maharashtra"},{"name":"Delhi","state":"Delhi"}]"""
            } else {
                """[{"name":"Aurangabad","state":"Maharashtra"}]"""
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val config = ApiConfig("http://test")
        startKoin { modules(module { single { config }; single { CityRepository(DesiCricsApi(http, { null }, config)) } }) }
    }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun listsPopularCitiesBeforeTyping() {
        var form by mutableStateOf(ProfileFormState(loading = false, name = "Rohan"))
        compose.setContent {
            DesiTheme(darkTheme = false) {
                OnboardingContent(form, OnboardingUiState(step = OnboardingStep.CITY), OnboardingActions(onUpdate = { form = it(form) }))
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Popular cities") }
        compose.onNodeWithText("Delhi").performClick()
        compose.waitForIdle()
        assertEquals("Delhi", form.city)
        assertEquals(false, compose.onAllNodesWithTextExists("Popular cities"))
    }

    @Test
    fun suggestsCities() {
        var form by mutableStateOf(ProfileFormState(loading = false, name = "Rohan"))
        compose.setContent {
            DesiTheme(darkTheme = false) {
                OnboardingContent(form, OnboardingUiState(step = OnboardingStep.CITY), OnboardingActions(onUpdate = { form = it(form) }))
            }
        }
        compose.onNodeWithText("City (optional)").performClick()
        compose.onNodeWithText("City (optional)").performTextInput("Aura")
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Maharashtra") }
        compose.onNodeWithText("Maharashtra").performClick()
        compose.waitForIdle()
        assertEquals("Aurangabad", form.city)
        // Cleared again: back to popular cities, never the old matches under that heading.
        compose.onNodeWithText("Aurangabad").performTextClearance()
        assertEquals(false, compose.onAllNodesWithTextExists("Aurangabad"))
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Mumbai") }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextExists(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
}
