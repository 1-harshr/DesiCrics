-- Matches that stop early: abandoned (with a reason), conceded, or a walkover.
ALTER TABLE matches ADD COLUMN result_ending VARCHAR(12);
ALTER TABLE matches ADD COLUMN result_reason VARCHAR(12);

ALTER TABLE match_events ADD COLUMN ending VARCHAR(12);
ALTER TABLE match_events ADD COLUMN winner VARCHAR(1);
ALTER TABLE match_events ADD COLUMN reason VARCHAR(12);
ALTER TABLE match_events ADD COLUMN before_start BOOLEAN DEFAULT FALSE NOT NULL;
