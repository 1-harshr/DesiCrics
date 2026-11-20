package com.harsh.desicrics.cities

import com.harsh.desicrics.api.CitySuggestion

/** A city in the list: its standard name, its state, how many people live there, and other ways it's written. */
data class City(val name: String, val state: String, val population: Int, val aliases: List<String>)

/**
 * India's cities, from GeoNames (see scripts/build-cities.sh). Suggests cities as someone types, and turns any
 * name the list knows ("bombay ", "Bangalore") into its standard one, so the same city is always spelled the
 * same way and city pages, rankings and "open to join" find each other.
 */
class Cities(cities: List<City>) {

    private val byPopulation = cities.sortedByDescending { it.population }

    /** Every known spelling to its city. A city's own name wins over another city's other name. */
    private val known: Map<String, City> = buildMap {
        byPopulation.forEach { city -> city.aliases.forEach { putIfAbsent(key(it), city) } }
        byPopulation.asReversed().forEach { put(key(it.name), it) }
    }

    /** The standard name for [raw], or [raw] tidied up (trimmed, single spaces) if it isn't a city we know. */
    fun canonical(raw: String): String {
        val tidy = raw.trim().replace(WHITESPACE, " ")
        return known[key(tidy)]?.name ?: tidy
    }

    /**
     * Cities matching what's been typed: names starting with it first, then other names starting with it,
     * then names with a word starting with it. Bigger cities first within each. Nothing typed yet gives the
     * biggest cities, to pick from without typing.
     */
    fun suggest(query: String, limit: Int = CitySuggestion.LIMIT): List<CitySuggestion> {
        val q = key(query)
        if (q.isEmpty()) return byPopulation.take(limit).map { CitySuggestion(it.name, it.state) }
        if (q.length < MIN_QUERY) return emptyList()
        fun rank(city: City): Int? = when {
            key(city.name).startsWith(q) -> 0
            city.aliases.any { key(it).startsWith(q) } -> 1
            key(city.name).split(' ').any { it.startsWith(q) } -> 2
            else -> null
        }
        return byPopulation.mapNotNull { city -> rank(city)?.let { it to city } }
            .sortedBy { it.first }
            .take(limit)
            .map { (_, city) -> CitySuggestion(city.name, city.state) }
    }

    companion object {
        const val MIN_QUERY = 2
        private val WHITESPACE = Regex("\\s+")

        private fun key(name: String) = name.trim().lowercase().replace(WHITESPACE, " ")

        /** The list shipped with the server. */
        val India: Cities by lazy { load("/cities/india.tsv") }

        fun load(resource: String): Cities {
            val text = Cities::class.java.getResource(resource)?.readText() ?: error("Missing $resource")
            return Cities(
                text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                    val (name, state, population, aliases) = line.split('\t')
                    City(name, state, population.toInt(), aliases.split(',').filter { it.isNotBlank() })
                }.toList(),
            )
        }
    }
}
