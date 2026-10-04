-- match_events.game_time written by the referee pad before 0.2.34 is the
-- count-up clock INSIDE the current period (0..period_length_seconds).
-- The hall clock counts down and the pad draws that countdown, but the
-- number saved was seconds already played in the half, not seconds left.
--
-- Admin protocol rows already store minute * 60 from kickoff. Once that
-- minute is past the first period the value is greater than one period
-- length and this update leaves it alone.
--
-- Rule for rows we cannot otherwise tell apart: period > 1 and
-- game_time <= period_length_seconds is legacy within-period elapsed, so
-- add the lengths of the previous periods. A value already past the period
-- length is elapsed from kickoff and is not converted again.
-- Exact boundary (game_time = period length in a later period) is treated
-- as a full period of count-up. The only collision is an admin minute that
-- landed exactly on that boundary under the old period formula (minute 15
-- of a 15-minute period was stored as period 2 and 900 seconds). New admin
-- minutes from 0.2.34 keep 15' in the first half.

UPDATE match_events AS e
SET game_time = e.game_time + (e.period - 1) * m.period_length_seconds
FROM matches AS m
WHERE e.match_id = m.id
  AND e.period > 1
  AND e.game_time IS NOT NULL
  AND m.period_length_seconds > 0
  AND e.game_time <= m.period_length_seconds;
