# Features

Every feature area, what we build, and the phase it lands in. See [roadmap.md](roadmap.md) for levels and exit criteria.

| Area | What we build | Phase |
|---|---|---|
| Sign-in | Phone OTP and Google via Firebase Auth; the server verifies the ID token. No intro carousel: sign-in leads straight to a profile step. | 0 |
| Profile | Name, photo, gender, city, player role, batting style, bowling style. Onboarding asks only for name, role and styles. | 0 |
| Create match | Three-step wizard: teams → squads → rules. Defaults to T20 with a tennis ball. Rules cover match type, overs, ball type, pitch type, venue, start time, officials and power plays. | 1 |
| Toss | One sheet before the first ball: winner and decision. | 1 |
| Scoring | Thumb-zone run pad, contextual wicket sheet, server-validated undo, substitutes, revise target, end innings. Wagon wheel optional. | 1 |
| Offline scoring | Local event queue with a sync indicator. Deliveries are never lost. | 1 |
| Match detail | Scorecard, commentary, squads, overs, info. Updates live. | 1 / 3 |
| Teams | Create team, add members by search, phone number or QR code, team admins, invite links that work through WhatsApp. | 2 |
| Profile tabs | Matches / Teams / Stats on the player's profile. | 2 |
| Settings and support | Edit profile, support form with attachments, sign out, account deletion. | 2 |
| Today feed | My live match pinned first, then followed matches, then nearby. | 3 |
| Follow | Follow teams and players. Public read-only match links. | 3 |
| Notifications | FCM push for match start, followed players' milestones and results, plus an in-app inbox. | 3 |
| Share cards | Image cards for scorecards and milestones, sized for WhatsApp status. | 3 |
| Tournaments | Knockout and round robin first, then box league, double out, best of three and custom. Groups, knockouts, points table with NRR, key stats, organisers, registration form. | 4 |
| Player stats | Server-computed career, season and format splits, form chart, player comparison. | 5 |
| MVP points | One documented formula that ranks every player in a match, computed from deliveries. | 5 |
| Leaderboards | Scoped to city, tournament and friends. | 5 |
| Looking for | Post a need (opponent, player, ground, umpire, scorer) with a city and date; others respond. | 6 |
| Rankings | City-scoped team and player rankings, weighted by opposition strength. | 6 |
| Stream overlay | Score overlay feed for OBS / Larix livestreams. No video hosting. | 6 |

## Cricket vocabulary the app supports

- **Match types:** limited overs, test, the Hundred, pair cricket, box cricket. Pair and box come after Phase 1.
- **Ball types:** leather, tennis, other.
- **Pitch types:** rough, cement, turf, astroturf, matting.
- **Extras:** wide, no-ball, bye, leg bye, penalty run.
- **Wickets:** bowled, caught, caught behind, caught and bowled, LBW, stumped, run out, hit wicket, hit ball twice, handled ball, obstructing the field, timed out, retired, retired hurt.
- **Player status in a match:** yet to play, playing, played, injured, substitute, suspended, withdrawn.
- **Player roles:** opening, top-order, middle-order and lower-order batter, wicketkeeper, wicketkeeper-batter, bowler, all-rounder.
- **Bowling styles:** right-arm fast, right-arm medium, left-arm fast, left-arm medium, slow left-arm orthodox, slow left-arm chinaman, right-arm off break, right-arm leg break.
- **Match stage:** round, quarterfinal, semifinal, final.
- **Tournament types (minimum teams):** knockout (2), mini robin (3), box league (4), double out (4), best of three (2), custom (2).
