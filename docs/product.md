# Product

## Problem

Amateur cricket in India runs on WhatsApp groups and paper scorebooks. Scores get lost, stats never add up across seasons, and nobody outside the ground can follow the game.

DesiCrics gives these groups one place to score matches, follow them live, keep career stats and run tournaments. It is built on three choices:

1. A native Android app built with Jetpack Compose.
2. A server that owns the data and the scoring rules.
3. A design system made for one-thumb scoring in sunlight.

## Goals

- A scorer can record a full T20 ball by ball with one hand, without errors or lag.
- Anyone with a link can follow a match live.
- Career stats build up automatically from every scored ball.
- Organisers run a tournament end to end in the app.

## Non-goals (for now)

- iOS app. The KMP setup keeps the door open, but we don't build it.
- Video live streaming (a score overlay feed is in Phase 6).
- Betting, fantasy or payments.
- Professional DLS calculations. Targets are revised manually.

## Principles

- **The server is the source of truth.** Clients send events; the server derives the score.
- **Scoring needs a connection, and copes with a bad one.** Each ball is saved on the server before the score moves on; if a save fails, the scorer just taps again. There is no offline scoring.
- **One rules engine.** It lives in `core` and is shared by the app and the server.
- **Calm by default.** Scoring and following stay fast and free of upsells. Network features live one tab away.

## Users

| User | Needs |
|---|---|
| Scorer / captain | Creates the match, picks squads, records every ball. Usually also playing. Needs speed, undo and no surprises. |
| Player | Wants their runs, wickets and form in one place, and a profile worth sharing. |
| Organiser | Runs a weekend league or corporate cup: teams, fixtures, points table, top performers. |
| Follower | Family, teammates or rivals not at the ground. Opens a shared link and watches the score move. |

## Success metrics

| Metric | Target | Phase |
|---|---|---|
| Matches scored to completion | ≥ 80% of matches started | 1 |
| Median tap-to-confirm per ball | ≤ 1.5 s | 1 |
| Balls lost when a save fails | 0 (the ball stays ready to resend) | 1 |
| Followers per live match | ≥ 3 | 3 |
| Week-4 retention (players) | ≥ 35% | 5 |
| Crash-free sessions | ≥ 99.5% | all |

## Risks

- **Scoring edge cases.** Cricket laws have long tails (run-out off a no-ball, overthrows, penalty runs). Mitigation: a table-driven test suite in `core` before any scoring UI.
- **Duplicate players.** Placeholder players fragment stats. Mitigation: a claim-and-merge flow in Phase 2.
- **Weekend load spikes.** Live matches cluster on weekend mornings. Mitigation: a stateless Ktor service, load-tested before Phase 3.

## Open questions

- Keep the KMP structure or move to plain Android + JVM Gradle modules? Recommendation: keep KMP for the shared `core` and drop the iOS target.
- Hosting: Cloud Run + Cloud SQL, or Fly.io + Neon? Needs deciding by Phase 0 Level 3 (staging deploy). See [ADR 0001](decisions/0001-backend-stack.md).
- Pair cricket and box cricket in v1? They matter in gully cricket.
- Hindi UI strings from day one?
