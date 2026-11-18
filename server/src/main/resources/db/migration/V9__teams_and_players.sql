-- Players across matches: every user has one (id = their UID); team admins add placeholders for people
-- without the app, who claim them later. Match players link here so stats follow the person.
CREATE TABLE players (
    id         VARCHAR(128) NOT NULL,
    user_uid   VARCHAR(128),
    name       VARCHAR(100) NOT NULL,
    phone      VARCHAR(20)  NOT NULL DEFAULT '',
    claim_code VARCHAR(8),
    created_by VARCHAR(128) NOT NULL,
    created_at BIGINT       NOT NULL,
    CONSTRAINT pk_players PRIMARY KEY (id),
    CONSTRAINT uq_players_user UNIQUE (user_uid),
    CONSTRAINT uq_players_claim_code UNIQUE (claim_code)
);
CREATE INDEX players_phone ON players (phone);

INSERT INTO players (id, user_uid, name, phone, claim_code, created_by, created_at)
SELECT uid, uid, name, phone, NULL, uid, created_at FROM users;

CREATE TABLE teams (
    id         VARCHAR(36)  NOT NULL,
    name       VARCHAR(60)  NOT NULL,
    city       VARCHAR(100) NOT NULL,
    join_code  VARCHAR(8)   NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at BIGINT       NOT NULL,
    CONSTRAINT pk_teams PRIMARY KEY (id),
    CONSTRAINT uq_teams_join_code UNIQUE (join_code)
);

CREATE TABLE team_members (
    team_id   VARCHAR(36)  NOT NULL,
    player_id VARCHAR(128) NOT NULL,
    role      VARCHAR(10)  NOT NULL,
    joined_at BIGINT       NOT NULL,
    CONSTRAINT pk_team_members PRIMARY KEY (team_id, player_id),
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_player FOREIGN KEY (player_id) REFERENCES players (id)
);
CREATE INDEX team_members_player ON team_members (player_id);

-- A match side can come from a saved team, with its captain and keeper.
ALTER TABLE matches ADD COLUMN team_a_id VARCHAR(36);
ALTER TABLE matches ADD COLUMN team_b_id VARCHAR(36);
ALTER TABLE match_players ADD COLUMN profile_id VARCHAR(128);
ALTER TABLE match_players ADD COLUMN is_captain BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE match_players ADD COLUMN is_keeper BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX match_players_profile ON match_players (profile_id);

-- Support messages from the app's settings.
CREATE TABLE support_requests (
    id          VARCHAR(36)   NOT NULL,
    uid         VARCHAR(128)  NOT NULL,
    category    VARCHAR(10)   NOT NULL,
    message     VARCHAR(4000) NOT NULL,
    app_version VARCHAR(40)   NOT NULL,
    created_at  BIGINT        NOT NULL,
    CONSTRAINT pk_support_requests PRIMARY KEY (id)
);

-- Deleted accounts keep their row so old scorecards still resolve, with the personal data cleared.
ALTER TABLE users ADD COLUMN deleted_at BIGINT;
