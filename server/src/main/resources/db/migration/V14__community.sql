-- Phase 6: "looking for" posts and their responses, and a directory of grounds, umpires and scorers.
CREATE TABLE looking_for_posts (
    id         VARCHAR(36)  NOT NULL,
    author_uid VARCHAR(128) NOT NULL,
    kind       VARCHAR(10)  NOT NULL,
    city       VARCHAR(100) NOT NULL,
    for_date   BIGINT       NOT NULL,
    note       VARCHAR(500) NOT NULL,
    team_id    VARCHAR(36),
    is_open    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at BIGINT       NOT NULL,
    CONSTRAINT pk_looking_for_posts PRIMARY KEY (id)
);
CREATE INDEX looking_for_posts_city ON looking_for_posts (city, for_date);

CREATE TABLE looking_for_responses (
    post_id    VARCHAR(36)  NOT NULL,
    uid        VARCHAR(128) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    phone      VARCHAR(20)  NOT NULL,
    created_at BIGINT       NOT NULL,
    CONSTRAINT pk_looking_for_responses PRIMARY KEY (post_id, uid),
    CONSTRAINT fk_looking_for_responses_post FOREIGN KEY (post_id) REFERENCES looking_for_posts (id) ON DELETE CASCADE
);

CREATE TABLE directory_entries (
    id         VARCHAR(36)  NOT NULL,
    kind       VARCHAR(10)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    city       VARCHAR(100) NOT NULL,
    phone      VARCHAR(20)  NOT NULL,
    notes      VARCHAR(500) NOT NULL,
    added_by   VARCHAR(128) NOT NULL,
    created_at BIGINT       NOT NULL,
    CONSTRAINT pk_directory_entries PRIMARY KEY (id)
);
CREATE INDEX directory_entries_city ON directory_entries (city);
