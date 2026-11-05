-- Clear only the seeded demo blurb. Any other tournament description stays.
UPDATE tournaments
SET description = NULL
WHERE description IS NOT NULL
  AND btrim(description) = 'Демо-сезон, чтобы таблица и статистика не были пустыми.';
