package com.harsh.desicrics.api

import kotlinx.serialization.Serializable

// Cities.
//
//   GET    /cities?q=                         → List<CitySuggestion>, best match first, at most CitySuggestion.LIMIT
//
// Cities are still free text: anything not in the list is kept as typed. The server saves the standard
// name of a city it knows ("Bombay" → "Mumbai"), so everyone's city is spelled the same.

@Serializable
data class CitySuggestion(val name: String, val state: String) {
    companion object {
        const val LIMIT = 8
    }
}
