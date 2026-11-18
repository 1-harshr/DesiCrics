-- Between-ball events (retirements, swapped ends, bowler changes). They share each match's sequence
-- numbers with deliveries, so the scorecard replays both in order.
CREATE TABLE match_events (
    match_id   VARCHAR(36) NOT NULL,
    seq        INT         NOT NULL,
    innings    INT         NOT NULL,
    type       VARCHAR(20) NOT NULL,
    player_id  VARCHAR(36),
    is_out     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at BIGINT      NOT NULL,
    CONSTRAINT pk_match_events PRIMARY KEY (match_id, seq),
    CONSTRAINT fk_match_events_match FOREIGN KEY (match_id) REFERENCES matches (id) ON DELETE CASCADE,
    CONSTRAINT fk_match_events_player FOREIGN KEY (player_id) REFERENCES match_players (id)
);
