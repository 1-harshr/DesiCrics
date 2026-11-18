# 0003 · Tournament fixtures: ordinary matches, made a round at a time

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

Phase 4 needs seven formats (knockout, round robin, groups plus knockouts, box league, double out, best of three, custom), with byes, playoffs and points tables. Fixtures also have to work with everything already built for matches: live scores, notifications, public links and stats.

## Decision

- **A fixture is a `Match`** with status `SCHEDULED`, saved teams on both sides, empty rosters, and a `FixtureInfo` (tournament, stage, round, number, group). An organiser starts it by picking both squads and the toss. From then on it's an ordinary match, scored by whoever started it.
- **`TournamentEngine` in `core` is pure.** `firstRound` runs when the organiser starts the tournament. `nextRound` runs every time a fixture finishes, and returns the next round once the current one is complete. `pointsTable` and `champion` are worked out from the fixtures. Nothing about the bracket is stored separately, so there's nothing to drift out of sync.
- **Rounds are made lazily** rather than as a full bracket up front, which keeps every format to one rule:
  - **Knockout:** byes go to the top seeds, and the stage is named by how many teams are still in.
  - **League formats:** the full league comes first (circle method), then the top `advance` from each group go into knockouts. Group winners meet runners-up.
  - **Double out:** each round pairs teams with the same number of losses, and a team is out after two.
  - **Best of three:** keep playing until one team has two wins.
- **The points table:** 2 points for a win, 1 for a tie. Ties on points are split by NRR, then by wins. NRR counts a side that's bowled out as having faced its full overs, so innings totals are enough and no deliveries need loading.
- **A tied knockout match sends the higher seed through**, because there's no super over yet.

## Consequences

- A second server instance needs the per-tournament lock (two fixtures finishing together) moved into the database.
- If the last ball of a finished fixture is undone after the next round already exists, that round isn't regenerated. An organiser can still score the replay. Blocking undo in that case is a possible follow-up.
