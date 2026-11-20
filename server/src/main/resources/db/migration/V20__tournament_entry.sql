-- Who can enter a tournament, separately for teams and players: ORGANISERS, APPROVAL or OPEN.
-- Registration that was open becomes APPROVAL, which is how it worked; closed becomes ORGANISERS.
ALTER TABLE tournaments ADD COLUMN team_entry VARCHAR(10) NOT NULL DEFAULT 'ORGANISERS';
ALTER TABLE tournaments ADD COLUMN player_entry VARCHAR(10) NOT NULL DEFAULT 'ORGANISERS';
UPDATE tournaments SET team_entry = 'APPROVAL' WHERE registration_open = TRUE;
ALTER TABLE tournaments DROP COLUMN registration_open;

-- Players in a tournament: the pool (team_id null) and every team's squad. The key keeps a player to one squad.
CREATE TABLE tournament_players (
    tournament_id VARCHAR(36)  NOT NULL,
    player_id     VARCHAR(128) NOT NULL,
    team_id       VARCHAR(36),
    status        VARCHAR(10)  NOT NULL,
    -- Entered on their own, so they go back to the pool if their team leaves.
    from_pool     BOOLEAN      NOT NULL,
    added_by      VARCHAR(128) NOT NULL,
    added_at      BIGINT       NOT NULL,
    CONSTRAINT pk_tournament_players PRIMARY KEY (tournament_id, player_id),
    CONSTRAINT fk_tournament_players_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id) ON DELETE CASCADE,
    CONSTRAINT fk_tournament_players_player FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT fk_tournament_players_team FOREIGN KEY (team_id) REFERENCES teams (id)
);
CREATE INDEX tournament_players_player ON tournament_players (player_id);

-- Tournament news in the inbox (entries and squad places) points at a tournament.
ALTER TABLE notifications ADD COLUMN tournament_id VARCHAR(36);
