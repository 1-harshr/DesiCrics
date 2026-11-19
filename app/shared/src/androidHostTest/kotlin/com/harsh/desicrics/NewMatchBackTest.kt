package com.harsh.desicrics

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.ui.newmatch.NewMatchActions
import com.harsh.desicrics.ui.newmatch.NewMatchContent
import com.harsh.desicrics.ui.newmatch.NewMatchFormState
import com.harsh.desicrics.ui.newmatch.SideForm
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Back in the new match form steps back, and only leaves a filled-in form once the user says so. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class NewMatchBackTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        // Avatars look up the server address to load photos.
        startKoin { modules(module { single { ApiConfig("http://localhost") } }) }
    }

    @After
    fun tearDown() = stopKoin()

    private val filled = NewMatchFormState(
        teamA = SideForm("Andheri Strikers", listOf(SquadPlayer("Rohan"), SquadPlayer("Aarav"))),
        teamB = SideForm("Powai XI", listOf(SquadPlayer("Esha"), SquadPlayer("Farhan"))),
    )

    private fun show(state: NewMatchFormState, step: Int = 0): () -> Int {
        var left = 0
        compose.setContent {
            DesiTheme(darkTheme = false) {
                NewMatchContent(state, NewMatchActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, { left++ }), initialStep = step)
            }
        }
        return { left }
    }

    @Test
    fun backFromAnEmptyFormLeaves() {
        val left = show(NewMatchFormState())
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, left())
    }

    @Test
    fun backFromAFilledFormAsksFirst() {
        val left = show(filled)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(0, left())
        compose.onNodeWithText("Discard this match?").assertExists()
        compose.onNodeWithText("Discard").performClick()
        assertEquals(1, left())
    }

    @Test
    fun editingALineupFromDetailsReturnsThere() {
        val left = show(filled, step = 2)
        compose.onAllNodesWithContentDescription("Edit lineup")[0].performClick()
        compose.onNodeWithText("Team A").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Match details").assertExists()
        assertEquals(0, left())
    }

    @Test
    fun formsWithInputAreNoticed() {
        assertFalse(NewMatchFormState().hasInput)
        assertFalse(NewMatchFormState(myTeams = emptyList(), myTeamsFailed = true).hasInput)
        assertTrue(NewMatchFormState(venue = "Oval Maidan").hasInput)
        assertTrue(filled.hasInput)
    }
}
