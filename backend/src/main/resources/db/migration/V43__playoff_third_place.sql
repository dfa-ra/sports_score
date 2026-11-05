-- The match for 3rd place is another slot in the final round (slot_index 1),
-- not a new round. Existing pairs stay MAIN. THIRD is created only after both
-- semifinals have a loser.

ALTER TABLE playoff_slots
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'MAIN';

ALTER TABLE playoff_slots
    ADD CONSTRAINT ck_playoff_slot_kind CHECK (kind IN ('MAIN', 'THIRD'));
