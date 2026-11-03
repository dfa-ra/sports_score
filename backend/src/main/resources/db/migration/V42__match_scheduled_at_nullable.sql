-- A later-round playoff match is created when the previous pair finishes.
-- Its kickoff stays empty until an admin sets one. Existing rows are not rewritten.
ALTER TABLE matches ALTER COLUMN scheduled_at DROP NOT NULL;
