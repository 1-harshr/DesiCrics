-- Rain revisions: the innings' new overs and, for the chase, its new target.
ALTER TABLE match_events ADD COLUMN overs INT;
ALTER TABLE match_events ADD COLUMN target INT;
