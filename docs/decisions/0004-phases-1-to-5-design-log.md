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
| What does the home screen widget show, and how does it stay current? | The match Home would put first (yours, then live, upcoming, recent). It refreshes on live-score pushes, when you leave the app, and every 30 minutes, the shortest Android allows. |
| A fixture with no squads looked "complete" to the engine. Why? | All-out was being computed for an empty team. Fixed: an innings with no batters isn't complete. |

## Phase 5: stats

| Question | Answer |
|---|---|
| Recompute stats from deliveries every time? | No. A projection writes one line per linked player when a match completes, and rewrites it on undo. Career stats, filters and leaderboards add up those lines. |
| What's a "format" filter when every match is limited overs? | Match length: up to 10 overs, 11–20, and longer. |
| What's the MVP formula? | One documented formula ([features.md](../features.md#mvp-points)). Its top scorer is player of the match. The highest total across a tournament is player of the tournament. |
| How is the exit criterion checked? | `StatsEngineTest` plays 10 matches and recounts each player's numbers straight from the scorecards. |
| Voice scoring: how far to trust speech? | Runs and extras are recorded from speech. "Out" opens the wicket sheet, and anything unrecognised asks again. The parser understands English and Hindi cricket words. |

## Phase 6: cricket network

Built after Phase 5 in the same stretch, because it needed no decisions from outside the code.

| Question | Answer |
|---|---|
| Who can see the phone number in a reply to a "looking for" post? | Only the post's author. Replies are one per person, never on your own post, and the public list shows only how many there are. |
| How long does a post stay up? | Until its date passes or the author closes it. Old posts drop off the list on their own, with no clean-up job. |
| Who can post for a team? | Only its admins. Anyone can post for themselves. |
| How is "opposition strength" measured for rankings? | Elo for saved teams (it's simple, and well understood by players). A player's MVP points are scaled by the rating of the side they came against ([features.md](../features.md#rankings)). |
| Store the ratings, or compute them? | Compute them on request. Local cricket is small enough per city, and a stored rating would need rewriting on every undo. Worth revisiting if the query gets slow. |
| Who keeps the directory honest? | Anyone signed in can add an entry; only whoever added it can remove it. Moderation waits until there's abuse to moderate. |
| What does a streamer need from the overlay? | A URL to paste into OBS or Larix as a browser source: a transparent score bar at `/overlay/{matchId}` that polls `/m/{matchId}/overlay.json` every 2 seconds. Polling, not the WebSocket, so it survives a flaky stream PC. It's public, like the match page. |

## Checking

- The real APK was driven on an emulator against the new server (on a throwaway Postgres), through onboarding, a team with a placeholder, a match from a saved squad to its result, profile stats, a best-of-three tournament to its second fixture, and the home screen widget. That run found four bugs (a Google sign-up blocked by Firebase's empty phone string, a fixture card showing its placeholder toss, "1 matches", and a stretched form chart), all fixed in 8e53b2a.

- Push was tested on the emulator with real FCM messages to its own token: the app registered the token with the server after sign-in, and messages showed both in the background (shown by Firebase) and in the foreground (shown by `PushService`). That test found background pushes landing in Firebase's generic channel; the app now creates its channels at start and declares the default. Crashlytics 20.1.0 and Analytics initialise on start. The silent live-score messages are covered by `PushServiceTest` (Robolectric): the server's exact payloads delivered to the real `PushService` post one quiet, ongoing notification per match, update it in place, and remove it when the match ends. Not tested: the server sending through Firebase Admin (needs a service account key; none is on this machine, and the Firebase CLI's personal login shouldn't stand in for one), and a crash report reaching the console (that would mean uploading a fake crash to the project).
- Phase 6 was driven on the emulator the same way: a "looking for" post made, listed and closed, a directory entry added and removed, both rankings tabs, and the overlay link from a live match's share sheet. The overlay page was loaded in Chrome and matched the app's score. That run found three small bugs, all fixed: your own posts were listed twice, "nothing here yet" showed before a city was entered, and the overlay ran the two batters' names together. Replying to a post needs a second account, so it's covered by `CommunityTest` instead.
- Every server test also runs on real Postgres 17 (`TEST_POSTGRES_URL`, and in CI), not just H2.
- Screenshot tests (Robolectric + Roborazzi) render the main screens to `app/shared/screenshots/`. Looking at them caught two bugs the compiler and tests couldn't: notification text cut to one line, and the tournament table never being shown in tests.

## Still open

- **Hosting** (Cloud Run vs Fly.io). It blocks the staging deploy and the move from the `images` table to object storage.
- **The public domain** and its `assetlinks.json`, for verified App Links.
- **A Firebase service account** on the server, for push.
- **Paid insights or an organiser plan** (Phase 6). It needs a pricing decision.
- Nothing deferred within Phases 0–6 beyond the items above, which need decisions or credentials.
