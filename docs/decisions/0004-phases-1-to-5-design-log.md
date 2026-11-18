# 0004 · Design log for Phases 1–5

- **Status:** Accepted
- **Date:** 2026-11-19

Phases 1 (Level 3) to 5 were built in one unattended stretch. These are the questions that came up and how they were answered, so each choice can be reviewed or reversed. The bigger ones have their own records: [0002](0002-player-identity.md) (player identity) and [0003](0003-tournament-fixtures.md) (tournament fixtures).

## Phase 1: scoring

| Question | Answer |
|---|---|
| What does "matches a paper scorebook" mean as a test? | The scorer's end-of-innings cross-checks: batting plus extras, bowling plus byes, leg byes and penalties, balls and wickets. `ScorebookReconciliationTest` plays 50 seeded 20-over matches through validation and checks all of them. |
| Are the wagon wheel and powerplay "rules"? | They're per-match settings fixed at creation, so they live in `MatchRules`. That gets them stored, validated and given presets for free. |
| How are wagon wheel zones named so they work for left-handers? | Relative to the batter (cover, point, fine leg…), drawn with the off side on the right as the batter sees it. Nothing needs mirroring. |
| Won't a zone picker slow scoring down? | It's off by default, only appears for runs off the bat, and always has Skip. |
| How does scoring move to another phone without a user search? | A one-time 6-digit code that lasts 10 minutes. It works across accounts and needs no search. |

## Phase 2: teams and players

| Question | Answer |
|---|---|
| How do stats attach to people who typed-in names? | Profile IDs and claimable placeholders ([0002](0002-player-identity.md)). |
| What can be trusted for a claim? | The placeholder's code, or the **verified** phone number from a phone sign-in. Never the phone a user types into their profile. |
| How does account deletion work when matches point at the user? | The user row is anonymised, not deleted, so scorecards stay intact. Teams they ran alone get a new admin. |
| What do join and QR links point to? | The server's own `/join/{code}` page. The app opens the same links, so there's one domain to set up later. |
| Where do photos live before hosting is decided? | An `images` table, served from `/images/{id}`. Pictures are resized to 512 px on the phone first. `ImageStore` is the only thing that changes when it moves to object storage. |

## Phase 3: following live

| Question | Answer |
|---|---|
| How does a follower see a ball within 2 seconds? | A WebSocket per match that sends the whole scorecard after each change, so a client can't drift. The current scorecard is sent only once the subscription is live, so nothing slips in between. The app falls back to polling if the socket drops. |
| What about a second server instance? | Watchers are kept in memory, which is right for one instance. A second instance needs Postgres LISTEN/NOTIFY between them. Documented in architecture.md. |
| How does push work without credentials? | Behind a `PushSender` interface: Firebase Admin when `FIREBASE_SERVICE_ACCOUNT` is set, logging otherwise. The inbox always fills, and it stores facts rather than sentences so the app can word them in the user's language. |
| Live score notifications without a foreground service? | Silent data pushes at each over and wicket update one quiet, ongoing notification per match. It goes away when the match ends, or after 3 hours without news. |
| When does a fixture "start" for notifications? | When its squads and toss are in, not when the tournament schedules it. |

## Phase 4: tournaments

| Question | Answer |
|---|---|
| How do seven formats fit one model? | Fixtures are ordinary matches. A pure `TournamentEngine` makes rounds lazily ([0003](0003-tournament-fixtures.md)). |
| Byes? | They go to the top seeds. The stage is named after the whole bracket, so a 6-team first round is still a quarter-final. The engine tests caught it when it was wrong. |
| How is NRR calculated? | The standard way. A side that's bowled out counts as having faced its full overs. Ties on points go to NRR, then wins, then seed. |
| A tied knockout match? | The higher seed goes through. There's no super over yet. |
| Two fixtures finishing at once? | A per-tournament lock, so the next round is only made once. |
| A fixture with no squads looked "complete" to the engine. Why? | All-out was being computed for an empty team. Fixed: an innings with no batters isn't complete. |

## Phase 5: stats

| Question | Answer |
|---|---|
| Recompute stats from deliveries every time? | No. A projection writes one line per linked player when a match completes, and rewrites it on undo. Career stats, filters and leaderboards add up those lines. |
| What's a "format" filter when every match is limited overs? | Match length: up to 10 overs, 11–20, and longer. |
| What's the MVP formula? | One documented formula ([features.md](../features.md#mvp-points)). Its top scorer is player of the match. The highest total across a tournament is player of the tournament. |
| How is the exit criterion checked? | `StatsEngineTest` plays 10 matches and recounts each player's numbers straight from the scorecards. |
| Voice scoring: how far to trust speech? | Runs and extras are recorded from speech. "Out" opens the wicket sheet, and anything unrecognised asks again. The parser understands English and Hindi cricket words. |

## Checking

- Every server test also runs on real Postgres 17 (`TEST_POSTGRES_URL`, and in CI), not just H2.
- Screenshot tests (Robolectric + Roborazzi) render the main screens to `app/shared/screenshots/`. Looking at them caught two bugs the compiler and tests couldn't: notification text cut to one line, and the tournament table never being shown in tests.

## Still open

- **Hosting** (Cloud Run vs Fly.io). It blocks the staging deploy and the move from the `images` table to object storage.
- **The public domain** and its `assetlinks.json`, for verified App Links.
- **A Firebase service account** on the server, for push.
- **The home screen widget** (Level 3), deferred.
