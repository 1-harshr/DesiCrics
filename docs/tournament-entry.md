# Tournament entry · Teams and players joining a tournament

**Goal:** a tournament is a place people join, not a list the organiser types in. Teams and individual players can enter it, the organiser decides how open each door is, and everyone hears what happened to their entry.

Choices marked **(default)** are what we build unless we decide otherwise.

## What works today

- **Organisers add teams.** On the Teams tab, "Add a team" searches every team; the team is in straight away (`EntryStatus.CONFIRMED`).
- **Team admins register, if the organiser allowed it.** "Let team admins register" (`registrationOpen`) is a switch on the new tournament screen, and can't be changed afterwards. With it on, a team's admin sees "Register your team", and the entry waits as `PENDING` until an organiser confirms or rejects it. Pending entries are dropped when the tournament starts.
- **Players only come in with their team.** Everyone in a team that's in the tournament can play for it. There's no tournament squad, so a player could turn out for two teams in the same tournament.
- **Auction pools are separate.** Players can ask to join an auction's pool from the tournament page (`auction_players.requested`), but that's the only way a single player can enter, and only when there's an auction.

## What's missing

1. **No individual players.** A player without a team, or whose team isn't entering, has no way in.
2. **One fixed setting for entry.** Registration is on or off at creation, for teams only.
3. **Nobody can find the tournament.** The Tournaments tab lists only your own, and the link at `/t/{id}` says "Follow", not "Join".
4. **Nobody is told anything.** No notification when someone registers, or when their entry is confirmed or rejected.
5. **No invites.** The organiser can add a team outright, but can't ask a team or player to accept.
6. **No tournament squads.** Who plays for which team in this tournament isn't recorded or limited.

---

## The model

A tournament has two kinds of entry, **teams** and **players**, and each has its own **entry setting**:

| Setting | Who can enter | What happens |
|---|---|---|
| **Organisers only** | Organisers add or invite | Added entries are in; invited ones are in once accepted |
| **On approval** | Anyone, by asking | The entry waits until an organiser confirms or rejects it **(default for teams and players)** |
| **Open** | Anyone | In straight away; organisers can still remove an entry until the tournament starts |

- Organisers can change either setting at any time until the tournament starts, and set an optional **closing date** after which nobody can ask.
- Optional **limits**: the most teams, and the most players in the pool. Once a limit is reached, new requests go on a **waiting list** in the order they came in.
- Teams enter through one of their admins. Players enter themselves; a placeholder (a player not on the app) can only be added by an organiser.

### Entry states

`CONFIRMED`, `PENDING` (asked, waiting for an organiser), `INVITED` (asked by an organiser, waiting to accept) and `WAITLISTED`. Rejected and withdrawn entries are deleted, as rejected ones were before. A team or player can withdraw their own entry until the tournament starts.

### Tournament squads

Each team in a tournament has a **squad**: the players who can play for it in this tournament.

- When a team is confirmed, its squad starts as the team's current members **(default)**. The team's admins can then trim it or add to it.
- A player is in **one squad per tournament**. Adding someone who's already in another squad is refused, with the other team's name.
- Optional limits: the fewest and most players per squad.
- Squads lock when the tournament starts **(default)**. Organisers can still make changes after that (an injury replacement, say), and each change is shown on the team's tournament page.
- When picking a playing XI for a fixture, only squad players are offered.

### What happens to individual players

Players who enter on their own go into the tournament's **player pool**. A tournament picks how the pool is used:

| Pool mode | How pool players reach a team |
|---|---|
| **Organiser places them** | An organiser assigns each pool player to a team's squad **(default)** |
| **Teams pick** | Team admins see the pool as free agents and ask a player to join their squad; the player accepts |
| **Auction** | Pool players go up in the tournament's auction: those waiting when it's set up, and anyone confirmed into the pool after; sold and retained players join the buying team's squad. The auction keeps its own requests for tournaments that don't take players |
| **Organiser makes the teams** | For pick-up tournaments with no saved teams: the organiser creates the teams from the pool, or the app splits it into balanced sides by role and stats |

A pool player who joins a squad leaves the pool. Pool players who are never placed still count as entered, so they get the tournament's notifications.

---

## Finding and joining

- **Invite links.** The share sheet gets "Invite teams" and "Invite players" whenever that kind of entry isn't organisers-only. The text says what the link does ("Enter your team for {tournament}…", "Join {tournament} as a player…"). Both use `/t/{id}`, with a hint for which button to show first.
- **On the tournament page**, anyone who can enter sees "Enter your team" or "Join as a player" near the top, with the entry setting, closing date and spots left. Someone who doesn't run a team sees "Join as a player" and a way to create a team.
- **Discovery.** The Tournaments tab gets an "Open to join" section: tournaments that haven't started and take teams or players, filtered by the city on your profile, nearest start first. `GET /tournaments/open?city=`.
- **Your entries.** A player's or team admin's pending, invited and waitlisted entries show on the Tournaments tab, so they can accept, withdraw or check.
- The web page at `/t/{id}` shows that entry is open, and how to get the app.

## Notifications

Each goes to the inbox and, where a device is registered, out as a push, the same way team join requests do (`Notifier.teamNotice`).

| When | Who's told |
|---|---|
| A team or player asks to enter | Every organiser |
| An entry is confirmed, rejected, or moved off the waiting list | The team's admins, or the player |
| An organiser invites a team or player | The team's admins, or the player |
| A pool player is placed in a squad, or a team asks them to join | The player |
| A squad changes after it locks | The team's admins |
| Entry closes in a day, with requests still waiting | Every organiser |

Organisers also see a count of waiting entries on the Teams and Players tabs.

## Screens

- **New tournament**: "Who can enter" with the setting for teams and for players, the pool mode, and optional limits and closing date. Advanced options stay collapsed.
- **Tournament page**: the Teams tab, a new **Players** tab (the pool, and every squad), and an **Entries** view for organisers with waiting, invited and waitlisted entries to confirm or reject in one place.
- **Tournament settings** (organisers): the same entry options, editable until the start.
- **Team's tournament page**: the squad, with add and remove for its admins.

## Server and data

- Replace `registration_open` with `team_entry` and `player_entry` (each `ORGANISERS`, `APPROVAL` or `OPEN`), plus `entry_closes_at`, `max_teams`, `max_pool`, `squad_min`, `squad_max` and `pool_mode`. A migration maps `registration_open = true` to `APPROVAL` and `false` to `ORGANISERS`.
- `tournament_teams.status` gains `INVITED`, `WAITLISTED` and `WITHDRAWN`.
- New `tournament_players` (`tournament_id`, `player_id`, `team_id` null while in the pool, `status`, `added_by`, `added_at`), primary key (`tournament_id`, `player_id`): this one table holds both the pool and every squad, and the key keeps a player to one squad.
- Auctions read their pool from `tournament_players`; `auction_players` keeps only auction data (base price, category, retained).
- Routes under `/tournaments/{id}`: `PUT /entry` (settings), `POST /players` (join or add), `PUT /players/{playerId}` (confirm, reject, place in a squad), `DELETE /players/{playerId}`, `POST /teams/{teamId}/accept` and `POST /players/{playerId}/accept` for invites, and `GET /tournaments/open`.
- Fixture team picking reads the squad when the match is a tournament fixture.

## Out of scope

- Entry fees and payments.
- Eligibility rules beyond limits (age groups, city only, rating bands).
- Transfers between squads during a tournament, other than organiser changes.

## Levels

- **Level 1 · Playable:** entry settings for teams and players (organisers only, on approval, open), editable until the start; the player pool with organiser placement; tournament squads with one squad per player; invite links and "Join as a player"; notifications.
- **Level 2 · Complete:** invites with accept; withdraw; limits, waiting list and closing date; "Open to join" discovery; teams picking free agents; the auction pool moved onto `tournament_players`.
- **Level 3 · Delight:** organiser-made teams and balanced auto-split for pick-up tournaments; squad changes after the lock shown on team pages; the closing-soon reminder.

**Exit:** an organiser creates a tournament with teams on approval and players open, and shares both invite links in a WhatsApp group. Two team admins enter their teams and a player with no team joins the pool; the organiser confirms the teams and places the player in one of the squads. Everyone is notified at each step, the squads lock at the start, and the first fixture offers only squad players.
