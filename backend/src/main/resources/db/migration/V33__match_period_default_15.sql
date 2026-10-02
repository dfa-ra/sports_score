-- New matches that omit a period length start at 2×15 minutes.
-- Rows that already store another duration are left unchanged.
ALTER TABLE matches ALTER COLUMN period_length_seconds SET DEFAULT 900;
