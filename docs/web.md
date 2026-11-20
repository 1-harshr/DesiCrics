# Web app

A view-only React web app for people who don't have DesiCrics installed. They can follow live matches,
scorecards, tournaments, teams, players and stats in a browser, in the same Crease look as the Android app,
adapted to the web with real URLs, wide-screen layouts, and hover and keyboard support.

It doesn't do anything that changes data: no sign-in, no scoring, no creating or managing matches, teams or
tournaments. Those stay in the app, and the web app points people there when they want to do more.

Status: ✅ done · 🟡 partial · ⬜ to do. Status refers to the `web` branch until it merges.

## Who it's for

1. **Followers with a shared link.** Someone opens a WhatsApp link to a match or tournament. Today they get a
   bare HTML page from the server; the web app gives them the real live scorecard. This is the biggest group,
   so it comes first.
2. **Fans browsing.** Family at the boundary, or players on an iPhone, which the Android app doesn't cover,
   looking up live matches, results, a team or a player's stats.
3. **Spectators on a big screen.** A live scorecard or an auction on a projector or TV at the ground.

## Decisions

| Area | Choice | Why |
|---|---|---|
| Scope | View only, no accounts | Everything people do stays in the app; the web is for watching and looking things up |
| Location | `web/` at the repo root, its own npm project | Next to `server/` and `proxy/`; Gradle doesn't build it |
| Build | Vite, React, TypeScript | Static files, fast dev server, no server rendering needed |
| Routing | React Router (library mode) | Every page has a URL, and the shared `/m/{id}` and `/t/{id}` links keep working |
| Server data | TanStack Query | Caching, retries and refetch on focus, the job the app's repositories do |
| Server API | Read-only `/public/...` routes that need no token and leave out account IDs | The signed-in API stays as it is; the public routes carry only what a viewer may see |
| Scorecards | `:core` compiled to JavaScript with Kotlin/JS and called through a small JSON facade (`WebCore`) | The same scoring engine as the phone and server, so a scorecard reads the same everywhere. No TypeScript port to drift out of step |
| API types | TypeScript types in `web/src/api/types.ts` that mirror the `core` DTOs | The wire format is JSON; the facade covers the computed parts |
| Styling | CSS Modules over Crease tokens as CSS custom properties | No styling library; tokens stay in one file like `:app:designsystem` |
| Design system | `web/src/designsystem/`: tokens, fonts, icons and components | Features never define their own colours, type or shared components, same rule as the app |
| Fonts | Barlow Condensed, Figtree, JetBrains Mono, self-hosted | Same files as the app; no Google Fonts request on a scorecard |
| Icons | SVG generated from the app's Material Symbols vector drawables (`npm run icons`) | The same icon set without downloading it twice; a script keeps them in step |
| Text | i18next. The app's `strings.xml` is copied to `src/i18n/app.generated.json` (`npm run strings`); web-only text is in `src/i18n/web.json` | Same words as the app and one translation for both. No literal UI text, same rule as the app |
| Tests | Vitest and Testing Library | |
| Hosting | Static files behind the existing Cloudflare proxy | One domain: shared `/m`, `/t` and `/join` links open the web app, API paths pass through to the server |

### Adapting to the web, not copying the phone

- **Below 720px** it looks like the app: bottom navigation and one column.
- **From 720px** navigation moves to a side rail and content centres at 600px, as on tablets.
- **From 1100px** pages that have two things to show use two columns: scorecard and over-by-over, tournament
  table and fixtures, a player's stats and their recent matches.
- Every link and control has hover and focus states.
- Pages say "Get the app" once, in a banner that can be dismissed, for anyone who wants to score, follow or
  join.

## Working on it

```
cd web
cp .env.example .env.local      # the API URL
npm install
npm run dev                     # builds :core with Gradle, then serves http://localhost:5173
```

Run the server with `WEB_ORIGINS=http://localhost:5173 ./gradlew :server:run` so the browser may call it.
`npm test` runs Vitest; `npm run build` typechecks and builds to `web/dist`. After changing the app's
`strings.xml` or drawables, run `npm run strings` or `npm run icons`; CI fails if they're out of step.

## Server changes the web app needs

All read only, with no sign-in. Each leaves out Firebase account IDs, claim and join codes, and anything else
only members or organisers see in the app.

| Change | Phase |
|---|---|
| ✅ CORS for the web app's origins (`WEB_ORIGINS`) | W0 |
| ✅ `GET /public/matches/{id}`, `GET /public/tournaments/{id}` | W1 |
| ✅ `/public/matches/{id}/live` WebSocket | W1 |
| The proxy sends `/m/*`, `/t/*`, `/join/*` and the web app's own paths to the web app instead of the server's HTML pages | W1 |
| Public match lists (live, recent, upcoming, by city), tournament lists, search | W2 |
| Public team and player pages, player stats | W2 |
| Public leaderboards, rankings, directory and "looking for" posts | W3 |
| Public auction feed for watching | W4 |

---

## Phase W0 · Foundation

**Goal:** an empty web app in Crease that can call the server and draw a scorecard.

- 🟡 `web/` project: Vite, React, TypeScript, React Router, TanStack Query, i18next, Vitest. No linter yet
- ✅ Crease tokens as CSS custom properties, light and dark from `prefers-color-scheme`
- 🟡 Fonts bundled; type scale as classes. Shipped as TTF for now; WOFF2 would halve them
- ✅ Icons generated from `app/designsystem` drawables
- ✅ First components: buttons, card, section header, list card and rows, avatar, tag, live badge, ball chip,
  over strip, score header, match card, empty state, error state, error banner, loading
- ✅ App shell: bottom bar under 720px, side rail above
- ✅ `:core` JS target and `WebCore` facade: `MatchDetails` JSON in, scorecard view JSON out
- ✅ API client with base URL from `VITE_API_URL` and `ApiError` messages
- ✅ Server: CORS
- ✅ CI builds and tests `web/`

**Exit:** `npm run build` passes, the component catalogue page renders in both themes, and a test computes a
scorecard in the browser with `:core`.

## Phase W1 · Shared links

**Goal:** a shared match or tournament link opens a live, good-looking page in any browser.

- ✅ Server: public match and tournament JSON, public live feed
- ⬜ Match page `/m/:id`: score header, Live / Scorecard / Overs tabs, updates every ball; two columns on desktop
- ⬜ Tournament page `/t/:id`: champion, points tables, fixtures, key stats
- ⬜ Join page `/join/:code`: how to join the team in the app
- ⬜ Not found and offline states
- ⬜ "Get the app" banner
- ⬜ Hosting and proxy routing

**Exit:** a match link sent on WhatsApp opens the live scorecard on an iPhone, and it updates ball by ball.

## Phase W2 · Browse

**Goal:** someone with no link can find a match, a team or a player and follow along.

- ⬜ Home: live now, then recent results and upcoming fixtures, filtered by city
- ⬜ Tournaments: ongoing, upcoming and finished, by city
- ⬜ Search across matches, teams, players and tournaments
- ⬜ Team page: squad, recent results, record against other teams
- ⬜ Player page: career, season and format stats, form, recent matches

**Exit:** a parent searches for their child's team, opens it, and follows its live match.

## Phase W3 · Stats and community

**Goal:** the numbers and listings people like to look up.

- ⬜ Leaderboards by city, tournament and season
- ⬜ Team and player rankings by city
- ⬜ Compare two players
- ⬜ Directory of grounds, umpires and scorers, and "looking for" posts, to read

**Exit:** the city's top run-scorers and the team rankings open from a link with no app.

## Phase W4 · Big screen

**Goal:** the things a browser does better than a phone.

- ⬜ Big-screen scorecard for a projector or TV, readable from across the ground
- ⬜ Auction hall to watch, with the player up, bids and purses
- ⬜ Stream overlay moved from the server's page into the web app
- ⬜ Themes from Phase 8
- ⬜ Installable (web app manifest)

**Exit:** a live match and an auction both run on a projector from the web app.
