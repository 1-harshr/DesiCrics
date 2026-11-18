-- Room for the longer dismissal names (OBSTRUCTING_FIELD).
ALTER TABLE deliveries ALTER COLUMN wicket_kind SET DATA TYPE VARCHAR(20);

-- Penalty events: how many runs, and whether they went to the batting side or the fielding side.
ALTER TABLE match_events ADD COLUMN runs INT NOT NULL DEFAULT 0;
ALTER TABLE match_events ADD COLUMN to_batting_side BOOLEAN NOT NULL DEFAULT FALSE;
