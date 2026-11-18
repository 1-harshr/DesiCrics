-- Profile photos and team logos, small and already resized by the app. Kept in the database until the
-- object storage choice is made with hosting; served publicly from /images/{id}.
CREATE TABLE images (
    id           VARCHAR(36)  NOT NULL,
    owner_uid    VARCHAR(128) NOT NULL,
    content_type VARCHAR(30)  NOT NULL,
    bytes        BYTEA        NOT NULL,
    created_at   BIGINT       NOT NULL,
    CONSTRAINT pk_images PRIMARY KEY (id)
);

ALTER TABLE teams ADD COLUMN logo_url VARCHAR(200);
