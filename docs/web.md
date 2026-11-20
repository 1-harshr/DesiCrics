# Web app

A React web app for people who don't have DesiCrics installed. It covers the same product as the Android app, in
the same Crease look, adapted to the browser: real URLs for every page, layouts that use a wide screen, hover and
keyboard support. It talks to the same Ktor server and uses the same scoring rules.

Status: ✅ done · 🟡 partial · ⬜ to do. Status refers to the `web` branch until it merges.

## Who it's for

1. **Followers with a shared link.** Someone opens a WhatsApp link to a match, tournament or team invite. Today
   they get a bare HTML page from the server; the web app gives them the real live scorecard. This is the
   biggest group, so it comes first, and it needs no sign-in.
2. **Players without the app,** or on an iPhone, which the Android app doesn't cover. They sign in, set up a
   profile, follow teams, join teams and enter tournaments.
3. **Organisers at a laptop.** Setting up tournaments, fixtures and auctions is easier on a big screen, and the
   auction hall is meant for one.
4. **Scorers in a pinch,** on a borrowed phone or a laptop at the ground.

## Decisions

| Area | Choice | Why |
|---|---|---|
| Location | `web/` at the repo root, its own npm project | Next to `server/` and `proxy/`; Gradle doesn't build it |
| Build | Vite, React, TypeScript | Static files, fast dev server, no server rendering needed |
| Routing | React Router (library mode) | Every page has a URL; the app's `NavKey` routes map one to one |
| Server data | TanStack Query | Caching, retries and refetch on focus, the job the app's repositories do |
| Auth | Firebase JS SDK: phone OTP with invisible reCAPTCHA, Google popup | Same Firebase project and ID tokens, so the server needs no change |
| Scoring rules | `:core` compiled to JavaScript with Kotlin/JS and called through a small JSON facade (`WebCore`) | One rules engine for phone, server and browser, as the architecture asks. No TypeScript port to drift out of step |
| API types | TypeScript types in `web/src/api/types.ts` that mirror the `core` DTOs | The wire format is JSON; the facade covers the computed parts |
| Styling | CSS Modules over Crease tokens as CSS custom properties | No styling library; tokens stay in one file like `:app:designsystem` |
| Design system | `web/src/designsystem/`: tokens, fonts, icons and components | Features never define their own colours, type or shared components, same rule as the app |
| Fonts | Barlow Condensed, Figtree, JetBrains Mono, self-hosted | Same files as the app; no Google Fonts request on a scorecard |
| Icons | SVG generated from the app's Material Symbols vector drawables (`npm run icons`) | The same icon set without downloading it twice; a script keeps them in step |
| Text | i18next. The app's `strings.xml` is copied to `src/i18n/app.generated.json` (`npm run strings`); web-only text is in `src/i18n/web.json` | Same words as the app and one translation for both. No literal UI text, same rule as the app |
| Tests | Vitest and Testing Library | |
| Hosting | Static files behind the existing Cloudflare proxy | One domain: shared `/m`, `/t` and `/join` links open the web app, API paths pass through to the server |

### Adapting to the web, not copying the phone

- **Below 720px** it looks like the app: bottom navigation, one column, run pad in the thumb zone.
- **From 720px** navigation moves to a side rail and content centres at 600px, as on tablets.
- **From 1100px** pages that have two things to show use two columns: scorecard and commentary, tournament table
  and fixtures, auction player card and purses.
- Every tappable thing has hover and focus states; dialogs and sheets close with Esc.
- The scoring screen takes keyboard shortcuts (`0`–`6`, `W` wide, `N` no-ball, `B` bye, `L` leg bye, `X` out,
  `U` undo).
- Bottom sheets become centred dialogs on wide screens.
- Pages people follow (a live match, a tournament) say "Get the app" once, in a dismissable banner; never on
  scoring screens.

## Working on it

```
cd web
cp .env.example .env.local      # API URL and Firebase web config
npm install
npm run dev                     # builds :core with Gradle, then serves http://localhost:5173
```

Run the server with `WEB_ORIGINS=http://localhost:5173 ./gradlew :server:run` so the browser may call it.
`npm test` runs Vitest; `npm run build` typechecks and builds to `web/dist`. After changing the app's
`strings.xml` or drawables, run `npm run strings` or `npm run icons`; CI fails if they're out of step.

## Server changes the web app needs

| Change | Phase |
|---|---|
| ✅ CORS for the web app's origins (`WEB_ORIGINS`), so it works from a dev server or its own host | 0 |
| Public JSON for shared pages: `GET /public/matches/{id}`, `GET /public/tournaments/{id}` | 1 |
| Public live feed: `/public/matches/{id}/live` WebSocket, read only | 1 |
| The proxy sends `/m/*`, `/t/*`, `/join/*` and the web app's own paths to the web app instead of the server's HTML pages | 1 |

Everything else uses the existing signed-in API.

---

## Phase W0 · Foundation

**Goal:** an empty web app in Crease that can call the server and run the scoring engine.

- 🟡 `web/` project: Vite, React, TypeScript, React Router, TanStack Query, i18next, Vitest. No linter yet
- ✅ Crease tokens as CSS custom properties, light and dark from `prefers-color-scheme`
- 🟡 Fonts bundled; type scale as classes. Shipped as TTF for now; WOFF2 would halve them
- ✅ Icons generated from `app/designsystem` drawables
- ✅ First components: buttons, card, section header, list card and rows, avatar, tag, live badge, ball chip,
  over strip, score header, match card, empty state, error state, error banner, loading
- ✅ App shell: bottom bar under 720px, side rail above
- ✅ `:core` JS target and `WebCore` facade: `MatchDetails` JSON in, scorecard view JSON out
- ✅ API client with base URL from `VITE_API_URL`, `ApiError` messages, ID token when signed in
- ✅ Server: CORS
- ✅ CI builds and tests `web/`

**Exit:** `npm run build` passes, the component catalogue page renders in both themes, and a test computes a
scorecard in the browser with `:core`.

## Phase W1 · Shared links

**Goal:** a shared match or tournament link opens a live, good-looking page in any browser, without signing in.

- ⬜ Server: public match and tournament JSON, public live feed
- ⬜ Match page `/m/:id`: score header, Live / Scorecard / Overs tabs, updates every ball; two columns on desktop
- ⬜ Tournament page `/t/:id`: points tables, fixtures, key stats, champion
- ⬜ Join page `/join/:code`: team name and how to join, with sign-in to join from the web
- ⬜ Not found and offline states
- ⬜ "Get the app" banner
- ⬜ Hosting and proxy routing

**Exit:** a match link sent on WhatsApp opens the live scorecard on an iPhone, and it updates ball by ball.

## Phase W2 · Sign in and you

**Goal:** someone without the app signs in, sets up a profile and follows what they care about.

- ⬜ Sign-in: phone OTP and Google
- ⬜ Onboarding: you, how you play, photo, claim players, team, follow, in the app's order and skip rules
- ⬜ Home feed: your live match first, then followed, then nearby
- ⬜ Profile (Matches / Teams / Stats), edit profile, photo upload
- ⬜ Search, player pages, follow and unfollow
- ⬜ Notifications inbox
- ⬜ Settings: theme, sign out, delete account, support form

**Exit:** a new user signs up on the web, follows a team, and sees that team's next match in their feed.

## Phase W3 · Score a match

**Goal:** a full match scored from a browser, with the same rules and the same result as on the phone.

- ⬜ New match: teams → squads → rules, presets, toss
- ⬜ Scoring: run pad, extras, wicket flow with fielders, undo, strike and bowler changes, end of innings
- ⬜ Retire, substitutes and latecomers, revise target, declare, end early
- ⬜ Take over scoring with a handover code, both ways between phone and web
- ⬜ Wagon wheel
- ⬜ Keyboard shortcuts

**Exit:** a 5-over match scored on the web matches, ball for ball, the same match scored on the phone.

## Phase W4 · Teams, tournaments and community

**Goal:** everything around matches.

- ⬜ Teams: list, create, team page, members, admins, join codes, join requests, insights, head to head
- ⬜ Tournaments: list, create, entry settings, player pool, squads, offers, pick-up teams, start, fixtures,
  scheduling, organisers
- ⬜ Stats: player stats, compare, leaderboards
- ⬜ Community: looking for, directory, rankings

**Exit:** an organiser runs a tournament from a laptop, from entries to the final, while teams enter from the app.

## Phase W5 · Big screen and extras

**Goal:** the things a browser does better than a phone.

- ⬜ Auction hall on a projector, and online bidding
- ⬜ Share studio and posters, downloaded as images
- ⬜ Themes from Phase 8
- ⬜ Installable (web app manifest), and web push for followed matches
- ⬜ Stream overlay moved from the server's page into the web app

**Exit:** an auction runs with the hall on a projector and owners bidding from the web and the app.
