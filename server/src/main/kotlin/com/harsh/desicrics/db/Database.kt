package com.harsh.desicrics.db
import com.harsh.desicrics.model.AbandonReason
import com.harsh.desicrics.model.MatchEnding

import com.harsh.desicrics.DatabaseConfig
import com.harsh.desicrics.model.AuctionStatus
import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.DirectoryKind
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.FieldZone
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.LookingForKind
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.NotificationKind
import com.harsh.desicrics.model.OrganiserRole
import com.harsh.desicrics.model.Stage
import com.harsh.desicrics.model.EntryPolicy
import com.harsh.desicrics.model.PoolMode
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.api.SupportCategory
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.TeamRole
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
    /** Set when the account was deleted; the row stays, cleared, so old scorecards still resolve. */
    val deletedAt = long("deleted_at").nullable()
    /** Public, 8 digits, never changes. */
    val code = varchar("code", 8)
    override val primaryKey = PrimaryKey(uid)
}

/** A player across matches: a user (id = their UID) or a placeholder someone else added. */
object PlayersTable : Table("players") {
    val id = varchar("id", 128)
    val userUid = varchar("user_uid", 128).nullable()
    val name = varchar("name", 100)
    val phone = varchar("phone", 20)
    val claimCode = varchar("claim_code", 8).nullable()
    val createdBy = varchar("created_by", 128)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TeamsTable : Table("teams") {
    val id = varchar("id", 36)
    val name = varchar("name", 60)
    val city = varchar("city", 100)
    val joinCode = varchar("join_code", 8)
    /** Public, 8 digits, never changes; the join code is the secret one. */
    val code = varchar("code", 8)
    val logoUrl = varchar("logo_url", 200).nullable()
    val createdBy = varchar("created_by", 128)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TeamMembersTable : Table("team_members") {
    val teamId = reference("team_id", TeamsTable.id, onDelete = ReferenceOption.CASCADE)
    val playerId = reference("player_id", PlayersTable.id)
    val role = enumerationByName("role", 10, TeamRole::class)
    val joinedAt = long("joined_at")
    override val primaryKey = PrimaryKey(teamId, playerId)
}

object TeamJoinRequestsTable : Table("team_join_requests") {
    val teamId = reference("team_id", TeamsTable.id, onDelete = ReferenceOption.CASCADE)
    val playerId = reference("player_id", PlayersTable.id)
    val requestedAt = long("requested_at")
    override val primaryKey = PrimaryKey(teamId, playerId)
}

object SupportRequestsTable : Table("support_requests") {
    val id = varchar("id", 36)
    val uid = varchar("uid", 128)
    val category = enumerationByName("category", 10, SupportCategory::class)
    val message = varchar("message", 4000)
    val appVersion = varchar("app_version", 40)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
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
    val resultEnding = enumerationByName("result_ending", 12, MatchEnding::class).nullable()
    val resultReason = enumerationByName("result_reason", 12, AbandonReason::class).nullable()
    /** JSON list of InningsScore. */
    val inningsScores = text("innings_scores")
    /** JSON MatchRules. */
    val rules = text("rules")
    val scorerUid = varchar("scorer_uid", 128)
    val teamAId = varchar("team_a_id", 36).nullable()
    val teamBId = varchar("team_b_id", 36).nullable()
    val venue = varchar("venue", 120).default("")
    val scheduledAt = long("scheduled_at").nullable()
    val tournamentId = varchar("tournament_id", 36).nullable()
    val stage = enumerationByName("stage", 15, Stage::class).nullable()
    val roundNo = integer("round_no").nullable()
    val fixtureNo = integer("fixture_no").nullable()
    val groupNo = integer("group_no").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** A player in one match; [profileId] links them to [PlayersTable] when they aren't a typed-in guest. */
object MatchPlayersTable : Table("match_players") {
    val id = varchar("id", 36)
    val matchId = reference("match_id", MatchesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val side = enumerationByName("side", 1, TeamSide::class)
    val position = integer("position")
    val name = varchar("name", 60)
    val profileId = varchar("profile_id", 128).nullable()
    val isCaptain = bool("is_captain")
    val isKeeper = bool("is_keeper")
    override val primaryKey = PrimaryKey(id)
}

/** One row per ball; (match, seq) as the key also rejects a double-tapped ball. */
object DeliveriesTable : Table("deliveries") {
    val matchId = reference("match_id", MatchesTable.id, onDelete = ReferenceOption.CASCADE)
    val seq = integer("seq")
    val innings = integer("innings")
    val strikerId = reference("striker_id", MatchPlayersTable.id)
    val nonStrikerId = reference("non_striker_id", MatchPlayersTable.id).nullable()
    val bowlerId = reference("bowler_id", MatchPlayersTable.id)
    val runsOffBat = integer("runs_off_bat")
    val extra = enumerationByName("extra", 10, ExtraType::class).nullable()
    val extraRuns = integer("extra_runs")
    val wicketKind = enumerationByName("wicket_kind", 20, DismissalKind::class).nullable()
    val playerOutId = reference("player_out_id", MatchPlayersTable.id).nullable()
    val fielderId = reference("fielder_id", MatchPlayersTable.id).nullable()
    val extraPenalty = integer("extra_penalty")
    val countsAsBall = bool("counts_as_ball")
    val zone = enumerationByName("zone", 12, FieldZone::class).nullable()
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
    val overs = integer("overs").nullable()
    val target = integer("target").nullable()
    val ending = enumerationByName("ending", 12, MatchEnding::class).nullable()
    val winner = enumerationByName("winner", 1, TeamSide::class).nullable()
    val reason = enumerationByName("reason", 12, AbandonReason::class).nullable()
    val beforeStart = bool("before_start").default(false)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(matchId, seq)
}

/** Codes a scorer shows so someone else can take over the match. */
object ScorerHandoversTable : Table("scorer_handovers") {
    val code = varchar("code", 6)
    val matchId = reference("match_id", MatchesTable.id, onDelete = ReferenceOption.CASCADE)
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(code)
}

object TournamentsTable : Table("tournaments") {
    val id = varchar("id", 36)
    val name = varchar("name", 80)
    val city = varchar("city", 100)
    val format = enumerationByName("format", 20, TournamentFormat::class)
    val oversPerInnings = integer("overs_per_innings")
    /** JSON MatchRules. */
    val rules = text("rules")
    val groupCount = integer("group_count")
    val advance = integer("advance")
    val startsAt = long("starts_at").nullable()
    val teamEntry = enumerationByName("team_entry", 10, EntryPolicy::class)
    val playerEntry = enumerationByName("player_entry", 10, EntryPolicy::class)
    val maxTeams = integer("max_teams").nullable()
    val maxPlayers = integer("max_players").nullable()
    val entryClosesAt = long("entry_closes_at").nullable()
    val poolMode = enumerationByName("pool_mode", 10, PoolMode::class)
    val started = bool("started")
    val createdBy = varchar("created_by", 128)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TournamentOrganisersTable : Table("tournament_organisers") {
    val tournamentId = reference("tournament_id", TournamentsTable.id, onDelete = ReferenceOption.CASCADE)
    val uid = varchar("uid", 128)
    val role = enumerationByName("role", 15, OrganiserRole::class)
    override val primaryKey = PrimaryKey(tournamentId, uid)
}

object TournamentTeamsTable : Table("tournament_teams") {
    val tournamentId = reference("tournament_id", TournamentsTable.id, onDelete = ReferenceOption.CASCADE)
    val teamId = reference("team_id", TeamsTable.id)
    val seed = integer("seed")
    val groupNo = integer("group_no").nullable()
    val status = enumerationByName("status", 10, EntryStatus::class)
    override val primaryKey = PrimaryKey(tournamentId, teamId)
}

/** Squad changes after a tournament starts. */
object TournamentSquadChangesTable : Table("tournament_squad_changes") {
    val id = varchar("id", 36)
    val tournamentId = reference("tournament_id", TournamentsTable.id, onDelete = ReferenceOption.CASCADE)
    val teamId = varchar("team_id", 36)
    val playerId = varchar("player_id", 128)
    val added = bool("added")
    val changedBy = varchar("changed_by", 128)
    val changedAt = long("changed_at")
    override val primaryKey = PrimaryKey(id)
}

/** Teams asking pool players to join their squads. */
object TournamentSquadOffersTable : Table("tournament_squad_offers") {
    val tournamentId = reference("tournament_id", TournamentsTable.id, onDelete = ReferenceOption.CASCADE)
    val playerId = varchar("player_id", 128)
    val teamId = reference("team_id", TeamsTable.id)
    val offeredBy = varchar("offered_by", 128)
    val offeredAt = long("offered_at")
    override val primaryKey = PrimaryKey(tournamentId, playerId, teamId)
}

/** The pool (no team) and every squad; one row per player per tournament. */
object TournamentPlayersTable : Table("tournament_players") {
    val tournamentId = reference("tournament_id", TournamentsTable.id, onDelete = ReferenceOption.CASCADE)
    val playerId = reference("player_id", PlayersTable.id)
    val teamId = reference("team_id", TeamsTable.id).nullable()
    val status = enumerationByName("status", 10, EntryStatus::class)
    val fromPool = bool("from_pool")
    val addedBy = varchar("added_by", 128)
    val addedAt = long("added_at")
    override val primaryKey = PrimaryKey(tournamentId, playerId)
}

object AuctionsTable : Table("auctions") {
    val id = varchar("id", 36)
    val tournamentId = reference("tournament_id", TournamentsTable.id, onDelete = ReferenceOption.CASCADE)
    /** JSON AuctionSettings. */
    val settings = text("settings")
    val status = enumerationByName("status", 10, AuctionStatus::class)
    val createdBy = varchar("created_by", 128)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AuctionPlayersTable : Table("auction_players") {
    val auctionId = reference("auction_id", AuctionsTable.id, onDelete = ReferenceOption.CASCADE)
    val playerId = reference("player_id", PlayersTable.id)
    val basePrice = integer("base_price")
    val category = varchar("category", 30).nullable()
    val requested = bool("requested")
    val retainedBy = varchar("retained_by", 36).nullable()
    val retainedPrice = integer("retained_price").nullable()
    val addedAt = long("added_at")
    override val primaryKey = PrimaryKey(auctionId, playerId)
}

object AuctionEventsTable : Table("auction_events") {
    val auctionId = reference("auction_id", AuctionsTable.id, onDelete = ReferenceOption.CASCADE)
    val seq = integer("seq")
    val type = varchar("type", 20)
    /** JSON AuctionEvent. */
    val data = text("data")
    val createdBy = varchar("created_by", 128).nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(auctionId, seq)
}

object ImagesTable : Table("images") {
    val id = varchar("id", 36)
    val ownerUid = varchar("owner_uid", 128)
    val contentType = varchar("content_type", 30)
    val bytes = binary("bytes")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

/** The stats projection; [line] is a JSON PlayerMatchLine. */
object PlayerMatchLinesTable : Table("player_match_lines") {
    val matchId = varchar("match_id", 36)
    val profileId = varchar("profile_id", 128)
    val playedAt = long("played_at")
    val tournamentId = varchar("tournament_id", 36).nullable()
    val oversPerInnings = integer("overs_per_innings")
    val line = text("line")
    override val primaryKey = PrimaryKey(matchId, profileId)
}

object FollowsTable : Table("follows") {
    val uid = varchar("uid", 128)
    val targetType = enumerationByName("target_type", 10, FollowType::class)
    val targetId = varchar("target_id", 128)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(uid, targetType, targetId)
}

object NotificationsTable : Table("notifications") {
    val id = varchar("id", 36)
    val uid = varchar("uid", 128)
    val kind = enumerationByName("kind", 20, NotificationKind::class)
    val matchId = varchar("match_id", 36)
    val matchTitle = varchar("match_title", 130)
    val playerName = varchar("player_name", 100).nullable()
    val amount = integer("amount").nullable()
    val detail = varchar("detail", 200).nullable()
    val createdAt = long("created_at")
    val readAt = long("read_at").nullable()
    val teamId = varchar("team_id", 36).nullable()
    val tournamentId = varchar("tournament_id", 36).nullable()
    override val primaryKey = PrimaryKey(id)
}

object PushTokensTable : Table("push_tokens") {
    val token = varchar("token", 512)
    val uid = varchar("uid", 128)
    val platform = varchar("platform", 10)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(token)
}

object LookingForPostsTable : Table("looking_for_posts") {
    val id = varchar("id", 36)
    val authorUid = varchar("author_uid", 128)
    val kind = enumerationByName("kind", 10, LookingForKind::class)
    val city = varchar("city", 100)
    val forDate = long("for_date")
    val note = varchar("note", 500)
    val teamId = varchar("team_id", 36).nullable()
    val isOpen = bool("is_open")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object LookingForResponsesTable : Table("looking_for_responses") {
    val postId = reference("post_id", LookingForPostsTable.id, onDelete = ReferenceOption.CASCADE)
    val uid = varchar("uid", 128)
    val message = varchar("message", 500)
    val phone = varchar("phone", 20)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(postId, uid)
}

object DirectoryEntriesTable : Table("directory_entries") {
    val id = varchar("id", 36)
    val kind = enumerationByName("kind", 10, DirectoryKind::class)
    val name = varchar("name", 100)
    val city = varchar("city", 100)
    val phone = varchar("phone", 20)
    val notes = varchar("notes", 500)
    val addedBy = varchar("added_by", 128)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
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
