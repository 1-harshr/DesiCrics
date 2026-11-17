package com.harsh.desicrics.db

import com.harsh.desicrics.DatabaseConfig
import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

// Plain relational tables; nothing here is H2-specific.

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
    val result = varchar("result", 200).nullable()
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
    val wicketKind = enumerationByName("wicket_kind", 15, DismissalKind::class).nullable()
    val playerOutId = reference("player_out_id", MatchPlayersTable.id).nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(matchId, seq)
}

object DatabaseFactory {
    /**
     * Connects and creates any missing tables. Fine for H2 and early Postgres; switch to versioned
     * migrations (e.g. Flyway) once there's production data to preserve.
     */
    fun connect(config: DatabaseConfig): Database {
        val database = Database.connect(config.url, user = config.user, password = config.password)
        transaction(database) {
            SchemaUtils.create(UsersTable, MatchesTable, MatchPlayersTable, DeliveriesTable)
        }
        return database
    }
}

/** Runs blocking JDBC work off the request's coroutine dispatcher. */
suspend fun <T> Database.query(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction(this@query) { block() } }
