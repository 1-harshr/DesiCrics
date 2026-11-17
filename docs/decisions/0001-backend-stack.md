# 0001 · Backend stack: Ktor + Postgres + Firebase Auth

- **Status:** Accepted
- **Date:** 2026-11-17

## Context

We want scores that can't be corrupted by a buggy or offline client, and one set of scoring rules used everywhere.

Options considered:

1. **Firestore / Firebase-only**, with the app writing documents directly. Fast to start, but the rules end up in the client or in security rules, and stats queries across seasons are awkward.
2. **Supabase as the backend.** Postgres with row-level permissions and edge functions. Cricket rules don't fit row-level permissions, so they'd move into SQL or TypeScript and split from the Kotlin rules engine.
3. **Ktor server + Postgres, Firebase only for auth and messaging.**

## Decision

Option 3.

- Ktor is the only writer to the database and owns all scoring rules and permission checks, using `DeliveryRules` from `core`.
- Postgres through Exposed, with Flyway migrations and H2 for tests. The database is chosen by `DATABASE_URL`.
- Firebase is used for Auth (phone OTP, Google), FCM and Crashlytics only. No Firestore.
- No Supabase in any form.

## Consequences

- We build and run our own API, including the live feed (WebSockets). That is more work than Firestore listeners.
- The Kotlin rules engine is shared between the device and the server, so validation can't drift.
- Postgres makes stats, leaderboards and points tables plain SQL.
- Hosting is still open: Cloud Run + Cloud SQL, or Fly.io + Neon. It needs deciding before the Phase 0 staging deploy.
