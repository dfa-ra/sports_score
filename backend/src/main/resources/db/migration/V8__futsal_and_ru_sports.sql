INSERT INTO sports (id, name, code)
VALUES ('11111111-1111-1111-1111-111111111105', 'Футзал', 'FUTSAL')
ON CONFLICT (code) DO NOTHING;

UPDATE sports SET name = 'Футбол' WHERE code = 'FOOTBALL';
UPDATE sports SET name = 'Футзал' WHERE code = 'FUTSAL';
UPDATE sports SET name = 'Баскетбол' WHERE code = 'BASKETBALL';
UPDATE sports SET name = 'Волейбол' WHERE code = 'VOLLEYBALL';
UPDATE sports SET name = 'Хоккей' WHERE code = 'HOCKEY';
