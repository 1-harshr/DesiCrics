-- Initial schema: profiles, quick matches, their players and ball-by-ball deliveries.
-- Kept to SQL that runs on both Postgres and H2 (PostgreSQL mode), which the tests use.
-- Enum columns store the Kotlin enum name; see the matching tables in db/Database.kt.

CREATE TABLE users (
    uid           VARCHAR(128)  NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    phone         VARCHAR(20)   NOT NULL,
    email         VARCHAR(254),
    photo_url     VARCHAR(1024),
    city          VARCHAR(100)  NOT NULL,
    role          VARCHAR(20)   NOT NULL,
    batting_style VARCHAR(20)   NOT NULL,
    bowling_style VARCHAR(30)   NOT NULL,
    created_at    BIGINT        NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (uid)
);

CREATE TABLE matches (
    id                VARCHAR(36)  NOT NULL,
    created_by        VARCHAR(128) NOT NULL,
    created_at        BIGINT       NOT NULL,
    team_a_name       VARCHAR(60)  NOT NULL,
    team_b_name       VARCHAR(60)  NOT NULL,
    overs_per_innings INT          NOT NULL,
    toss_winner       VARCHAR(1)   NOT NULL,
    toss_decision     VARCHAR(4)   NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    score_summary     VARCHAR(200) NOT NULL,
    result            VARCHAR(200),
    CONSTRAINT pk_matches PRIMARY KEY (id),
    CONSTRAINT fk_matches_created_by FOREIGN KEY (created_by) REFERENCES users (uid)
);

CREATE INDEX matches_created_at ON matches (created_at);

CREATE TABLE match_players (
    id       VARCHAR(36) NOT NULL,
    match_id VARCHAR(36) NOT NULL,
    side     VARCHAR(1)  NOT NULL,
    position INT         NOT NULL,
    name     VARCHAR(60) NOT NULL,
    CONSTRAINT pk_match_players PRIMARY KEY (id),
    CONSTRAINT fk_match_players_match FOREIGN KEY (match_id) REFERENCES matches (id) ON DELETE CASCADE
);

CREATE INDEX match_players_match_id ON match_players (match_id);

CREATE TABLE deliveries (
    match_id       VARCHAR(36) NOT NULL,
    seq            INT         NOT NULL,
    innings        INT         NOT NULL,
    striker_id     VARCHAR(36) NOT NULL,
    non_striker_id VARCHAR(36) NOT NULL,
    bowler_id      VARCHAR(36) NOT NULL,
    runs_off_bat   INT         NOT NULL,
    extra          VARCHAR(10),
    extra_runs     INT         NOT NULL,
    wicket_kind    VARCHAR(15),
    player_out_id  VARCHAR(36),
    created_at     BIGINT      NOT NULL,
    CONSTRAINT pk_deliveries PRIMARY KEY (match_id, seq),
    CONSTRAINT fk_deliveries_match FOREIGN KEY (match_id) REFERENCES matches (id) ON DELETE CASCADE,
    CONSTRAINT fk_deliveries_striker FOREIGN KEY (striker_id) REFERENCES match_players (id),
    CONSTRAINT fk_deliveries_non_striker FOREIGN KEY (non_striker_id) REFERENCES match_players (id),
    CONSTRAINT fk_deliveries_bowler FOREIGN KEY (bowler_id) REFERENCES match_players (id),
    CONSTRAINT fk_deliveries_player_out FOREIGN KEY (player_out_id) REFERENCES match_players (id)
);
