-- Team timeout is one event per team per half. The service rejects a second
-- TIMEOUT in the same period; fouls keep using the existing FOUL type.
ALTER TABLE match_events DROP CONSTRAINT IF EXISTS ck_match_events_type;

ALTER TABLE match_events ADD CONSTRAINT ck_match_events_type CHECK (event_type IN (
    'GOAL', 'ASSIST', 'YELLOW_CARD', 'RED_CARD', 'FOUL',
    'SUBSTITUTION', 'POINT', 'PERIOD_START', 'PERIOD_END', 'OTHER',
    'TIMEOUT'
));
