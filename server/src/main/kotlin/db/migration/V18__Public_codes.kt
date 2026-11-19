package db.migration

import com.harsh.desicrics.service.newPublicCode
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

/**
 * Gives every user and team a public 8-digit code, so people can find each other exactly instead of by
 * name. In Kotlin rather than SQL because existing rows need random, unique codes, and H2 (tests) and
 * Postgres share no way to loop for those in plain SQL.
 */
@Suppress("ClassName")
class V18__Public_codes : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val connection = context.connection
        for ((table, key) in listOf("users" to "uid", "teams" to "id")) {
            connection.createStatement().use { it.execute("ALTER TABLE $table ADD COLUMN code VARCHAR(8)") }
            val ids = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT $key FROM $table").use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
            }
            val used = HashSet<String>()
            connection.prepareStatement("UPDATE $table SET code = ? WHERE $key = ?").use { update ->
                for (id in ids) {
                    var code: String
                    do code = newPublicCode() while (!used.add(code))
                    update.setString(1, code)
                    update.setString(2, id)
                    update.addBatch()
                }
                if (ids.isNotEmpty()) update.executeBatch()
            }
            connection.createStatement().use { it.execute("ALTER TABLE $table ALTER COLUMN code SET NOT NULL") }
            connection.createStatement().use { it.execute("CREATE UNIQUE INDEX ${table}_code ON $table (code)") }
        }
    }
}
