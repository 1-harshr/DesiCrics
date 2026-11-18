# 0002 · Player identity: one ID per person, placeholders that can be claimed

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

Phase 1 matches use names typed in at the ground. Phase 2 needs those names to become people, so stats (Phase 5) follow a player across matches and teams. But most of a gully side won't have the app on day one, and the person scoring can't wait for them to sign up.

## Decision

- Every player has a **profile ID**, stored in `players`. A user's profile ID is their Firebase UID, and the row is kept in sync with their profile.
- A team admin can add a **placeholder**: a `players` row with no user, a one-time **claim code**, and optionally a phone number.
- A match player (`match_players`) carries an optional `profile_id`. Guests typed in by name have none and never count towards anyone's stats.
- **Claiming** moves everything the placeholder did to the user's own profile ID in one transaction: team memberships (keeping the higher role if both were in a team) and match appearances. Then the placeholder is deleted. Stats stay a projection over `match_players.profile_id`, so they move with it for free.
- A claim needs the placeholder's code, or a **verified** phone number (the `phone_number` claim of a phone-OTP Firebase token) that matches the one the admin entered. The phone a user types into their profile is never trusted for this.
- **Deleting an account** clears the user's personal data but keeps both rows, so old scorecards still resolve. The player reads "Former player" and can't be claimed. Teams the user ran alone pass to the next member who has the app.

## Consequences

- Searching for players only finds users, never placeholders, so a placeholder isn't added to a second team by mistake. Whoever made it shares the claim code instead.
- Match rows keep the name as it was on the day, so a scorecard doesn't change when someone renames themselves.
- Merging two *users* (one person with two accounts) isn't supported. It would use the same move as claiming, if it's ever needed.
