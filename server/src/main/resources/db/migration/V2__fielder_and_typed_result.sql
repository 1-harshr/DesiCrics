-- Name the catcher, run-out fielder or keeper on a dismissal.
ALTER TABLE deliveries ADD COLUMN fielder_id VARCHAR(36);
ALTER TABLE deliveries ADD CONSTRAINT fk_deliveries_fielder FOREIGN KEY (fielder_id) REFERENCES match_players (id);

-- Store the result as data so the app can word it in the user's language.
-- A completed match with no winner is a tie.
ALTER TABLE matches DROP COLUMN result;
ALTER TABLE matches ADD COLUMN result_winner VARCHAR(1);
ALTER TABLE matches ADD COLUMN result_margin INT;
ALTER TABLE matches ADD COLUMN result_margin_unit VARCHAR(10);
