package com.harsh.desicrics.cities

import com.harsh.desicrics.db.DirectoryEntriesTable
import com.harsh.desicrics.db.LookingForPostsTable
import com.harsh.desicrics.db.TeamsTable
import com.harsh.desicrics.db.TournamentsTable
import com.harsh.desicrics.db.UsersTable
import com.harsh.desicrics.db.query
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Rewrites cities saved before the list existed (or before a city joined it) to their standard names, so
 * "Bombay" and "mumbai " already in the database meet "Mumbai". Runs at startup; once everything is
 * standard it only reads the distinct cities, which is cheap.
 */
suspend fun standardiseCities(db: Database, cities: Cities = Cities.India): Int = db.query {
    val columns: List<Pair<Table, Column<String>>> = listOf(
        UsersTable to UsersTable.city,
        TeamsTable to TeamsTable.city,
        TournamentsTable to TournamentsTable.city,
        LookingForPostsTable to LookingForPostsTable.city,
        DirectoryEntriesTable to DirectoryEntriesTable.city,
    )
    columns.sumOf { (table, column) ->
        table.select(column).withDistinct().map { it[column] }.sumOf { saved ->
            val standard = cities.canonical(saved)
            if (standard == saved) 0 else table.update({ column eq saved }) { it[column] = standard }
        }
    }
}
