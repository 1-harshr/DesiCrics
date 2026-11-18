-- What each user follows: saved teams and players.
CREATE TABLE follows (
    uid         VARCHAR(128) NOT NULL,
    target_type VARCHAR(10)  NOT NULL,
    target_id   VARCHAR(128) NOT NULL,
    created_at  BIGINT       NOT NULL,
    CONSTRAINT pk_follows PRIMARY KEY (uid, target_type, target_id)
);
CREATE INDEX follows_target ON follows (target_type, target_id);

-- The in-app inbox. Facts, not sentences, so the app words them in the user's language.
CREATE TABLE notifications (
    id          VARCHAR(36)  NOT NULL,
    uid         VARCHAR(128) NOT NULL,
    kind        VARCHAR(20)  NOT NULL,
    match_id    VARCHAR(36)  NOT NULL,
    match_title VARCHAR(130) NOT NULL,
    player_name VARCHAR(100),
    amount      INT,
    detail      VARCHAR(200),
    created_at  BIGINT       NOT NULL,
    read_at     BIGINT,
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);
CREATE INDEX notifications_uid ON notifications (uid, created_at);

-- Where to send push messages: one row per device.
CREATE TABLE push_tokens (
    token      VARCHAR(512) NOT NULL,
    uid        VARCHAR(128) NOT NULL,
    platform   VARCHAR(10)  NOT NULL,
    updated_at BIGINT       NOT NULL,
    CONSTRAINT pk_push_tokens PRIMARY KEY (token)
);
CREATE INDEX push_tokens_uid ON push_tokens (uid);

-- Fixtures that haven't started yet, e.g. from a tournament, and when they're due.
ALTER TABLE matches ADD COLUMN scheduled_at BIGINT;
