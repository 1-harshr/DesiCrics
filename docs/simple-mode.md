# Simple mode · A bare-bones DesiCrics

**Goal:** someone who only wants to score a match can do that without wading through tournaments, auctions, feeds and posters. One switch turns the app into a minimal version of itself, and the same switch turns it back.

Choices marked **(default)** are what we build unless we decide otherwise.

## The idea

Simple mode is **a second front end on the same app**. It has its own screens and its own navigation, built for one job, and shares everything underneath with Full mode: the view models, repositories, API client, scoring engine and design system. Nothing is stored twice, and a match scored in Simple mode is an ordinary match in Full mode.

| | Simple | Full |
|---|---|---|
| Who it's for | Players who score their own games **(default)** | Organisers, followers, anyone who wants everything |
| Tabs | Home, Me | Home, Feed, Tournaments, Me |
| What it does | Score a match, see the scorecard, share the result, keep teams | Everything |

## Where the switch lives

1. **Me → Settings → "App mode"**, the main place, in both modes. A row like Theme, showing "Simple" or "Full"; tapping it opens a sheet with the two choices, each with one line on what it shows. The change is immediate. **(default)**
2. **During sign-up**, one question after the profile steps: "How will you use DesiCrics?", with "Score my matches" (Simple) and "Everything: tournaments, feed, stats" (Full). Simple is picked to start with. **(default)**
3. **At the edge of Simple mode**, the way out when it's needed:
   - At the bottom of Home, one quiet line: "Want tournaments, the feed and more? Switch to Full mode."
   - When a link opens something Simple mode doesn't have (a tournament, a player's page), a small page says "This is part of Full mode" with a button to switch.

People already using the app stay on **Full**, so nobody loses what they're used to. Only new sign-ups are asked.

The mode is stored **on the phone**, like the theme, not on the account **(default)**. It needs no server change, and a new phone asks again during sign-in.

## What Simple mode has

### Navigation
- Two tabs: **Home** and **Me**.

### Home
- The "Start a match" card, your live and recent matches, and **Take over scoring**.
- A line at the bottom to switch to Full mode.

### New match
- One page: the two teams (a name and players typed in, or a saved team in Level 2), **overs** and **the toss**.
- Rules are Relaxed, with the wagon wheel and powerplay off.

### Scoring
- The batters, the bowler and this over, with pickers that open by themselves when someone's needed.
- Runs, wide, no-ball, out and undo. Out asks how, and for a run out, who.
- When the match ends, the result.

### Match screen
- Live and Scorecard. Share sends the result card (Level 2).

### Me
- Profile, your matches, App mode, sign out. Theme and delete account in Level 2.

### Not in Simple mode
Feed, Community, Tournaments (and everything in tournament entry), auctions, leaderboards, search, follows, the notifications inbox, posters and overlays, the wagon wheel, byes and leg byes, penalties, retirements and rain rules. Their data stays put; switching to Full shows it all again.

## How it's built

Two UI domains side by side, one switch at the root, and no mode checks anywhere else.

```
shell/              AppShell: reads the mode, shows FullApp or SimpleApp. AppModeSheet, used by both.
navigation/ + ui/   Full mode: its shell, routes and screens (as today).
simple/             Simple mode: its own shell, routes and screens.
                    ── both use ──
ui/**/*ViewModel.kt screen logic: state and actions, no drawing
data/               repositories, preferences
:core               models, API types, scoring engine
:app:designsystem   Crease components (buttons, cards, run pad, sheets)
ui/system, ui/text  platform helpers (share, push) and shared wording (results, rule labels)
```

### 1. One switch, at the root

`AppShell` is the only code that knows both modes exist:

```kotlin
when (mode) {
    AppMode.FULL -> AppNavigation(profile, …)
    AppMode.SIMPLE -> SimpleApp(profile, …)
}
```

`AppMode` is a plain enum held by `ModePreference`, like `ThemePreference`: local settings, key `app_mode`, a `StateFlow<AppMode>`. A missing value means FULL, which is how existing users stay on Full. Switching mode swaps the whole shell, so each mode's back stacks start fresh and nobody lands on a screen the other mode doesn't have.

### 2. Separate views

Simple mode has its own `SimpleHomeScreen`, `SimpleNewMatchScreen`, `SimpleMatchScreen` and `SimpleMeScreen`, and its own routes. None of its screens calls a Full screen, and no Full screen knows Simple mode exists. A screen in either mode shows everything it has; nothing is hidden by a flag.

So some drawing exists twice (Simple's scoring screen and Full's both draw a run pad). That's the price of keeping them apart, and it stays small because the pieces both need live in the design system (`RunPad`, `OverStrip`, `MatchCard`, `ListRow`, `DesiSheet`).

### 3. Shared logic

Simple's screens use the same view models as Full's: `HomeViewModel`, `NewMatchViewModel`, `MatchViewModel`, `ProfileViewModel`. These hold state and actions only (record a ball, undo, create a match), so the rules of cricket, saving, retries and offline handling exist once. Where a view model grows something only one mode needs, that part moves to the mode's own view model rather than becoming a flag.

Settings that differ by mode belong to the screen that owns them, not to a branch in shared code. Simple's new match screen sets the rules to Relaxed without the wagon wheel when it opens; the view model sends whatever it's given.

### 4. The boundary is tested

`ModeBoundaryTest` reads the source and fails if:
- anything in `simple/` imports from `ui/` other than a view model file (`*ViewModel.kt`, `*ViewModels.kt`), `ui/system` or `ui/text`;
- anything in `ui/` or `navigation/` imports from `simple/`;
- anything outside `shell/` imports both shells.

### Links

Each shell handles links itself. Simple opens match links (`/m/{id}`) in its own match screen. Links to things Simple doesn't have open the "part of Full mode" page (Level 2); until then they're ignored.

### Also

- **No server or API changes.**
- **Analytics:** a `mode_changed` event with the mode, to see whether Simple mode gets used.
- **Strings:** all of Simple's text is in `strings.xml` under `simple_`.

## Tests

- `ModePreference`: defaults to Full, remembers a change.
- `ModeBoundaryTest`, as above.
- Screenshot tests for each Simple screen (Home, New match, Match while scoring and when finished, Me), light and dark. Full's screenshots don't change.

## Levels

- **Level 1 · Playable:** `AppMode`, `ModePreference`, `AppShell` and the boundary test. The App mode row and sheet in Full's Me settings. Simple's shell with Home and Me; Simple Home (start a match, your matches, take over scoring, the "Switch to Full mode" line); Simple new match (two teams typed in, overs, toss); Simple match screen (pick batters and bowler, runs, wides, no-balls, wickets, undo, the result); Simple Me (profile, App mode, sign out).
- **Level 2 · Complete:** Simple scorecard tab and result sharing; saved teams in Simple new match; Theme and delete account in Simple Me; the onboarding question; the "part of Full mode" page for links.
- **Level 3 · Delight:** a one-time tip on Home for existing users that Simple mode exists; the mode chosen per account if people sign in on more than one phone.

**Exit:** a new user picks "Score my matches" at sign-up, sees only Home and Me, starts a match choosing nothing but teams, overs and the toss, scores it, and shares the result card. They switch to Full mode in Settings and find the match in their history with everything else back.

## Open questions

1. **Who is Simple mode for?** Written here for players scoring their own games. Should small organisers running a quick league get a cut-down tournament in Simple mode too?
2. **Default for new users:** written as Simple, with Full one tap away. The other way round is equally reasonable.
3. **What else must stay?** Written as scoring, scorecard, sharing the result, teams and career stats. Anything to add or drop?
4. **Phone or account:** written as stored on the phone, like the theme. Per account means a small server change (Level 3).
