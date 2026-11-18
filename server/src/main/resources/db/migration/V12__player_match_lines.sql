-- The stats projection: what each linked player did in each completed match. Rewritten whenever a match
-- completes (or is undone back into play); career stats and leaderboards add these up.
CREATE TABLE player_match_lines (
    match_id          VARCHAR(36)  NOT NULL,
    profile_id        VARCHAR(128) NOT NULL,
    played_at         BIGINT       NOT NULL,
    tournament_id     VARCHAR(36),
    overs_per_innings INT          NOT NULL,
    line              TEXT         NOT NULL,
    CONSTRAINT pk_player_match_lines PRIMARY KEY (match_id, profile_id)
);
CREATE INDEX player_match_lines_profile ON player_match_lines (profile_id, played_at);
CREATE INDEX player_match_lines_tournament ON player_match_lines (tournament_id);
