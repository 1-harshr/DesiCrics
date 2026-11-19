package com.harsh.desicrics.data

/**
 * A few product events, so we can see which features get used. Names and parameters follow Firebase
 * Analytics rules (snake_case, at most 40 characters) and never carry names, numbers or other personal data.
 */
fun interface Analytics {
    fun log(event: String, params: Map<String, String>)

    companion object {
        /** For tests and previews, and anywhere analytics isn't set up. */
        val None = Analytics { _, _ -> }
    }
}

/** The events the app logs. */
object AnalyticsEvents {
    const val MATCH_CREATED = "match_created"
    const val FIXTURE_STARTED = "fixture_started"
    const val MATCH_ENDED_EARLY = "match_ended_early"
    const val TEAM_CREATED = "team_created"
    const val TEAM_JOINED = "team_joined"
    const val TOURNAMENT_CREATED = "tournament_created"
    const val PLAYER_CLAIMED = "player_claimed"
    const val SCORING_HANDED_OVER = "scoring_handed_over"
}
