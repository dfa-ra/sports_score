-- Demo goals stored the typed minute in game_time (9, not 540). The protocol
-- divides by 60, so every such goal printed as 0'. Convert only rows whose
-- timestamp sits on that same minute after the scheduled kickoff. A referee
-- row stores seconds and its timestamp does not line up with minute * 60.
UPDATE match_events AS e
SET game_time = e.game_time * 60
FROM matches AS m
WHERE e.match_id = m.id
  AND e.game_time IS NOT NULL
  AND e.game_time > 0
  AND e.game_time < 60
  AND e.timestamp IS NOT NULL
  AND m.scheduled_at IS NOT NULL
  AND abs(extract(epoch FROM (e.timestamp - m.scheduled_at)) - (e.game_time * 60)) <= 2;
