# App revamp feedback and implementation brief

Implementation brief for the Android app and Ktor server. This captures feedback on match creation and the app's information architecture; it is not an implemented feature. Preserve existing match and team data.

## Home, feed, tournaments, and profile

The current Home mixes the user's unfinished matches, everyone else's live/upcoming/recent matches, and links to teams, tournaments, leaderboards, and community. These are different jobs and need distinct destinations. Keep **Start a match** prominent at the top of Home; do not repeat the earlier mistake of moving the primary action below the match list.

Recommended four top-level tabs:

| Tab | Purpose | Main content |
|---|---|---|
| **Home** | Act on my cricket | Start a match; my scheduled, live, and unfinished matches; a short route into recent personal matches. Prioritise resuming scoring without making the start action hard to find. |
| **Feed** | Discover what people are playing | Recent and live matches across users, including followed players/teams and the local community. Provide clear filters such as Following, Nearby/City, and All, plus live/recent where useful. Match cards open public scorecards. |
| **Tournaments** | Follow and manage competitions | Tournaments I organise or joined, discoverable tournaments if supported, schedules, fixtures, standings, and tournament-specific leaderboards. Do not mix tournament administration cards into the general match feed. |
| **Me** | My identity and records | Profile, career/player stats, my teams, my match history, settings, and account actions. Player stats should not compete with the match feed on Home. |

The existing Community screen contains looking-for posts, rankings, and a directory. Put it behind an explicit **Community** section within Feed (for example a Matches/Community switch) or give it its own tab only if usage justifies a fifth destination. Keep community posts visually distinct from match results, and keep tournament fixtures identifiable when they appear in Feed. This is a navigation proposal, not a mandate to flatten these content types into one list.

Navigation should preserve each tab's scroll/filter state when switching, keep one clear selected tab, and handle Back, match/team/player detail screens, and deep links predictably. Decide whether the bottom bar remains visible on details; use one consistent rule. Keep the create-match action easy to reach from Home. Provide loading, empty, error, and refresh states for each destination, rather than one failure message for all content.

### Existing code and feed gap

- `HomeScreen.kt` renders `TodayFeed.mine`, `live`, `upcoming`, and `recent` in one `LazyColumn`, then secondary destination tiles. `AppNavigation.kt` uses a single stack rooted at Home; tournaments, community, and profile are pushed routes rather than top-level tabs.
- `SocialService.feed()` already reads recent matches globally and sorts followed teams/players first. `TodayFeed` in `core/.../model/Social.kt` is shaped for the Home dashboard: it inspects the latest 100 matches and caps completed results at 30. It is not a browsable history of community matches.
- Keep a small Home summary, but give Feed its own paginated/filterable API and repository/UI state. Define a stable cursor/order, prevent duplicates across pages, and decide which matches are publicly discoverable. Followed/nearby ordering should be explicit. Do not simulate an endless feed by polling the same bounded `TodayFeed` response.
- Existing `GET /search?q=` already returns teams, players, and matches through `SearchResults`. Reuse or extend it for team selection and discovery; evaluate pagination, ranking, and visibility before adding another search endpoint.
- Profile already embeds `CareerSection`; tournaments and community already have dedicated screens. Reorganise navigation and entry points around them instead of rebuilding those features from scratch.

### Navigation acceptance criteria

1. Home shows the primary Start a match action and my actionable matches without a long list of unrelated community results.
2. Feed shows recent matches beyond my own and can browse further than the present 30 completed results. It distinguishes live, scheduled, and completed matches and identifies tournament fixtures.
3. Tournaments have a stable top-level destination; profile and career stats are discoverable from Me.
4. Community posts/directory remain reachable and are visually distinct from match cards.
5. Switching tabs preserves useful state; deep links and Back return to the expected destination. Existing match, tournament, player, team, notification, and share links still work.
6. Test light/dark appearance, large text, long lists, empty/error states, and tab behavior on an emulator.

## New match goal

Make creating a match feel like setting up a real game, whether it is an immediate, one-off game or a scheduled game between established teams. A saved team is a persistent database entity, a match lineup is the subset playing today, and a player identity is what career statistics attach to. The UI must keep those concepts distinct.

## New match flow

Use three pages with visible progress and Back/Continue controls. Keep entered data when moving between pages or returning from a picker. The primary action must remain easy to find; never bury it below the roster or match feed.

### 1. Set up Team A

- Choose **Find a team**, **Create and save a team**, or **One-off team**.
- Find a team searches the database, not just teams the scorer belongs to. Show team name, city, logo, and enough context to distinguish similarly named teams. Selecting one links its team ID to the match; it does not grant admin rights or change that team's roster.
- Create and save creates a persistent team through the existing team API. Make the save explicit. The creator becomes its admin and can manage invitations and members later.
- One-off team asks for a name and lineup without silently creating a permanent team. Allow an explicit **Save as a team** action later if the scorer wants to keep it.
- Show a compact lineup list with player identity state and a prominent **Add player** action. A saved team's full membership is a source for selection, not automatically the playing XI. Allow captain and keeper selection without crowding the initial roster entry.

### 2. Set up Team B

Use the same choices and lineup UI as Team A. The opponent can be an existing public team without the scorer being a member. Do not let selecting an opponent edit its permanent roster. Prevent the same persistent team or player identity from occupying both sides of one match.

### 3. Match details

- Set overs, match rules, location, and an optional date/time. Summarise both teams and their selected lineups here, with links back to edit them.
- Offer clear outcomes: **Schedule match** (create an upcoming match, return to its details) and **Play now** (create/open the match, then enter the pre-scoring setup). The user should not be required to enter a toss to schedule a game.
- The pre-scoring setup confirms the final lineups, records toss winner and bat/bowl choice, then starts scoring. A scheduled match opens this same setup from its details when it is time to play. Display its date/time and status on Home and match details.
- Decide and display whether a scheduled game may have incomplete lineups. Recommended: allow it; require valid playing lineups only when starting scoring. Match rules and teams must be valid when scheduling.

## Add player experience

Replace the current inline name field and small plus button with an **Add player** sheet or page. Offer these routes, with clear labels:

1. **Find an existing player:** search registered players and eligible placeholders, inspect name/city, then add the selected profile ID to this match lineup. Do not create a duplicate by typing an existing person's name.
2. **Invite to the team:** for a persistent team, share its existing join link/code. Joining the team and being selected for this match are separate actions. Refresh the roster when an invitee joins; let the scorer add them to the lineup. A one-off team cannot offer a permanent team join link until saved.
3. **Add by phone:** capture name and phone with appropriate validation, create a claimable player identity, and select it for the lineup. The existing backend supports phone-linked placeholders for persistent teams. Extend the match flow deliberately for one-off teams; do not expose whether a phone number belongs to an existing account. Only the person signing in with a verified number can claim that identity.
4. **Add guest by name:** a fast path for an unregistered player, visibly marked **Guest**. Explain that a name alone is not a reliable career identity. If retrospective claiming is in scope, give the guest a claimable identity/code and define how completed match statistics are transferred; otherwise say that guest statistics remain on the match scorecard only.

Show a player's source and state in the lineup: registered, invited/pending, phone placeholder, or guest. Handle duplicate names without conflating identities. Match statistics must use stable player/profile IDs, never name matching. Avoid displaying or sharing another player's phone number in a public team directory or match page.

## Current implementation and gaps

| Area | Existing behavior | Required change |
|---|---|---|
| New match UI | `app/shared/.../ui/newmatch/NewMatchScreen.kt` is one long form with team names, inline guest entry, overs, rules, toss, and a pinned Start button. | Split into the three pages and a separate pre-scoring setup; simplify roster entry. |
| Form state | `NewMatchViewModel.kt` holds both sides and calls `CreateMatchRequest` once. `loadMyTeams()` only loads the caller's teams. | Keep one draft across pages, support team directory search, persistent team creation, player identity choices, scheduling, and resume/retry behavior. |
| Team picker | `SquadSheets.kt` picks from `myTeams` and loads the chosen saved team's members. | Use existing global team search alongside mine; allow one-off teams and explicit creation. Keep squad selection separate from team membership. |
| Team and player APIs | `core/.../api/TeamsApi.kt` and `TeamRoutes.kt` already have team creation, members, join codes, player search, phone placeholders, and claims. `GET /search?q=` already returns teams. | Reuse or extend team search for the picker; define visibility and pagination. Extend placeholder creation for a match without a permanent team if that flow is supported. |
| Ordinary matches | `CreateMatchRequest` in `core/.../api/Api.kt` requires a toss. `MatchService.create()` creates `IN_PROGRESS` immediately. | Separate match creation/scheduling from starting play, with validation appropriate to each transition. Existing clients and server tests must be updated together. |
| Scheduled matches | `MatchStatus.SCHEDULED`, `scheduledAt`, and a start-with-squads-and-toss flow exist for tournament fixtures (`TournamentsApi.kt`, `TournamentRoutes.kt`, `MatchViewModel.kt`, `FixtureStart.kt`). | Reuse the state and UI where appropriate, but allow ordinary scheduled matches with clear scorer/organiser permissions. Do not make them fake tournament fixtures. |
| Navigation and feed | `AppNavigation.kt` replaces the new-match form with a match route after creation. `HomeScreen.kt` displays upcoming matches. | Route both outcomes to match details; scheduled matches must reopen their pre-scoring setup. Preserve the primary Start a match action at the top of Home. |

Relevant server implementations are `server/src/main/kotlin/com/harsh/desicrics/service/Services.kt` and `TeamServices.kt`. Schema migrations live under `server/src/main/resources/db/migration/`. Use a new Flyway migration for any schema change; do not edit a migration already applied.

## Interaction and visual requirements

- Design each page around one decision at a time. Use a step label, short title, concise helper text, and a persistent bottom action that does not overlap the keyboard or navigation bar.
- The team chooser needs search results, empty/error/loading states, and a clear distinction between **Your teams** and discoverable teams. Do not label the entire chooser “saved teams” when it is a database search.
- The lineup should read like a roster, with large tap targets and a visible add action. Avoid an empty text field and plus icon as the dominant UI. Show selection count and the minimum required to start.
- Use existing Crease tokens and shared components in `:app:designsystem`; keep light and dark themes, dynamic text scaling, accessibility labels, and sunlight legibility. Validate the actual screens on an emulator, including the keyboard and bottom action.
- On a scheduled match page, distinguish **scheduled**, **ready to start**, and **in progress**. Keep toss out of scheduling. Never make a label imply the game has started before scoring can begin.

## API and data rules

- Model match lifecycle explicitly: draft in the client, scheduled/upcoming on the server, pre-scoring setup, in progress, completed. Define the transition permissions and prevent duplicate starts from repeated taps or requests.
- A saved team ID on a match means the match used that team. Do not change the team's permanent roster when the match lineup changes.
- Preserve existing `IN_PROGRESS`, `SCHEDULED`, and completed records and existing tournament fixture behavior. Migrate old data safely if the ordinary match contract changes.
- A phone-linked placeholder can be claimed only through verified sign-in or a secure claim mechanism. Keep phone data private, normalise it consistently, and avoid account enumeration in search/add responses.
- Define how career statistics work for registered players, placeholders before/after claim, and guests. The current `Player.profileId == null` guest path does not provide a stable career identity.
- Confirm who may start or edit a scheduled match involving a public opponent team. Selecting a public team must not imply its admins approved participation; consider an invitation/confirmation state if official team representation is important.

## Acceptance criteria

1. A user can create an immediate match through Team A → Team B → Match details, record toss only in pre-scoring setup, and begin scoring.
2. A user can schedule a match without a toss, find it on Home, reopen it, confirm lineups/toss, and start it once.
3. Either side can use a discoverable existing team or an ad hoc team. A new permanent team is saved only after an explicit action.
4. A persistent team can share a join link; the invitee's membership and match lineup selection are independently visible.
5. Add player supports registered search, phone-linked placeholder, invitation where applicable, and guest entry, with honest identity/career labels.
6. Validation points to the affected page and field; Back/Continue preserves entered data, and loading/errors allow retry without creating duplicates.
7. Existing ordinary matches, tournament fixtures, scoring, career records, and team membership continue to work.
8. Light and dark emulator screenshots cover all three pages, the player picker, scheduled match details, and pre-scoring toss; relevant core, server, and Android tests pass.

## Decisions to settle before implementation

- Can a name-only guest claim completed-match career statistics later? If yes, issue a stable placeholder and claim mechanism at creation.
- Is a public team immediately selectable as an official opponent, or must a team admin confirm the fixture? Team discovery and representation permissions depend on this.
- Can scheduled matches be created with no players yet? The recommended answer is yes; enforce lineup minimums at start.
- Who may edit, cancel, or start an ordinary scheduled match, and what happens if its scheduled time passes?

These questions should not block building the three-page UI shell, but backend contracts and final copy should follow the chosen answers.

## Screen review and interaction direction

This review uses the [nine repository screenshots](../app/shared/screenshots/), the Home and New Match emulator screens seen during the design pass, and the navigation and feed behavior described above. The repository images show example states from an earlier build. The recommendations below are design proposals awaiting usability testing.

### What makes the screens feel generic

The Home example gives Teams, Tournaments, Leaderboards, Take over scoring, and Community almost identical outlined buttons. They look equally urgent even though a scorer taking over an innings and a spectator browsing results have different intentions. The New Match example repeats two team cards with text fields, pills, and removable chips before the user reaches the toss. The player profile gives nine batting figures equal prominence, with recent form below the fold. Rounding those boxes or changing their green leaves the same decisions competing for attention.

The scoring screen already has a stronger visual language. At 14/0, the total reads first, the over sits beside it, and the six delivery marks explain how the over unfolded. Carry that grammar into match lists. Align teams with their scores, use fixed-width figures for overs, and give one line to the state of play. A scheduled row needs teams and start time. A chase needs the target equation. A completed game needs the winner and margin. Keep team colours, crests, and photos where they identify someone. Reserve the strongest surface on Home for starting a match.

### Home match card

The current card stacks both teams in separate rows, limits each name to one line, and mutes the team that is waiting to bat. In a list of fixtures the same white surface repeats, and the small LIVE label carries most of the work of distinguishing a game in progress. Prototype this side-by-side layout beneath **Start a match**.

```text
● LIVE                                 10 overs

Andheri Strikers          Powai XI
82/5                      57/3
10 overs                  7.2 overs · Batting

Powai XI need 26 off 16 balls
```

- Give each team half the width. Use bold Figtree for **both team names** in the primary text colour. Place each score directly underneath in larger Barlow Condensed numerals, with smaller overs below. Keep the team order steady when the innings changes. Move the Batting marker to the side at the crease.
- Use the same neutral surface for all match cards. The small LIVE dot and label carry the status colour. Keep the strongest fill on Start a match.
- Write one useful footer for each state. A scheduled card gives the start time, such as “Sunday, 3:30 pm”. During a chase, show the equation. After play, show the result, such as “Andheri Strikers won by 4 runs”. Before a team bats, show “Yet to bat” in its score slot. Avoid showing 0/0 for an innings that has never started.
- Let long names wrap and align the scores below the taller name. On narrow screens or with large text, stack the team blocks when both names and scores cannot fit comfortably. Tapping anywhere opens that match. TalkBack should read each team together with its score and overs.
- Review live, scheduled, and final cards **together** on Home, in light and dark mode. A reader should be able to point to the live game, name both teams, and tell who is batting without opening a card. Include two long club names and a team still waiting to bat in the review.

### Patterns worth borrowing

| Observed pattern | Application to DesiCrics |
|---|---|
| Play-Cricket Scorer lets a scorer select a lineup in advance, return to it, and assign roles from the player row. [ECB team selection](https://play-cricket.ecb.co.uk/hc/en-us/articles/360006032537-Selecting-a-Team-PCS) | Reopen the same lineup on match day. Show captain and keeper beside the names. Let tapping a row edit that person while retaining the batting order. |
| Play-Cricket uses a direct scoring control for ordinary deliveries and asks for dismissal-specific details on a wicket. Undo addresses the previous ball. [Scoring controls](https://play-cricket.ecb.co.uk/hc/en-us/articles/360000431729-Scoring-A-Match-Balls-Runs-Extras-Wickets-Standard-And-One-Click-Modes-PCS-Pro) · [Undo a ball](https://play-cricket.ecb.co.uk/hc/en-us/articles/360000422365-Undo-A-Ball-PCS-Pro) | Keep the run pad stable. Ask for a fielder only when the selected dismissal needs one. Identify the delivery being corrected by over, ball, and outcome. |
| Strava explains how recent activity is ordered and preserves the order of items already seen during refresh. [Strava feed ordering](https://support.strava.com/en-us/articles/15402105-how-does-strava-order-my-activity-feed) | Default the community results view to latest finishes. Update a visible live score in place. Offer “New matches” above the list when fresh entries arrive so a reader can choose when to move. |

### Behaviors to prototype

| Surface | Interaction and visible result |
|---|---|
| Home and Feed | Keep **Start a match** visible on the first Home viewport, including for a returning scorer. Immediately below it, show the scorer's active game with **Resume scoring**. On Feed, tapping the same game opens the spectator scorecard. Back restores the same match row and filter. A new user with zero teams or follows can still browse recent public games. |
| Team A and Team B | Replace the large chip collection with a batting-order list. The whole row selects a player and the checkmark confirms it. Keep search text when adding several players. Show “4 selected” beside Done. After a phone addition, show the new player by name with their claim status. After sharing an invite, wait for a real join before offering the player for selection. |
| Schedule and toss | Show a compact fixture preview using the actual team names. Choosing Later reveals date and time and changes the primary action to **Schedule match**. Reopening the fixture shows that time and **Start match** to the authorised scorer. At the ground, ask who won the toss using the two team names, then Bat or Bowl. Preview “Powai XI will bat first” before scoring begins. |
| Live scoring | Preserve the bottom keypad and over strip shown in the screenshots. Tapping 4 should update the total and append one delivery on acknowledgement. A pending tap has visible feedback. If the connection drops, retain the last confirmed score and resolve that delivery before accepting a retry. Undo names the last event and updates the total, strike, and strip together. Optional motion briefly highlights the changed score and respects reduced motion. |
| Watching a match | Keep Live, Scorecard, and Overs within one match. Tap an over to expand its deliveries or a batter to open their innings figures. Returning restores the selected innings and over. A spectator who loses connection sees when the score last updated. |
| Tournaments | Keep the compact standings table in the screenshot. Make the team name a route to that team's fixtures within the competition. A completed fixture opens its scorecard. Back returns to the same table or fixture date. Put organiser actions alongside the fixture they affect. |
| Teams, players, and Me | On the team screenshot, invite and add are useful roster actions, while Change logo and Leave team draw attention during selection. Put those in team settings. On a player page, lead with recent form and figures for the selected discipline. Tapping an innings in the form history opens its scorecard. Keep a full stats table one tap away and explain abbreviations such as HS and SR on demand. |
| Search, community, and notifications | Group search results by teams, players, and matches. City and crest help distinguish two teams with the same name. A community post opens its details and response action. In the notification example, a milestone, result, and time change all use initials and a dot. Use the event, match names, and time to identify each row. A reschedule opens the fixture with its revised time visible. |
| Entry and recovery | After sign-in from a team invite, return to that team with a clear Join action. Keep the phone number editable after an OTP delivery failure. On a failed feed refresh, retain the last list, mark it as stale, and offer Retry. Show an empty-results message only after a successful response. |

### How to evaluate the redesign

Prototype the three journeys below with realistic names and scores before applying the treatment to every screen. Record wrong turns, repeated taps, and lost selections. Use a small phone, large text, and TalkBack for the controls that depend on icons, colour, or gestures. These are proposed checks, not observed user-test results.

1. **Organiser on Friday.** Find an opponent outside My teams, add a player by phone, and schedule Sunday’s match before the toss. Back up one step and change the time. Verify that both lineups survive and only one fixture exists.
2. **Scorer at the ground.** Open the scheduled fixture, confirm a late replacement, record the toss, enter a boundary, and correct the last ball. Interrupt connectivity after submission. Verify that the scorecard agrees with the keypad and the delivery appears exactly once.
3. **Spectator with no follows.** Find a recent community result, inspect a player's innings, then return to the original feed position. Open a tournament table and find that team's next fixture. Verify that the spectator can complete both tasks without entering match creation or scoring.

Prioritise Home, match creation, and the scorer's last-ball feedback for the first interactive walkthrough. Follow with Feed → scorecard → player and Tournaments → fixture. These journeys exercise the four proposed tabs and give the visual decisions a concrete cricket task to support.
