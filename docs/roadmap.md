# Roadmap

Each phase delivers something a real group can use on a Sunday morning. Inside a phase, levels separate what must ship from polish:

- **Level 1 · Playable**: the minimum for the phase's goal.
- **Level 2 · Complete**: the full feature area as described in [features.md](features.md).
- **Level 3 · Delight**: polish that makes it stand out; can slip to a later phase.

A phase is done when its exit criterion passes. Level 3 items don't block the next phase.

Status: ✅ done · 🟡 partial · ⬜ to do. Status refers to `main`.

---

## Phase 0 · Foundation

**Goal:** a signed-in user with a profile, a deployed server and a design system to build on.

### Level 1 · Playable
- ✅ Ktor server with Firebase token auth
- ✅ Exposed stores
- ✅ Shared DTOs in `core`
- ✅ Drop the iOS target from the build
- ✅ Postgres + Flyway migrations (local Postgres in Docker)

### Level 2 · Complete
- ✅ `:app:designsystem` module: tokens, fonts, type, first 10 components ([design-system.md](design-system.md))
- ✅ `strings.xml` set up in `:app:shared` and `:app:designsystem`; sign-in, profile and session text moved ([ui-conventions.md](ui-conventions.md))
- ✅ Phone OTP + Google sign-in screens in Crease
- ✅ Profile onboarding: name, role, styles; photo upload from the profile editor
- ✅ CI: build, unit tests, server tests (`.github/workflows/ci.yml`)

### Level 3 · Delight
- ⬜ Staging server deploy (needs the hosting decision)
- ✅ Crashlytics + basic analytics events (match, team, tournament, claim, handover; no personal data)
- ⬜ Compose screenshot tests for components

**Exit:** a new user installs the debug APK, signs in, and their profile is saved on the staging server.

---

## Phase 1 · Score a match

**Goal:** two teams play a full limited-overs match scored ball by ball, with a correct scorecard at the end.

### Level 1 · Playable
- ✅ Delivery rules engine with tests: extras, wickets off extras, fielders, innings end, typed results and errors
- ✅ Rebuild home, new match and scoring screens in Crease, moving their text to string resources
- ✅ Quick match: ad-hoc teams, typed player names, overs
- ✅ Toss (on the new match screen)
- ✅ Run pad: 0–6, wides, no-balls, byes, leg byes
- ✅ Common wickets: bowled, caught, LBW, run out, stumped, with the fielder
- ✅ Strike rotation, over end, innings end, result
- ✅ Undo last ball

### Level 2 · Complete
- ✅ All dismissals in the laws except timed out, plus "Other" for local rules; penalty runs to either side
- ✅ Retired hurt (can return) / retired out, swap ends, change bowler mid-over, add latecomers and substitutes mid-match
- ✅ Per-match rules with presets, each off / warn / enforce: last man stands, retired batters' return, retire at a score, overs per bowler, back-to-back overs, wide and no-ball runs and re-bowling, free hits
- ✅ Revise target (rain) and declare: the scorer enters the new overs and, for the chase, the target; results are judged against par
- ✅ Scorecard and over-by-over tabs
- ✅ Auto-generated commentary text (per ball, on the Overs tab)

### Level 3 · Delight
- ✅ Wagon wheel (8 fielding zones): optional per match; a tap per scoring shot, shown per innings and per batter on the scorecard
- ✅ Haptics per event type (wicket, boundary, other)
- ✅ Scorer handover to another device with a one-time 6-digit code (10 minutes)
- ✅ Powerplay configuration (overs per innings, shown on the scoreboard)

**Exit:** a 20-over match scored on one phone ends with totals that match a paper scorebook. Automated by `ScorebookReconciliationTest`: 50 seeded 20-over matches, each checked against the scorebook cross-checks (batting + extras, bowling + byes + leg byes + penalties, balls, wickets).

---

## Phase 2 · Teams and players

**Goal:** matches are played between saved teams of real users, so stats attach to people.

### Level 1 · Playable
- ✅ Create team (name, logo, city)
- ✅ Add members by search or phone number
- ✅ Squad selection with captain and keeper
- ✅ Placeholder players who can claim their profile later, by code or verified phone ([ADR 0002](decisions/0002-player-identity.md))

### Level 2 · Complete
- ✅ Team admins; leave or remove a member
- ✅ QR code to join a team
- ✅ Profile tabs: Matches / Teams
- ✅ Settings, support form (no attachments yet), account deletion

### Level 3 · Delight
- 🟡 WhatsApp invite links: shared through the share sheet and opened by the app; verified App Links need the deployed domain's assetlinks.json
- ✅ Reuse last match's squad
- ✅ Team head-to-head record

**Exit:** a club creates its team once and starts every following match in under 60 seconds.

---

## Phase 3 · Follow live

**Goal:** people who aren't at the ground follow the match in real time and come back to the app.

### Level 1 · Playable
- ✅ WebSocket live feed per match (whole scorecards; the app falls back to polling if the socket drops)
- ✅ Public read-only match link (`/m/{id}`, server-rendered, opens in the app when installed)
- ✅ Today feed: your match, live, upcoming, recent; followed teams and players first

### Level 2 · Complete
- ✅ Follow teams and players
- ✅ FCM: match start, result, followed players' milestones (50, 100, 5 wickets). Push needs `FIREBASE_SERVICE_ACCOUNT` on the server; without it notifications only reach the inbox
- ✅ Notification inbox
- ✅ Search: matches, teams, players

### Level 3 · Delight
- ✅ Share cards: scorecard and player stats images (9:16, for WhatsApp status), and match links
- ⬜ Live progress notification for followed matches (needs a foreground service holding the socket; deferred)
- ⬜ Home screen widget (Glance; deferred)

**Exit:** a follower sees each ball within 2 seconds of it being scored.

---

## Phase 4 · Tournaments

**Goal:** an organiser runs a multi-team event from fixtures to final in the app.

### Level 1 · Playable
- ✅ Create tournament, add teams
- ✅ Knockout and round-robin fixture generation ([ADR 0003](decisions/0003-tournament-fixtures.md))
- ✅ Points table with NRR

### Level 2 · Complete
- ✅ Groups → quarters → semis → final
- ✅ Box league, double out, best of three, custom
- ✅ Organisers and co-organisers
- ✅ Tournament key stats (most runs, wickets, sixes)

### Level 3 · Delight
- ✅ Player of the match / tournament (from MVP points)
- ✅ Public tournament page link (`/t/{id}`)
- ✅ Team registration: team admins enter while registration is open; organisers confirm or decline
- ✅ Fixture reschedule notifications

**Exit:** an 8-team weekend cup runs without a spreadsheet.

---

## Phase 5 · Stats and community

**Goal:** players open the app between matches to check and compare their numbers.

### Level 1 · Playable
- ✅ Career batting and bowling stats projection (`player_match_lines`, rewritten whenever a match completes or is undone)
- ✅ Stats tab on the profile, and on every player's page

### Level 2 · Complete
- ✅ Season and format filters (format by overs: up to 10, 11–20, longer)
- ✅ Leaderboards by city, tournament and friends (runs, wickets, MVP, sixes, catches)
- ✅ Form chart (last 10 innings)
- ✅ MVP points on every scorecard (formula in [features.md](features.md#mvp-points))

### Level 3 · Delight
- ✅ Batting zones from wagon wheel data
- ✅ Compare two players
- ✅ Milestone badges
- ✅ Voice scoring ("four", "wide two", "chhakka", "out"); wickets open the wicket sheet rather than being recorded from speech

**Exit:** stats for a player with 10 matches match a manual recount. Automated by `StatsEngineTest.careerStatsOverTenMatchesMatchARecount`.

---

## Phase 6 · Cricket network

**Goal:** DesiCrics becomes where local cricket gets organised, not only recorded.

### Level 1 · Playable
- ⬜ "Looking for": opponents and players by city and date

### Level 2 · Complete
- ⬜ City team and player rankings
- ⬜ Grounds, umpires and scorers directory

### Level 3 · Delight
- ⬜ Livestream score overlay (OBS / Larix)
- ⬜ Paid insights or organiser plan (needs a pricing decision)

**Exit:** at least 20% of weekend matches in a pilot city are arranged through "Looking for".
