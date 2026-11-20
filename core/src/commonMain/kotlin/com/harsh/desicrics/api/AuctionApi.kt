package com.harsh.desicrics.api

import com.harsh.desicrics.model.AuctionSettings
import kotlinx.serialization.Serializable

// Player auctions. Every write returns the auction's new AuctionDetails, and the live feed sends the same to
// everyone watching. Organisers are the tournament's; owners are the admins of its confirmed teams.
//
//   GET    /tournaments/{id}/auction             → AuctionDetails (404 if it has none)
//   POST   /tournaments/{id}/auction             CreateAuctionRequest → AuctionDetails (organisers)
//   GET    /auctions/{id}                        → AuctionDetails
//   PUT    /auctions/{id}/settings               AuctionSettings → AuctionDetails (organisers, before the start)
//   POST   /auctions/{id}/players                AddAuctionPlayerRequest → AuctionDetails (organisers)
//   PUT    /auctions/{id}/players/{profileId}    AuctionPlayerRequest → AuctionDetails (organisers; accepts a request)
//   DELETE /auctions/{id}/players/{profileId}    → AuctionDetails (organisers; declines a request, or removes a player not yet up)
//   POST   /auctions/{id}/requests               → AuctionDetails (any player asking to be in the pool)
//   DELETE /auctions/{id}/requests               → AuctionDetails (withdraws the caller's request)
//   POST   /auctions/{id}/start                  → AuctionDetails (organisers)
//   POST   /auctions/{id}/next                   NextPlayerRequest → AuctionDetails (organisers)
//   POST   /auctions/{id}/bids                   BidRequest → AuctionDetails (Hall: organisers; Online: that team's owners)
//   POST   /auctions/{id}/sold                   → AuctionDetails (organisers; to the highest bid)
//   POST   /auctions/{id}/unsold                 → AuctionDetails (organisers)
//   POST   /auctions/{id}/undo                   → AuctionDetails (organisers)
//   POST   /auctions/{id}/pause                  → AuctionDetails (organisers)
//   POST   /auctions/{id}/resume                 → AuctionDetails (organisers)
//   POST   /auctions/{id}/reverse                ReverseSaleRequest → AuctionDetails (organisers)
//   POST   /auctions/{id}/finish                 → AuctionDetails (organisers; sold players join their teams)
//   WS     /auctions/{id}/live                   → AuctionDetails now, then on every change

@Serializable
data class CreateAuctionRequest(val settings: AuctionSettings = AuctionSettings())

/**
 * Adds a signed-up player by [profileId] (found by search or code), or a placeholder by [placeholderName], at
 * [basePrice] (the auction's base price if null) in an optional [category].
 */
@Serializable
data class AddAuctionPlayerRequest(
    val profileId: String? = null,
    val placeholderName: String? = null,
    val placeholderPhone: String? = null,
    val basePrice: Int? = null,
    val category: String? = null,
) {
    fun validate(): AuctionRequestError? = when {
        (profileId == null) == (placeholderName == null) -> AuctionRequestError.PLAYER_INVALID
        placeholderName != null && (placeholderName.isBlank() || placeholderName.trim().length > CreateMatchRequest.MAX_NAME) ->
            AuctionRequestError.PLAYER_INVALID
        placeholderPhone != null && placeholderPhone.isNotBlank() && placeholderPhone.count { it.isDigit() } !in 10..13 ->
            AuctionRequestError.PHONE_INVALID
        basePrice != null && basePrice < 1 -> AuctionRequestError.PRICE_INVALID
        category != null && category.trim().length > 30 -> AuctionRequestError.CATEGORY_INVALID
        else -> null
    }
}

/** A pool player's base price and category, and the team keeping them before the auction, at [retainedPrice]. */
@Serializable
data class AuctionPlayerRequest(
    val basePrice: Int,
    val category: String? = null,
    val retainedBy: String? = null,
    val retainedPrice: Int? = null,
) {
    fun validate(): AuctionRequestError? = when {
        basePrice < 1 -> AuctionRequestError.PRICE_INVALID
        category != null && category.trim().length > 30 -> AuctionRequestError.CATEGORY_INVALID
        (retainedBy == null) != (retainedPrice == null) || (retainedPrice != null && retainedPrice < 0) -> AuctionRequestError.RETAINED_INVALID
        else -> null
    }
}

/** Brings [profileId] up, or whoever is up next when null. */
@Serializable
data class NextPlayerRequest(val profileId: String? = null)

/**
 * A bid for [teamId]. Without an [amount] it's the least the next bid can be, which is what the hall's
 * tap-a-team does. Online, send the amount the owner saw, so two bids at once can't push it higher than meant.
 */
@Serializable
data class BidRequest(val teamId: String, val amount: Int? = null)

@Serializable
data class ReverseSaleRequest(val profileId: String)

enum class AuctionRequestError(val message: String) {
    PLAYER_INVALID("Pick a player, or enter a name"),
    PHONE_INVALID("Enter a 10-digit phone number"),
    PRICE_INVALID("The base price has to be at least 1"),
    CATEGORY_INVALID("Keep the category under 30 characters"),
    RETAINED_INVALID("A retained player needs a team and a price"),
}
