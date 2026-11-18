-- Tournaments: their organisers, the saved teams entered, and the fixtures (matches) they generate.
CREATE TABLE tournaments (
    id                VARCHAR(36)  NOT NULL,
    name              VARCHAR(80)  NOT NULL,
    city              VARCHAR(100) NOT NULL,
    format            VARCHAR(20)  NOT NULL,
    overs_per_innings INT          NOT NULL,
    rules             TEXT         NOT NULL,
    group_count       INT          NOT NULL,
    advance           INT          NOT NULL,
    starts_at         BIGINT,
    registration_open BOOLEAN      NOT NULL,
    started           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by        VARCHAR(128) NOT NULL,
    created_at        BIGINT       NOT NULL,
    CONSTRAINT pk_tournaments PRIMARY KEY (id)
);

CREATE TABLE tournament_organisers (
    tournament_id VARCHAR(36)  NOT NULL,
    uid           VARCHAR(128) NOT NULL,
    role          VARCHAR(15)  NOT NULL,
    CONSTRAINT pk_tournament_organisers PRIMARY KEY (tournament_id, uid),
    CONSTRAINT fk_tournament_organisers FOREIGN KEY (tournament_id) REFERENCES tournaments (id) ON DELETE CASCADE
);

CREATE TABLE tournament_teams (
    tournament_id VARCHAR(36) NOT NULL,
    team_id       VARCHAR(36) NOT NULL,
    seed          INT         NOT NULL,
    group_no      INT,
    status        VARCHAR(10) NOT NULL,
    CONSTRAINT pk_tournament_teams PRIMARY KEY (tournament_id, team_id),
    CONSTRAINT fk_tournament_teams_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id) ON DELETE CASCADE,
    CONSTRAINT fk_tournament_teams_team FOREIGN KEY (team_id) REFERENCES teams (id)
);
CREATE INDEX tournament_teams_team ON tournament_teams (team_id);

-- Where a match sits in a tournament, when it's a fixture.
ALTER TABLE matches ADD COLUMN tournament_id VARCHAR(36);
ALTER TABLE matches ADD COLUMN stage VARCHAR(15);
ALTER TABLE matches ADD COLUMN round_no INT;
ALTER TABLE matches ADD COLUMN fixture_no INT;
ALTER TABLE matches ADD COLUMN group_no INT;
CREATE INDEX matches_tournament ON matches (tournament_id);
