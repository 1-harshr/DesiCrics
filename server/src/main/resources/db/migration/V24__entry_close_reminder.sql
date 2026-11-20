-- Organisers are reminded once, a day before entry closes, about entries still waiting for them.
ALTER TABLE tournaments ADD COLUMN entry_reminded BOOLEAN NOT NULL DEFAULT FALSE;
