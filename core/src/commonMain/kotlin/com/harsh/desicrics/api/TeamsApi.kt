package com.harsh.desicrics.api

import com.harsh.desicrics.model.TeamRole
import kotlinx.serialization.Serializable

// Teams, players and the account.
//
//   GET    /teams/mine                          → List<TeamSummary>
//   POST   /teams                               TeamRequest → SavedTeam (the caller is its first admin)
//   GET    /teams/{id}                          → SavedTeam
//   PUT    /teams/{id}                          TeamRequest → SavedTeam (admins)
//   POST   /teams/{id}/members                  AddMemberRequest → SavedTeam (admins)
//   PUT    /teams/{id}/members/{profileId}      SetRoleRequest → SavedTeam (admins)
//   DELETE /teams/{id}/members/{profileId}      → SavedTeam (admins, or a member leaving)
//   POST   /teams/{id}/join-code                → SavedTeam with a new join code (admins)
//   POST   /teams/join/{code}                   → SavedTeam (the caller joins as a member)
//   GET    /teams/{id}/head-to-head/{otherId}   → HeadToHead
//   GET    /players/search?q=                   → List<PlayerSummary> (users only)
//   GET    /players/claimable                   → List<PlayerSummary> (placeholders with the caller's verified phone)
//   POST   /players/claim                       ClaimRequest → PlayerSummary (the caller's own)
//   GET    /me/matches                          → List<Match> the caller played in or scored
//   DELETE /me                                  → 204; the account's personal data is removed
//   POST   /support                             SupportRequest → 204

@Serializable
data class TeamRequest(val name: String, val city: String = "", val lineup: List<SquadPlayer> = emptyList()) {
    fun validate(): TeamError? = when {
        lineup.size > CreateMatchRequest.MAX_PLAYERS || lineup.any { it.name.isBlank() || it.name.length > CreateMatchRequest.MAX_NAME } -> TeamError.MEMBER_INVALID
        name.isBlank() -> TeamError.NAME_MISSING
        name.trim().length > CreateMatchRequest.MAX_NAME -> TeamError.NAME_TOO_LONG
        city.length > 100 -> TeamError.CITY_TOO_LONG
        else -> null
    }
}

/**
 * Adds a signed-up player by [profileId], or a placeholder for someone without the app by
 * [placeholderName] (and, optionally, the phone number that lets them claim it automatically).
 */
@Serializable
data class AddMemberRequest(
    val profileId: String? = null,
    val placeholderName: String? = null,
    val placeholderPhone: String? = null,
) {
    fun validate(): TeamError? = when {
        (profileId == null) == (placeholderName == null) -> TeamError.MEMBER_INVALID
        placeholderName != null && (placeholderName.isBlank() || placeholderName.trim().length > CreateMatchRequest.MAX_NAME) ->
            TeamError.MEMBER_INVALID
        placeholderPhone != null && placeholderPhone.isNotBlank() && placeholderPhone.count { it.isDigit() } !in 10..13 ->
            TeamError.PHONE_INVALID
        else -> null
    }
}

@Serializable
data class SetRoleRequest(val role: TeamRole)

/** Claims a placeholder, by the code an admin shared or (with neither) the one matching the caller's verified phone. */
@Serializable
data class ClaimRequest(val code: String? = null, val profileId: String? = null)

@Serializable
enum class SupportCategory { BUG, IDEA, ACCOUNT, OTHER }

@Serializable
data class SupportRequest(val category: SupportCategory, val message: String, val appVersion: String = "") {
    fun validate(): String? = when {
        message.isBlank() -> "Tell us what happened"
        message.length > MAX_MESSAGE -> "That message is too long"
        else -> null
    }

    companion object {
        const val MAX_MESSAGE = 4000
    }
}

enum class TeamError(val message: String) {
    NAME_MISSING("Give the team a name"),
    NAME_TOO_LONG("Team names must be 60 characters or fewer"),
    CITY_TOO_LONG("That city name is too long"),
    MEMBER_INVALID("Pick a player or type a name"),
    PHONE_INVALID("Please enter a valid mobile number"),
}

// Following, notifications, search and live scores.
//
//   GET    /feed                          → TodayFeed
//   GET    /search?q=                     → SearchResults
//   GET    /players/{id}                  → PlayerPage
//   GET    /follows                       → List<Follow>
//   PUT    /follows/{type}/{id}           → 204 (follow a TEAM or PLAYER)
//   DELETE /follows/{type}/{id}           → 204
//   GET    /notifications                 → List<Notification>, newest first
//   POST   /notifications/read            → 204 (marks them all read)
//   PUT    /me/push-token                 PushTokenRequest → 204
//   WS     /matches/{id}/live             → MatchDetails after every change
//   GET    /m/{id}                        → public HTML scorecard, no sign-in

@Serializable
data class PushTokenRequest(val token: String, val platform: String = "android")
