-- Players asking to join a team they found by name, until an admin accepts or declines.
CREATE TABLE team_join_requests (
    team_id      VARCHAR(36)  NOT NULL,
    player_id    VARCHAR(128) NOT NULL,
    requested_at BIGINT       NOT NULL,
    CONSTRAINT pk_team_join_requests PRIMARY KEY (team_id, player_id),
    CONSTRAINT fk_team_join_requests_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_join_requests_player FOREIGN KEY (player_id) REFERENCES players (id)
);
CREATE INDEX team_join_requests_player ON team_join_requests (player_id);

-- Team news in the inbox (join requests and acceptances) points at a team instead of a match.
ALTER TABLE notifications ADD COLUMN team_id VARCHAR(36);
