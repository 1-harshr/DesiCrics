-- Each match's playing conditions as JSON (MatchRules). '{}' decodes to the defaults older matches used.
ALTER TABLE matches ADD COLUMN rules TEXT NOT NULL DEFAULT '{}';

-- A last batter batting alone has no non-striker.
ALTER TABLE deliveries ALTER COLUMN non_striker_id DROP NOT NULL;

-- The wide or no-ball penalty the ball was scored with, and whether it counted as one of the over's six.
ALTER TABLE deliveries ADD COLUMN extra_penalty INT NOT NULL DEFAULT 1;
ALTER TABLE deliveries ADD COLUMN counts_as_ball BOOLEAN NOT NULL DEFAULT FALSE;
