-- Squad changes organisers make after a tournament starts, shown with each team's squad.
CREATE TABLE tournament_squad_changes (
    id            VARCHAR(36)  NOT NULL,
    tournament_id VARCHAR(36)  NOT NULL,
    team_id       VARCHAR(36)  NOT NULL,
    player_id     VARCHAR(128) NOT NULL,
    added         BOOLEAN      NOT NULL,
    changed_by    VARCHAR(128) NOT NULL,
    changed_at    BIGINT       NOT NULL,
    CONSTRAINT pk_tournament_squad_changes PRIMARY KEY (id),
    CONSTRAINT fk_tournament_squad_changes_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id) ON DELETE CASCADE
);
CREATE INDEX tournament_squad_changes_tournament ON tournament_squad_changes (tournament_id, changed_at);
