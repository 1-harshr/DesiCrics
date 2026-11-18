package com.harsh.desicrics.ui.match

import com.harsh.desicrics.model.ExtraType

/** What the scorer said, as an action on the run pad. */
sealed interface VoiceCommand {
    data class Runs(val runs: Int) : VoiceCommand
    /** [runs] is what the batters ran (wide, byes) or hit (no-ball), as the extra sheets take it. */
    data class Extra(val type: ExtraType, val runs: Int) : VoiceCommand
    /** Opens the wicket sheet: how out and who are too much to trust to speech. */
    data object Wicket : VoiceCommand
    data object Undo : VoiceCommand
    data object Unknown : VoiceCommand
}

/**
 * Turns what the speech recogniser heard into a [VoiceCommand]. Understands English and the Hindi words
 * scorers shout at the ground ("chauka", "chhakka", "ek", "do"), in whatever order they come: "wide",
 * "two wides", "no ball four", "leg bye one", "dot", "single", "out".
 */
object VoiceCommands {

    private val numbers = mapOf(
        "zero" to 0, "dot" to 0, "nothing" to 0, "no run" to 0, "shunya" to 0,
        "one" to 1, "single" to 1, "won" to 1, "ek" to 1,
        "two" to 2, "double" to 2, "to" to 2, "too" to 2, "do" to 2,
        "three" to 3, "teen" to 3, "tin" to 3,
        "four" to 4, "boundary" to 4, "for" to 4, "chauka" to 4, "chaukka" to 4, "char" to 4,
        "five" to 5, "paanch" to 5, "panch" to 5,
        "six" to 6, "sixer" to 6, "chhakka" to 6, "chakka" to 6, "chhe" to 6,
    )

    fun parse(heard: String): VoiceCommand {
        val text = " " + heard.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim() + " "
        fun has(vararg words: String) = words.any { " $it " in text }
        val digits = Regex("\\b([0-7])\\b").find(text)?.groupValues?.get(1)?.toInt()
        // Longest phrases first, so "no run" wins over "no".
        val spoken = numbers.entries.sortedByDescending { it.key.length }.firstOrNull { " ${it.key} " in text }?.value
        val runs = digits ?: spoken

        return when {
            has("undo", "galat", "wrong", "cancel") -> VoiceCommand.Undo
            has("out", "wicket", "bowled", "caught", "lbw", "stumped", "run out") -> VoiceCommand.Wicket
            has("wide", "wides", "wide ball") -> VoiceCommand.Extra(ExtraType.WIDE, runs ?: 0)
            has("no ball", "noball", "no balls") -> VoiceCommand.Extra(ExtraType.NO_BALL, runs ?: 0)
            has("leg bye", "leg byes", "legbye") -> runs?.takeIf { it in 1..4 }?.let { VoiceCommand.Extra(ExtraType.LEG_BYE, it) } ?: VoiceCommand.Unknown
            has("bye", "byes") -> runs?.takeIf { it in 1..4 }?.let { VoiceCommand.Extra(ExtraType.BYE, it) } ?: VoiceCommand.Unknown
            runs != null && runs in 0..6 -> VoiceCommand.Runs(runs)
            else -> VoiceCommand.Unknown
        }
    }
}
