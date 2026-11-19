package com.harsh.desicrics

import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway

class MigrationTest {

    @Test
    fun existingUsersAndTeamsGetUniquePublicCodes() {
        val db = freshDatabase()
        fun flyway(target: String) = Flyway.configure().dataSource(db.url, db.user, db.password).target(target).load()
        flyway("17").migrate()
        DriverManager.getConnection(db.url, db.user, db.password).use { connection ->
            connection.createStatement().use { statement ->
                for (i in 1..50) {
                    statement.execute("INSERT INTO users (uid, name, phone, city, role, batting_style, bowling_style, created_at) VALUES ('u$i', 'User $i', '', '', 'BATTER', 'RIGHT_HAND', 'NONE', 0)")
                    statement.execute("INSERT INTO teams (id, name, city, join_code, created_by, created_at) VALUES ('t$i', 'Team $i', '', 'J$i', 'u$i', 0)")
                }
            }
            flyway("latest").migrate()
            for (table in listOf("users", "teams")) {
                val codes = connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT code FROM $table").use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
                }
                assertEquals(50, codes.toSet().size, "$table codes are unique")
                assertTrue(codes.all { it.length == 8 && it.all(Char::isDigit) }, "$table codes are 8 digits")
            }
        }
    }
}
