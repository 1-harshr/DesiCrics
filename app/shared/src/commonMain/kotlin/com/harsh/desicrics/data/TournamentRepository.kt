package com.harsh.desicrics.data
import com.harsh.desicrics.api.EndBeforeStartRequest

import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.api.CustomFixtureRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.StartFixtureRequest
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.model.TournamentSummary

class TournamentRepository(private val api: DesiCricsApi, private val config: ApiConfig, private val analytics: Analytics) {

    suspend fun mine(): List<TournamentSummary> = api.myTournaments()

    suspend fun create(request: CreateTournamentRequest): TournamentDetails =
        api.createTournament(request).also { analytics.log(AnalyticsEvents.TOURNAMENT_CREATED, mapOf("format" to request.format.name.lowercase())) }

    suspend fun details(id: String): TournamentDetails = api.tournament(id)

    suspend fun enterTeam(id: String, teamId: String): TournamentDetails = api.enterTeam(id, teamId)

    suspend fun review(id: String, teamId: String, confirm: Boolean): TournamentDetails = api.reviewEntry(id, teamId, confirm)

    suspend fun addOrganiser(id: String, profileId: String): TournamentDetails = api.addOrganiser(id, profileId)

    suspend fun start(id: String): TournamentDetails = api.startTournament(id)

    suspend fun addFixture(id: String, teamAId: String, teamBId: String): TournamentDetails = api.addFixture(id, CustomFixtureRequest(teamAId, teamBId))

    suspend fun schedule(matchId: String, scheduledAt: Long?): MatchDetails = api.schedule(matchId, scheduledAt)

    suspend fun startFixture(matchId: String, request: StartFixtureRequest): MatchDetails =
        api.startFixture(matchId, request).also { analytics.log(AnalyticsEvents.FIXTURE_STARTED, emptyMap()) }

    suspend fun endBeforeStart(matchId: String, request: EndBeforeStartRequest): MatchDetails =
        api.endBeforeStart(matchId, request).also { analytics.log(AnalyticsEvents.MATCH_ENDED_EARLY, mapOf("ending" to request.ending.name.lowercase())) }

    /** The public page anyone can open. */
    fun link(id: String): String = "${config.publicUrl.trimEnd('/')}/t/$id"
}
