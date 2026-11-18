-- Where each scoring shot went, for matches that track a wagon wheel.
ALTER TABLE deliveries ADD COLUMN zone VARCHAR(12);

-- Who scores the match now; it starts as the creator and moves with a handover code.
ALTER TABLE matches ADD COLUMN scorer_uid VARCHAR(128);
UPDATE matches SET scorer_uid = created_by;
ALTER TABLE matches ALTER COLUMN scorer_uid SET NOT NULL;

-- Short-lived codes a scorer shows so someone else can take over.
CREATE TABLE scorer_handovers (
    code       VARCHAR(6)  NOT NULL,
    match_id   VARCHAR(36) NOT NULL,
    expires_at BIGINT      NOT NULL,
    CONSTRAINT pk_scorer_handovers PRIMARY KEY (code),
    CONSTRAINT fk_scorer_handovers_match FOREIGN KEY (match_id) REFERENCES matches (id) ON DELETE CASCADE
);
