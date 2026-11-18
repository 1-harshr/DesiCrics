-- Each innings' runs, wickets and balls as JSON, so match lists can show a score per team.
ALTER TABLE matches ADD COLUMN innings_scores TEXT NOT NULL DEFAULT '[]';
