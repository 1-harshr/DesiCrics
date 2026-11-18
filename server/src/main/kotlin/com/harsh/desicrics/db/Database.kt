package com.harsh.desicrics.db

import com.harsh.desicrics.DatabaseConfig
import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

// Exposed mappings for the tables. The schema itself is owned by the Flyway migrations in
// src/main/resources/db/migration; change both together.

object UsersTable : Table("users") {
    val uid = varchar("uid", 128)
    val name = varchar("name", 100)
    val phone = varchar("phone", 20)
    val email = varchar("email", 254).nullable()
    val photoUrl = varchar("photo_url", 1024).nullable()
    val city = varchar("city", 100)
    val role = enumerationByName("role", 20, PlayerRole::class)
    val battingStyle = enumerationByName("batting_style", 20, BattingStyle::class)
    val bowlingStyle = enumerationByName("bowling_style", 30, BowlingStyle::class)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(uid)
}

object MatchesTable : Table("matches") {
    val id = varchar("id", 36)
    val createdBy = reference("created_by", UsersTable.uid)
    val createdAt = long("created_at").index()
    val teamAName = varchar("team_a_name", 60)
    val teamBName = varchar("team_b_name", 60)
    val oversPerInnings = integer("overs_per_innings")
    val tossWinner = enumerationByName("toss_winner", 1, TeamSide::class)
    val tossDecision = enumerationByName("toss_decision", 4, TossDecision::class)
    val status = enumerationByName("status", 20, MatchStatus::class)
    val scoreSummary = varchar("score_summary", 200)
    val resultWinner = enumerationByName("result_winner", 1, TeamSide::class).nullable()
    val resultMargin = integer("result_margin").nullable()
    val resultMarginUnit = enumerationByName("result_margin_unit", 10, MarginUnit::class).nullable()
    /** JSON list of InningsScore. */
    val inningsScores = text("innings_scores")
    override val primaryKey = PrimaryKey(id)
}

/** A player in one match. A nullable link to [UsersTable] comes with "add player by phone number". */
object MatchPlayersTable : Table("match_players") {
    val id = varchar("id", 36)
    val matchId = reference("match_id", MatchesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val side = enumerationByName("side", 1, TeamSide::class)
    val position = integer("position")
    val name = varchar("name", 60)
    override val primaryKey = PrimaryKey(id)
}

/** One row per ball; (match, seq) as the key also rejects a double-tapped ball. */
object DeliveriesTable : Table("deliveries") {
    val matchId = reference("match_id", MatchesTable.id, onDelete = ReferenceOption.CASCADE)
    val seq = integer("seq")
    val innings = integer("innings")
    val strikerId = reference("striker_id", MatchPlayersTable.id)
    val nonStrikerId = reference("non_striker_id", MatchPlayersTable.id)
    val bowlerId = reference("bowler_id", MatchPlayersTable.id)
    val runsOffBat = integer("runs_off_bat")
    val extra = enumerationByName("extra", 10, ExtraType::class).nullable()
    val extraRuns = integer("extra_runs")
    val wicketKind = enumerationByName("wicket_kind", 20, DismissalKind::class).nullable()
    val playerOutId = reference("player_out_id", MatchPlayersTable.id).nullable()
    val fielderId = reference("fielder_id", MatchPlayersTable.id).nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(matchId, seq)
}

/** Between-ball events; [type] picks which of the other columns apply. */
object MatchEventsTable : Table("match_events") {
    val matchId = reference("match_id", MatchesTable.id, onDelete = ReferenceOption.CASCADE)
    val seq = integer("seq")
    val innings = integer("innings")
    val type = varchar("type", 20)
    val playerId = reference("player_id", MatchPlayersTable.id).nullable()
    val isOut = bool("is_out")
    val runs = integer("runs")
    val toBattingSide = bool("to_batting_side")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(matchId, seq)
}

/** A connected database plus the pool behind it, so the server can close it on shutdown. */
class DatabaseHandle(val database: Database, private val dataSource: HikariDataSource) : AutoCloseable {
    override fun close() = dataSource.close()
}

object DatabaseFactory {
    /** Opens a connection pool, applies any pending Flyway migrations, then hands the pool to Exposed. */
    fun connect(config: DatabaseConfig): DatabaseHandle {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = config.maxPoolSize
            },
        )
        try {
            Flyway.configure().dataSource(dataSource).load().migrate()
        } catch (e: Exception) {
            dataSource.close()
            throw e
        }
        return DatabaseHandle(Database.connect(dataSource), dataSource)
    }
}

/** Runs blocking JDBC work off the request's coroutine dispatcher. */
suspend fun <T> Database.query(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction(this@query) { block() } }
