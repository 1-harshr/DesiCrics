package com.harsh.desicrics.data

import com.harsh.desicrics.api.CitySuggestion
import com.harsh.desicrics.data.api.DesiCricsApi

/** Cities to suggest as someone types one. The server keeps the list; a city it doesn't know is still fine to save. */
class CityRepository(private val api: DesiCricsApi) {
    suspend fun suggest(query: String): List<CitySuggestion> = api.cities(query.trim())
}
