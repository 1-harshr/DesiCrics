package com.harsh.desicrics.seed

import com.harsh.desicrics.ServerConfig
import com.harsh.desicrics.api.AddOrganiserRequest
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.api.DirectoryRequest
import com.harsh.desicrics.api.LookingForRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.RespondRequest
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.api.StartFixtureRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.db.DatabaseFactory
import com.harsh.desicrics.db.MatchesTable
import com.harsh.desicrics.db.UsersTable
import com.harsh.desicrics.db.query
import com.harsh.desicrics.live.LoggingPushSender
import com.harsh.desicrics.live.Notifier
import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DirectoryKind
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.FieldZone
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.LookingForKind
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.service.CommunityService
import com.harsh.desicrics.service.MatchService
import com.harsh.desicrics.service.ProfileService
import com.harsh.desicrics.service.SocialService
import com.harsh.desicrics.service.StatsProjector
import com.harsh.desicrics.service.TeamService
import com.harsh.desicrics.service.TournamentService
import com.harsh.desicrics.store.ExposedCommunityStore
import com.harsh.desicrics.store.ExposedMatchStore
import com.harsh.desicrics.store.ExposedPlayerStore
import com.harsh.desicrics.store.ExposedSocialStore
import com.harsh.desicrics.store.ExposedStatsStore
import com.harsh.desicrics.store.ExposedTeamStore
import com.harsh.desicrics.store.ExposedTournamentStore
import com.harsh.desicrics.store.ExposedUserStore
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/**
 * Fills the database with a believable season of Pune club cricket: ten teams of signed-up players, a
 * finished league, a knockout cup with a semi-final live right now, friendlies, gully games, fixtures still
 * to come, "looking for" posts and a directory. Every ball goes through [MatchService], so scorecards,
 * stats, points tables, rankings and notifications are exactly what the app would have made.
 *
 * Run with `./gradlew :server:seedDemo`. Add `--args="--for <uid>"` to put your own account in the demo:
 * you join Kothrud Kings (and play for them), follow a couple of teams and players, and take Pune as your
 * city if you have none. Runs once; wipe with `docker compose down -v` to seed again.
 */
fun main(args: Array<String>) = runBlocking {
    val forUids = args.toList().windowed(2).filter { it[0] == "--for" }.map { it[1] }
    val handle = DatabaseFactory.connect(ServerConfig.fromEnv().database)
    handle.use { DemoSeed(it.database, forUids).run() }
}

/** The seed's own time, so matches, balls and stats land on the days they were "played". */
private class SeedClock(var now: Long)

private data class Skill(val bat: Double, val bowl: Double)

private class DemoPlayer(
    val uid: String,
    val name: String,
    val role: PlayerRole,
    val batting: BattingStyle,
    val bowling: BowlingStyle,
    val skill: Skill,
) {
    val principal get() = FirebasePrincipal(uid)
}

private class DemoTeam(val name: String, val city: String, val strength: Double) {
    lateinit var id: String
    val players = mutableListOf<DemoPlayer>()
    val captain get() = players[CAPTAIN]

    fun squad() = SquadRequest(name, players.take(11).map { SquadPlayer(it.name, it.uid) }, id, captain = CAPTAIN, keeper = KEEPER)

    companion object {
        const val CAPTAIN = 2
        const val KEEPER = 4
    }
}

private class DemoSeed(private val database: Database, private val forUids: List<String>) {
    private val rng = Random(2026)
    private val ist = ZoneId.of("Asia/Kolkata")
    private val realNow = System.currentTimeMillis()
    private val today = LocalDate.now(ist)
    private val clock = SeedClock(realNow)
    private val time: () -> Long = { clock.now }

    private val users = ExposedUserStore(database)
    private val players = ExposedPlayerStore(database)
    private val teamStore = ExposedTeamStore(database)
    private val matchStore = ExposedMatchStore(database)
    private val socialStore = ExposedSocialStore(database)
    private val statsStore = ExposedStatsStore(database)
    private val profiles = ProfileService(users, players, time)
    private val matches = MatchService(matchStore, users, players, teamStore, clock = time)
    private val teams = TeamService(teamStore, players, matchStore, users, time)
    private val social = SocialService(socialStore, matchStore, teamStore, players, time)
    private val community = CommunityService(ExposedCommunityStore(database), players, teamStore, users, matchStore, statsStore, time)
    private val tournaments = TournamentService(ExposedTournamentStore(database), matchStore, matches, teamStore, players, users, statsStore, clock = time)

    init {
        // As in Application.module, minus the live socket feed. Pushes are only logged, never sent.
        matches.listen(Notifier(socialStore, LoggingPushSender, time))
        matches.listen(StatsProjector(statsStore, matchStore))
        matches.listen(tournaments)
    }

    /** Skills by profile ID; guests get theirs made up per match player. */
    private val skills = mutableMapOf<String, Skill>()
    private val guestSkills = mutableMapOf<String, Skill>()
    private val usedNames = mutableSetOf<String>()
    private var nextUid = 1

    private val pune = listOf(
        DemoTeam("Kothrud Kings", PUNE, 0.06),
        DemoTeam("Baner Blasters", PUNE, 0.04),
        DemoTeam("Hadapsar Hurricanes", PUNE, 0.0),
        DemoTeam("Aundh Avengers", PUNE, 0.03),
        DemoTeam("Wakad Warriors", PUNE, -0.02),
        DemoTeam("Viman Nagar Vipers", PUNE, 0.01),
        DemoTeam("Shivajinagar Sixers", PUNE, -0.04),
        DemoTeam("Kharadi Knights", PUNE, -0.01),
    )
    private val mumbai = listOf(DemoTeam("Andheri Aces", MUMBAI, 0.05), DemoTeam("Dadar Dynamos", MUMBAI, 0.02))
    private val allTeams = pune + mumbai
    private lateinit var organiser: DemoPlayer

    suspend fun run() {
        if (users.get(uid(1)) != null) {
            println("Demo data is already in this database. Wipe it with `docker compose down -v` to seed again.")
            return
        }
        clock.now = at(today.minusDays(120), 9, 0)
        allTeams.forEach { createTeam(it) }
        organiser = signUp(PUNE, PlayerRole.ALL_ROUNDER, Skill(0.4, 0.4))
        val guests = forUids.mapNotNull { joinDemo(it) }
        follows()
        guests.forEach { followFor(it) }

        league()
        friendlies()
        gully()
        cup()
        upcoming()
        registration()
        communityPosts()
        directory()

        val count = matchStore.recent(100).size
        println("\nSeeded ${allTeams.size} teams, ${nextUid - 1} players, 3 tournaments and $count recent matches.")
        if (forUids.isEmpty()) println("Tip: rerun on a fresh database with --args=\"--for <your uid>\" to join the demo yourself.")
    }

    // People and teams.

    private suspend fun createTeam(team: DemoTeam) {
        val shape = listOf(
            PlayerRole.BATTER to Skill(0.68, 0.1), PlayerRole.BATTER to Skill(0.64, 0.1), PlayerRole.BATTER to Skill(0.7, 0.3),
            PlayerRole.BATTER to Skill(0.6, 0.35), PlayerRole.WICKET_KEEPER to Skill(0.55, 0.0), PlayerRole.ALL_ROUNDER to Skill(0.55, 0.55),
            PlayerRole.ALL_ROUNDER to Skill(0.5, 0.6), PlayerRole.BOWLER to Skill(0.35, 0.68), PlayerRole.BOWLER to Skill(0.28, 0.7),
            PlayerRole.BOWLER to Skill(0.22, 0.65), PlayerRole.BOWLER to Skill(0.18, 0.66),
        )
        for ((role, base) in shape) {
            fun vary(value: Double) = if (value == 0.0) 0.0 else (value + team.strength + rng.nextDouble(-0.1, 0.1)).coerceIn(0.05, 0.95)
            team.players += signUp(team.city, role, Skill(vary(base.bat), vary(base.bowl)))
            clock.now += rng.nextLong(HOUR, 3 * DAY)
        }
        val captain = team.captain
        team.id = teams.create(captain.principal, TeamRequest(team.name, team.city, team.players.map { SquadPlayer(it.name, it.uid) })).id
        // A vice-captain who also runs the team.
        teamStore.setRole(team.id, team.players[DemoTeam.KEEPER].uid, TeamRole.ADMIN)
    }

    private suspend fun signUp(city: String, role: PlayerRole, skill: Skill): DemoPlayer {
        val name = generateSequence { "${FIRST_NAMES.random(rng)} ${LAST_NAMES.random(rng)}" }.first { usedNames.add(it) }
        val bowling = when {
            skill.bowl < 0.2 -> BowlingStyle.NONE
            skill.bowl < 0.45 -> listOf(BowlingStyle.RIGHT_ARM_OFF_SPIN, BowlingStyle.RIGHT_ARM_MEDIUM).random(rng)
            else -> BowlingStyle.entries.drop(1).random(rng)
        }
        val player = DemoPlayer(
            uid(nextUid++), name, role, if (rng.nextDouble() < 0.3) BattingStyle.LEFT_HAND else BattingStyle.RIGHT_HAND, bowling, skill,
        )
        profiles.update(player.principal, UpdateProfileRequest(player.name, phone(), city, role, player.batting, player.bowling))
        skills[player.uid] = skill
        return player
    }

    /** Puts a real account in Kothrud Kings, as an admin who plays. */
    private suspend fun joinDemo(uid: String): DemoPlayer? {
        val profile = users.get(uid) ?: run {
            println("No profile for $uid; sign in to the app and set one up first. Skipping it.")
            return null
        }
        if (profile.city.isBlank()) database.query { UsersTable.update({ UsersTable.uid eq uid }) { it[city] = PUNE } }
        val you = DemoPlayer(uid, profile.name, profile.role, profile.battingStyle, profile.bowlingStyle, Skill(0.62, 0.52))
        skills[uid] = you.skill
        val kings = pune.first()
        teamStore.addMember(kings.id, uid, TeamRole.ADMIN, clock.now)
        // In the XI in place of an all-rounder, so you have a scorecard, stats and form of your own.
        kings.players.add(6, you)
        println("Added ${profile.name} to ${kings.name}.")
        return you
    }

    private suspend fun follows() {
        val stars = allTeams.flatMap { team -> team.players.sortedByDescending { it.skill.bat + it.skill.bowl }.take(2) }
        for (team in allTeams) for (player in team.players) {
            for (other in allTeams.filter { it !== team }.shuffled(rng).take(rng.nextInt(0, 3))) {
                social.follow(player.principal, FollowType.TEAM, other.id)
            }
            for (star in stars.shuffled(rng).take(rng.nextInt(0, 3)).filter { it.uid != player.uid }) {
                social.follow(player.principal, FollowType.PLAYER, star.uid)
            }
        }
    }

    /** Before any match is played, so your inbox fills as the season goes. */
    private suspend fun followFor(you: DemoPlayer) {
        social.follow(you.principal, FollowType.TEAM, pune[1].id)
        social.follow(you.principal, FollowType.TEAM, mumbai[0].id)
        social.follow(you.principal, FollowType.PLAYER, pune[2].players[2].uid)
        social.follow(you.principal, FollowType.PLAYER, pune[3].players[8].uid)
    }

    // Tournaments.

    /** Pune Premier League: six teams, a round robin, semis and a final, all played. */
    private suspend fun league() {
        val start = today.minusWeeks(10).with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
        clock.now = at(start.minusDays(30), 11, 0)
        val id = tournaments.create(
            organiser.principal,
            CreateTournamentRequest(
                "Pune Premier League 2026", PUNE, TournamentFormat.ROUND_ROBIN, 12, MatchRules.Standard.copy(powerplayOvers = 3),
                advance = 4, startsAt = at(start, 8, 0),
            ),
        ).tournament.id
        tournaments.addOrganiser(organiser.principal, id, AddOrganiserRequest(pune[0].captain.uid))
        pune.take(6).forEach { tournaments.enterTeam(organiser.principal, id, it.id) }
        clock.now = at(start.minusDays(2), 20, 0)
        tournaments.start(organiser.principal, id)

        // Two games each Saturday and Sunday morning.
        val slots = generateSequence(start) { it.plusWeeks(1) }
            .flatMap { sat -> sequenceOf(at(sat, 7, 30), at(sat, 12, 0), at(sat.plusDays(1), 7, 30), at(sat.plusDays(1), 12, 0)) }
            .iterator()
        println("\nPune Premier League 2026")
        playFixtures(id, slots)
    }

    /** Monsoon Knockout Cup: quarter-finals played last weekend, a semi-final live now, the other to come. */
    private suspend fun cup() {
        val lastSat = today.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
        clock.now = at(lastSat.minusDays(24), 18, 0)
        val id = tournaments.create(
            organiser.principal,
            CreateTournamentRequest(
                "Monsoon Knockout Cup", PUNE, TournamentFormat.KNOCKOUT, 10, MatchRules.Club.copy(powerplayOvers = 2, wagonWheel = true),
                startsAt = at(lastSat, 8, 0),
            ),
        ).tournament.id
        pune.forEach { tournaments.enterTeam(organiser.principal, id, it.id) }
        clock.now = at(lastSat.minusDays(3), 21, 0)
        tournaments.start(organiser.principal, id)

        println("\nMonsoon Knockout Cup")
        val quarters = listOf(at(lastSat, 7, 30), at(lastSat, 12, 0), at(lastSat.plusDays(1), 7, 30), at(lastSat.plusDays(1), 12, 0))
        playFixtures(id, quarters.iterator(), rounds = 1)

        val (live, later) = tournaments.details(id).fixtures.filter { it.status == MatchStatus.SCHEDULED }.sortedBy { it.fixture!!.number }
        startFixture(live, realNow - 85 * MINUTE, venue = GROUNDS[1])
        play(live.id, organiser, realNow - 85 * MINUTE) { it.number == 2 && it.legalBalls >= 34 }.also { report("SF 1 (live)", it) }
        tournaments.schedule(organiser.principal, later.id, at(nextSaturday(), 16, 0))
    }

    /** Winter Box League: registration open, three teams in, one waiting for the organiser's OK. */
    private suspend fun registration() {
        clock.now = realNow - 3 * DAY
        val id = tournaments.create(
            organiser.principal,
            CreateTournamentRequest(
                "Winter Box League", PUNE, TournamentFormat.BOX_LEAGUE, 6, MatchRules.Gully,
                startsAt = at(nextSaturday().plusWeeks(2), 18, 0), registrationOpen = true,
            ),
        ).tournament.id
        pune.drop(4).take(3).forEach { tournaments.enterTeam(organiser.principal, id, it.id) }
        clock.now = realNow - DAY
        tournaments.enterTeam(pune[7].captain.principal, id, pune[7].id)
    }

    /** Starts and plays the scheduled fixtures in order, including rounds the tournament makes as it goes. */
    private suspend fun playFixtures(tournamentId: String, slots: Iterator<Long>, rounds: Int = Int.MAX_VALUE) {
        var played = 0
        while (played < rounds) {
            val round = tournaments.details(tournamentId).fixtures.filter { it.status == MatchStatus.SCHEDULED }
                .sortedWith(compareBy({ it.fixture!!.round }, { it.fixture!!.number }))
            if (round.isEmpty()) break
            for (fixture in round) {
                val at = slots.next()
                startFixture(fixture, at, GROUNDS[(fixture.fixture!!.round + fixture.fixture!!.number) % GROUNDS.size])
                val label = "${fixture.fixture!!.stage.name.lowercase().replaceFirstChar { it.uppercase() }} ${fixture.fixture!!.number}"
                report(label, play(fixture.id, organiser, at))
            }
            played++
        }
    }

    private suspend fun startFixture(fixture: Match, at: Long, venue: String) {
        clock.now = at - 30 * MINUTE
        tournaments.schedule(organiser.principal, fixture.id, at)
        // Fixtures are all made when the tournament starts; date them by when they're played, so feeds,
        // form and stats read in order.
        database.query {
            MatchesTable.update({ MatchesTable.id eq fixture.id }) {
                it[createdAt] = at - 30 * MINUTE
                it[MatchesTable.venue] = venue
            }
        }
        val a = team(fixture.teamA.teamId!!)
        val b = team(fixture.teamB.teamId!!)
        tournaments.startFixture(organiser.principal, fixture.id, StartFixtureRequest(a.squad(), b.squad(), toss(), tossDecision()))
    }

    // Quick matches.

    private suspend fun friendlies() {
        println("\nFriendlies")
        val fixtures = listOf(
            Friendly(0, 8, 44, 20, wagonWheel = true), Friendly(1, 9, 41, 16), Friendly(2, 3, 37, 12), Friendly(4, 5, 34, 15, wagonWheel = true),
            Friendly(6, 7, 30, 10), Friendly(0, 1, 27, 12, wagonWheel = true), Friendly(8, 9, 23, 20, mumbaiGround = true),
            Friendly(3, 6, 20, 10), Friendly(5, 2, 16, 12, wagonWheel = true), Friendly(7, 0, 13, 8), Friendly(1, 4, 11, 10),
            Friendly(9, 0, 4, 16, wagonWheel = true), Friendly(2, 7, 2, 8),
        )
        for (f in fixtures) {
            val a = allTeams[f.a]
            val b = allTeams[f.b]
            val day = today.minusDays(f.daysAgo.toLong())
            val start = if (day.dayOfWeek in WEEKEND) at(day, 7, 0) else at(day, 18, 30)
            val venue = if (f.mumbaiGround) MUMBAI_GROUNDS.random(rng) else GROUNDS.random(rng)
            val rules = if (f.wagonWheel) MatchRules(wagonWheel = true) else MatchRules()
            report("${a.name} v ${b.name}", quick(a.captain, a.squad(), b.squad(), f.overs, rules, venue, start))
        }

        // Live now: Kharadi Knights host Dadar Dynamos under lights.
        val start = realNow - 32 * MINUTE
        val details = quick(pune[7].captain, pune[7].squad(), mumbai[1].squad(), 8, MatchRules(), GROUNDS[5], start) { it.number == 1 && it.legalBalls >= 37 }
        report("Kharadi Knights v Dadar Dynamos (live)", details)
    }

    /** Street games: guests typed in by name, last man stands, six overs. */
    private suspend fun gully() {
        println("\nGully games")
        val host = pune[0].players[9]
        val games = listOf(
            Triple("Lane 7 Lions", "Lane 9 Legends", 15L),
            Triple("Office XI", "Night Owls", 8L),
            Triple("Building A", "Building C", 1L),
        )
        for ((home, away, daysAgo) in games) {
            val homeSide = SquadRequest(home, GUESTS.shuffled(rng).take(7).map { SquadPlayer(it) })
            val awaySide = SquadRequest(away, GUESTS.shuffled(rng).take(7).map { SquadPlayer(it) })
            val start = at(today.minusDays(daysAgo), 17, 0)
            report("$home v $away", quick(host, homeSide, awaySide, 6, MatchRules.Gully, "Society ground, Kothrud", start))
        }
    }

    /** Fixtures arranged for the coming days, squads and all, not yet started. */
    private suspend fun upcoming() {
        clock.now = realNow - 2 * HOUR
        val planned = listOf(Triple(2, 9, 2L), Triple(4, 6, 3L), Triple(1, 8, 9L))
        for ((ai, bi, inDays) in planned) {
            val a = allTeams[ai]
            matches.create(
                a.captain.principal,
                CreateMatchRequest(
                    a.squad(), allTeams[bi].squad(), 16, pending = true, scheduledAt = at(today.plusDays(inDays), 7, 30), venue = GROUNDS.random(rng),
                ),
            )
        }
    }

    private suspend fun quick(
        scorer: DemoPlayer,
        a: SquadRequest,
        b: SquadRequest,
        overs: Int,
        rules: MatchRules,
        venue: String,
        start: Long,
        until: (InningsState) -> Boolean = { false },
    ): MatchDetails {
        clock.now = start - 15 * MINUTE
        val match = matches.create(scorer.principal, CreateMatchRequest(a, b, overs, toss(), tossDecision(), rules, venue = venue)).match
        return play(match.id, scorer, start, until)
    }

    // Ball by ball.

    /** Scores the match ball by ball until it's over or [until] says to stop (for a match still live). */
    private suspend fun play(matchId: String, scorer: DemoPlayer, start: Long, until: (InningsState) -> Boolean = { false }): MatchDetails {
        var details = matches.details(matchId)
        val form = mutableMapOf<String, Double>()
        var t = start
        var innings = 1
        while (true) {
            val state = ScoringEngine.compute(details.match, details.deliveries, details.events)
            if (state.isComplete || until(state.current)) return details
            if (state.current.number != innings) {
                innings = state.current.number
                t += 15 * MINUTE
            } else if (state.current.bowlerId == null && state.current.legalBalls > 0) {
                t += rng.nextLong(45_000, 90_000)
            }
            t += rng.nextLong(20_000, 45_000)
            clock.now = minOf(t, realNow - 20_000)
            details = matches.recordDelivery(scorer.principal, matchId, nextBall(state, details.nextSeq, form))
        }
    }

    private fun nextBall(state: MatchState, seq: Int, form: MutableMap<String, Double>): Delivery {
        val innings = state.current
        val rules = state.match.rules
        val striker = innings.strikerId ?: innings.canComeIn.first { it.id != innings.nonStrikerId }.id
        val nonStriker = innings.nonStrikerId ?: innings.canComeIn.firstOrNull { it.id != striker }?.id
        val bowler = innings.bowlerId ?: pickBowler(state)
        fun dayForm(id: String) = form.getOrPut(id) { rng.nextDouble(-0.15, 0.15) }
        val bat = skillOf(innings.battingTeam.players.first { it.id == striker }).bat + dayForm(striker)
        val bowl = skillOf(innings.bowlingTeam.players.first { it.id == bowler }).bowl + dayForm(bowler)
        val edge = (bat - bowl).coerceIn(-0.8, 0.8)
        val death = if (innings.ballsRemaining <= 18) 1.0 else 0.0
        val ball = Delivery(seq, innings.number, striker, nonStriker, bowler)

        val roll = rng.nextDouble()
        return when {
            roll < 0.035 -> ball.copy(
                extra = ExtraType.WIDE, extraRuns = rules.widePenalty + if (rng.nextDouble() < 0.06) 4 else 0,
                extraPenalty = rules.widePenalty, countsAsBall = rules.countsAsBall(ExtraType.WIDE),
            )
            roll < 0.045 -> {
                val runs = rng.weighted(0 to 4.0, 1 to 3.0, 4 to 1.5, 6 to 0.7)
                ball.copy(
                    runsOffBat = runs, extra = ExtraType.NO_BALL, extraRuns = rules.noBallPenalty, extraPenalty = rules.noBallPenalty,
                    countsAsBall = rules.countsAsBall(ExtraType.NO_BALL), zone = zone(runs, rules),
                )
            }
            roll < 0.065 -> ball.copy(
                extra = if (rng.nextDouble() < 0.7) ExtraType.LEG_BYE else ExtraType.BYE, extraRuns = rng.weighted(1 to 8.0, 2 to 1.0, 4 to 1.0),
            )
            rng.nextDouble() < (0.055 - 0.04 * edge + 0.03 * death).coerceIn(0.015, 0.14) -> ball.copy(wicket = wicket(innings, striker, nonStriker, bowler))
            else -> {
                val runs = rng.weighted(
                    0 to 0.36 - 0.12 * edge - 0.08 * death, 1 to 0.33, 2 to 0.09, 3 to 0.012,
                    4 to 0.12 + 0.07 * edge + 0.05 * death, 6 to 0.04 + 0.05 * edge + 0.04 * death,
                )
                ball.copy(runsOffBat = runs, zone = zone(runs, rules))
            }
        }
    }

    private fun wicket(innings: InningsState, striker: String, nonStriker: String?, bowler: String): Wicket {
        val fielders = innings.bowlingTeam.players
        val keeper = innings.bowlingTeam.keeperId ?: fielders.random(rng).id
        // On a free hit only a run out stands.
        val kind = if (innings.isFreeHit) DismissalKind.RUN_OUT else rng.weighted(
            DismissalKind.CAUGHT to 55.0, DismissalKind.BOWLED to 22.0, DismissalKind.LBW to 9.0,
            DismissalKind.RUN_OUT to 8.0, DismissalKind.STUMPED to 4.0, DismissalKind.HIT_WICKET to 0.5,
        )
        return when (kind) {
            DismissalKind.CAUGHT -> {
                val catcher = rng.weighted(keeper to 2.0, bowler to 1.0, fielders.filter { it.id != bowler && it.id != keeper }.random(rng).id to 7.0)
                Wicket(kind, striker, catcher)
            }
            DismissalKind.STUMPED -> Wicket(kind, striker, keeper)
            DismissalKind.RUN_OUT -> {
                val out = if (nonStriker != null && rng.nextDouble() < 0.4) nonStriker else striker
                Wicket(kind, out, fielders.random(rng).id)
            }
            else -> Wicket(kind, striker)
        }
    }

    /** The best six bowlers share the overs: whoever has bowled least, not the last over's bowler, within the limit. */
    private fun pickBowler(state: MatchState): String {
        val innings = state.current
        val limit = state.match.rules.overLimit(innings.maxBalls / 6) * 6
        val bowled = innings.bowlers.associate { it.player.id to it.legalBalls }
        val attack = innings.bowlingTeam.players.sortedByDescending { skillOf(it).bowl }.take(6)
        val pool = attack.filter { it.id != innings.previousOverBowlerId && (bowled[it.id] ?: 0) < limit }
            .ifEmpty { innings.bowlingTeam.players.filter { it.id != innings.previousOverBowlerId } }
            .ifEmpty { innings.bowlingTeam.players }
        return pool.minWith(compareBy({ bowled[it.id] ?: 0 }, { -skillOf(it).bowl + rng.nextDouble(0.0, 0.3) })).id
    }

    private fun skillOf(player: Player): Skill =
        player.profileId?.let { skills[it] } ?: guestSkills.getOrPut(player.id) { Skill(rng.nextDouble(0.3, 0.7), rng.nextDouble(0.3, 0.7)) }

    private fun zone(runs: Int, rules: MatchRules): FieldZone? = if (rules.wagonWheel && runs > 0) FieldZone.entries.random(rng) else null

    // Community.

    private suspend fun communityPosts() {
        clock.now = realNow - 2 * DAY
        val posts = listOf(
            Post(pune[0], LookingForKind.OPPONENT, 3, "20-over game, leather ball. We have the ground at PYC booked from 7am, splitting the fee.", forTeam = true),
            Post(pune[4], LookingForKind.OPPONENT, 4, "Box cricket at Wakad, 8-a-side, Sunday evening. Floodlit.", forTeam = true),
            Post(pune[6], LookingForKind.PLAYER, 3, "Need a wicket-keeper for Saturday's friendly. Kit available.", forTeam = true),
            Post(pune[2], LookingForKind.PLAYER, 10, "Two fast bowlers wanted for the Winter Box League squad.", forTeam = true),
            Post(pune[1], LookingForKind.GROUND, 6, "Turf ground for a 16-over game near Baner or Aundh, 7 to 11am."),
            Post(pune[5], LookingForKind.UMPIRE, 4, "Two umpires for a 20-over friendly at Deccan Gymkhana."),
            Post(pune[3], LookingForKind.SCORER, 9, "Scorer for our league game. It's on DesiCrics, so just a phone needed."),
            Post(pune[7], LookingForKind.OPPONENT, 12, "Corporate side looking for a weekday evening game, tennis ball.", forTeam = true),
        )
        for (post in posts) {
            val author = post.team.captain
            val created = community.post(
                author.principal,
                LookingForRequest(post.kind, PUNE, at(today.plusDays(post.inDays.toLong()), 7, 0), post.note, post.team.id.takeIf { post.forTeam }),
            )
            clock.now += rng.nextLong(HOUR, 6 * HOUR)
            for (team in pune.filter { it !== post.team }.shuffled(rng).take(rng.nextInt(0, 4))) {
                val responder = team.players.random(rng)
                community.respond(responder.principal, created.id, RespondRequest(REPLIES.random(rng), phone()))
            }
        }
    }

    private suspend fun directory() {
        clock.now = realNow - 20 * DAY
        val adder = pune[0].captain.principal
        val entries = listOf(
            DirectoryRequest(DirectoryKind.GROUND, GROUNDS[0], PUNE, phone(), "Turf wicket, sight screens. About ₹6,000 for a 20-over slot."),
            DirectoryRequest(DirectoryKind.GROUND, GROUNDS[1], PUNE, phone(), "Big outfield, pavilion with changing rooms. Book a week ahead."),
            DirectoryRequest(DirectoryKind.GROUND, GROUNDS[2], PUNE, phone(), "Matting wicket, weekday evenings available."),
            DirectoryRequest(DirectoryKind.GROUND, GROUNDS[3], PUNE, phone(), "Floodlit box cricket till midnight, ₹1,200 an hour."),
            DirectoryRequest(DirectoryKind.GROUND, GROUNDS[4], PUNE, phone(), "Two pitches side by side; good for tournaments."),
            DirectoryRequest(DirectoryKind.GROUND, GROUNDS[5], PUNE, phone(), "Turf with lights, parking for 40 cars."),
            DirectoryRequest(DirectoryKind.UMPIRE, "Prakash Bhide", PUNE, phone(), "MCA panel umpire. Weekends only."),
            DirectoryRequest(DirectoryKind.UMPIRE, "Sunil Jagtap", PUNE, phone(), "20 years of club cricket. Brings his own counter and light meter."),
            DirectoryRequest(DirectoryKind.UMPIRE, "Farhan Qureshi", PUNE, phone(), "Available for leather and tennis ball games."),
            DirectoryRequest(DirectoryKind.SCORER, "Meera Kulkarni", PUNE, phone(), "Scores on DesiCrics; can stream the score overlay too."),
            DirectoryRequest(DirectoryKind.SCORER, "Ajay Ranade", PUNE, phone(), "Paper and app. Tournaments preferred."),
            DirectoryRequest(DirectoryKind.GROUND, MUMBAI_GROUNDS[0], MUMBAI, phone(), "Shared maidan; get there early on Sundays."),
            DirectoryRequest(DirectoryKind.UMPIRE, "Vijay Salunkhe", MUMBAI, phone(), "Kanga League experience."),
        )
        entries.forEach { community.addEntry(adder, it); clock.now += HOUR }
    }

    // Helpers.

    private fun report(label: String, details: MatchDetails) {
        val match = details.match
        val result = match.result?.let { result ->
            result.winner?.let { side ->
                val unit = if (result.marginUnit == MarginUnit.WICKETS) "wkts" else "runs"
                "${match.team(side).name} won by ${result.margin} $unit"
            } ?: "Tied"
        } ?: "in progress"
        println("  $label: ${match.scoreSummary} - $result")
    }

    private fun team(id: String) = allTeams.first { it.id == id }

    private fun toss() = if (rng.nextBoolean()) TeamSide.A else TeamSide.B

    private fun tossDecision() = if (rng.nextDouble() < 0.6) TossDecision.BAT else TossDecision.BOWL

    private fun phone() = "+91 9${rng.nextInt(1000, 9999)} ${rng.nextInt(10000, 99999)}"

    private fun at(day: LocalDate, hour: Int, minute: Int) = day.atTime(LocalTime.of(hour, minute)).atZone(ist).toInstant().toEpochMilli()

    private fun nextSaturday() = today.plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))

    private fun <T> Random.weighted(vararg options: Pair<T, Double>): T {
        var roll = nextDouble() * options.sumOf { it.second.coerceAtLeast(0.0) }
        for ((value, weight) in options) {
            roll -= weight.coerceAtLeast(0.0)
            if (roll <= 0) return value
        }
        return options.last().first
    }

    private data class Friendly(val a: Int, val b: Int, val daysAgo: Int, val overs: Int, val wagonWheel: Boolean = false, val mumbaiGround: Boolean = false)

    /** A "looking for" post by [team]'s captain, made for the team itself when [forTeam]. */
    private data class Post(val team: DemoTeam, val kind: LookingForKind, val inDays: Int, val note: String, val forTeam: Boolean = false)

    private companion object {
        const val PUNE = "Pune"
        const val MUMBAI = "Mumbai"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        /** Demo users' UIDs; nothing Firebase issues looks like this. */
        fun uid(n: Int) = "demo-%03d".format(n)

        val GROUNDS = listOf(
            "PYC Hindu Gymkhana", "Deccan Gymkhana Ground", "Nehru Stadium Nets", "Baner Box Cricket Arena", "Poona Club Ground", "Kharadi Turf Park",
        )
        val MUMBAI_GROUNDS = listOf("Shivaji Park", "Oval Maidan", "Cross Maidan")

        val FIRST_NAMES = listOf(
            "Aarav", "Aditya", "Ajinkya", "Akash", "Amit", "Aniket", "Ankit", "Arjun", "Ashwin", "Atharva", "Chinmay", "Darshan", "Deepak",
            "Dhruv", "Gaurav", "Harshal", "Hemant", "Ishaan", "Jay", "Kedar", "Kunal", "Manish", "Mayur", "Mihir", "Nikhil", "Nilesh",
            "Omkar", "Pranav", "Prasad", "Prathamesh", "Rahul", "Rajat", "Rohan", "Rohit", "Sagar", "Sahil", "Sameer", "Sanket", "Saurabh",
            "Shreyas", "Siddharth", "Soham", "Suraj", "Swapnil", "Tanmay", "Tejas", "Varun", "Vedant", "Vikram", "Vinay", "Yash", "Zaheer",
            "Imran", "Faisal", "Arshad", "Joel", "Kevin", "Ravi", "Karan", "Abhishek",
        )
        val LAST_NAMES = listOf(
            "Kulkarni", "Deshpande", "Patil", "Joshi", "Gokhale", "Jadhav", "Pawar", "Shinde", "Kale", "Bhosale", "Deshmukh", "Apte", "Sathe",
            "Gaikwad", "More", "Chavan", "Kadam", "Mane", "Naik", "Sawant", "Iyer", "Menon", "Shaikh", "Khan", "D'Souza", "Fernandes",
            "Sharma", "Verma", "Rao", "Reddy", "Nair", "Mehta", "Shah", "Agarwal", "Thakur", "Mishra",
        )
        val GUESTS = listOf(
            "Bunty", "Pappu", "Chintu", "Golu", "Monu", "Sonu", "Raju", "Pintu", "Bablu", "Tinku", "Guddu", "Lucky", "Sunny", "Vicky", "Bittu", "Appu",
        )
        val REPLIES = listOf(
            "We're in. Can bring 11 and our own balls.",
            "Interested! Call me to fix the timing.",
            "I keep wicket for my office side, happy to play.",
            "We can do it if the start is 8am instead.",
            "Count us in, sharing the ground fee is fine.",
            "Available that day, send the location.",
        )
    }
}
