package com.harsh.desicrics.api

import com.harsh.desicrics.model.DirectoryKind
import com.harsh.desicrics.model.LookingForKind
import kotlinx.serialization.Serializable

// Community (Phase 6).
//
//   GET    /looking-for?city=&kind=           → List<LookingForPost>, open and not past, soonest first
//   GET    /looking-for/mine                  → List<LookingForPost> with responses
//   POST   /looking-for                       LookingForRequest → LookingForPost
//   POST   /looking-for/{id}/responses        RespondRequest → LookingForPost
//   POST   /looking-for/{id}/close            → LookingForPost (author)
//   GET    /directory?city=&kind=             → List<DirectoryEntry>
//   POST   /directory                         DirectoryRequest → DirectoryEntry
//   DELETE /directory/{id}                    → 204 (whoever added it)
//   GET    /rankings/teams?city=              → List<TeamRanking>
//   GET    /rankings/players?city=            → List<PlayerRanking>
//   GET    /overlay/{matchId}                 → public HTML score bug for OBS / Larix
//   GET    /m/{matchId}/overlay.json          → OverlayScore, public

@Serializable
data class LookingForRequest(
    val kind: LookingForKind,
    val city: String,
    val date: Long,
    val note: String = "",
    val teamId: String? = null,
) {
    fun validate(): String? = when {
        city.isBlank() || city.length > 100 -> "Say which city"
        note.length > MAX_NOTE -> "That note is too long"
        else -> null
    }

    companion object {
        const val MAX_NOTE = 500
    }
}

@Serializable
data class RespondRequest(val message: String, val phone: String) {
    fun validate(): String? = when {
        message.isBlank() || message.length > LookingForRequest.MAX_NOTE -> "Write a short message"
        phone.count { it.isDigit() } !in 10..13 -> "Please enter a valid mobile number"
        else -> null
    }
}

@Serializable
data class DirectoryRequest(val kind: DirectoryKind, val name: String, val city: String, val phone: String = "", val notes: String = "") {
    fun validate(): String? = when {
        name.isBlank() || name.length > 100 -> "Give it a name"
        city.isBlank() || city.length > 100 -> "Say which city"
        phone.isNotBlank() && phone.count { it.isDigit() } !in 10..13 -> "Please enter a valid mobile number"
        notes.length > LookingForRequest.MAX_NOTE -> "Those notes are too long"
        else -> null
    }
}
