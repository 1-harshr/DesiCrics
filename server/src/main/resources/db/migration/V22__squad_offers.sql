-- Who puts players from the pool into squads: ORGANISERS, or TEAMS (team admins ask, players accept).
ALTER TABLE tournaments ADD COLUMN pool_mode VARCHAR(10) NOT NULL DEFAULT 'ORGANISERS';

-- A team asking a pool player to join its squad. The player takes one; the rest go when they're placed.
CREATE TABLE tournament_squad_offers (
    tournament_id VARCHAR(36)  NOT NULL,
    player_id     VARCHAR(128) NOT NULL,
    team_id       VARCHAR(36)  NOT NULL,
    offered_by    VARCHAR(128) NOT NULL,
    offered_at    BIGINT       NOT NULL,
    CONSTRAINT pk_tournament_squad_offers PRIMARY KEY (tournament_id, player_id, team_id),
    CONSTRAINT fk_tournament_squad_offers_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id) ON DELETE CASCADE,
    CONSTRAINT fk_tournament_squad_offers_team FOREIGN KEY (team_id) REFERENCES teams (id)
);
CREATE INDEX tournament_squad_offers_player ON tournament_squad_offers (player_id);
