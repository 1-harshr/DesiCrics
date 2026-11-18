package com.harsh.desicrics

import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.ui.match.VoiceCommand
import com.harsh.desicrics.ui.match.VoiceCommands
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceCommandsTest {

    @Test
    fun runs() {
        assertEquals(VoiceCommand.Runs(4), VoiceCommands.parse("Four!"))
        assertEquals(VoiceCommand.Runs(6), VoiceCommands.parse("chhakka"))
        assertEquals(VoiceCommand.Runs(1), VoiceCommands.parse("single"))
        assertEquals(VoiceCommand.Runs(0), VoiceCommands.parse("dot ball"))
        assertEquals(VoiceCommand.Runs(2), VoiceCommands.parse("2"))
        assertEquals(VoiceCommand.Runs(4), VoiceCommands.parse("chauka"))
    }

    @Test
    fun extras() {
        assertEquals(VoiceCommand.Extra(ExtraType.WIDE, 0), VoiceCommands.parse("wide"))
        assertEquals(VoiceCommand.Extra(ExtraType.WIDE, 2), VoiceCommands.parse("two wides"))
        assertEquals(VoiceCommand.Extra(ExtraType.NO_BALL, 4), VoiceCommands.parse("no ball four"))
        assertEquals(VoiceCommand.Extra(ExtraType.LEG_BYE, 1), VoiceCommands.parse("leg bye one"))
        assertEquals(VoiceCommand.Extra(ExtraType.BYE, 2), VoiceCommands.parse("byes 2"))
        // Byes need a number of runs.
        assertEquals(VoiceCommand.Unknown, VoiceCommands.parse("bye"))
    }

    @Test
    fun wicketsUndoAndNonsense() {
        assertEquals(VoiceCommand.Wicket, VoiceCommands.parse("he's out"))
        assertEquals(VoiceCommand.Wicket, VoiceCommands.parse("caught behind"))
        assertEquals(VoiceCommand.Undo, VoiceCommands.parse("undo that"))
        assertEquals(VoiceCommand.Unknown, VoiceCommands.parse("what a lovely day"))
    }
}
