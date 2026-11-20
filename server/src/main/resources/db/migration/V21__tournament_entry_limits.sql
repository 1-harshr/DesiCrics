-- How many teams and players (entering on their own) a tournament takes, and when entry closes.
-- Nulls mean no limit and no closing time. Entries past a limit wait on a list (status WAITLISTED).
ALTER TABLE tournaments ADD COLUMN max_teams INT;
ALTER TABLE tournaments ADD COLUMN max_players INT;
ALTER TABLE tournaments ADD COLUMN entry_closes_at BIGINT;
