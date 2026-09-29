-- Google sign-in used to insert an empty player_profiles row and the profile page
-- then showed the account as a player («Игрок»). Position was never stored by that
-- path; the word «Нападающий» was only a form placeholder, so a stub position is
-- NULL, blank, or that placeholder if someone saved the form without choosing.
--
-- Demote and delete the profile only when EVERY condition holds (a real player is kept):
--   * users.google_sub is set (the account came from Google sign-in)
--   * no approved role other than FAN or PLAYER (captain, referee, and admin stay)
--   * jersey number, date of birth, and bio are empty
--   * position is null, blank, or exactly «Нападающий»
--   * no team_members row, and the profile is not teams.captain_id
--   * no match_events (player or secondary) and no match_lineup_players row
--   * no uploaded photo: avatar and user photo are null, blank, or an external https
--     URL that is not our /media/ upload. A stored file (/media/avatars/...) keeps the player.
--
-- The PLAYER role row is removed and users.role falls back to FAN. The user account stays.

WITH stubs AS (
    SELECT p.id AS profile_id, p.user_id
    FROM player_profiles p
    JOIN users u ON u.id = p.user_id
    WHERE u.google_sub IS NOT NULL
      AND p.jersey_number IS NULL
      AND p.date_of_birth IS NULL
      AND (p.bio IS NULL OR trim(p.bio) = '')
      AND (
            p.position IS NULL
            OR trim(p.position) = ''
            OR p.position = 'Нападающий'
          )
      AND NOT EXISTS (
            SELECT 1
            FROM user_roles ur
            WHERE ur.user_id = u.id
              AND ur.status = 'APPROVED'
              AND ur.role NOT IN ('FAN', 'PLAYER')
          )
      AND NOT EXISTS (SELECT 1 FROM team_members tm WHERE tm.player_id = p.id)
      AND NOT EXISTS (SELECT 1 FROM teams t WHERE t.captain_id = p.id)
      AND NOT EXISTS (
            SELECT 1
            FROM match_events e
            WHERE e.player_id = p.id OR e.secondary_player_id = p.id
          )
      AND NOT EXISTS (SELECT 1 FROM match_lineup_players lp WHERE lp.player_id = p.id)
      AND (
            p.avatar_url IS NULL
            OR trim(p.avatar_url) = ''
            OR (p.avatar_url LIKE 'https://%' AND p.avatar_url NOT LIKE '%/media/%')
          )
      AND (
            u.photo_url IS NULL
            OR trim(u.photo_url) = ''
            OR (u.photo_url LIKE 'https://%' AND u.photo_url NOT LIKE '%/media/%')
          )
),
drop_player_role AS (
    DELETE FROM user_roles ur
    USING stubs s
    WHERE ur.user_id = s.user_id
      AND ur.role = 'PLAYER'
    RETURNING ur.user_id
),
demote_users AS (
    UPDATE users u
    SET role = 'FAN',
        updated_at = NOW()
    FROM stubs s
    WHERE u.id = s.user_id
      AND u.role = 'PLAYER'
      AND NOT EXISTS (
            SELECT 1
            FROM user_roles ur
            WHERE ur.user_id = u.id
              AND ur.status = 'APPROVED'
              AND ur.role NOT IN ('FAN', 'PLAYER')
          )
    RETURNING u.id
)
DELETE FROM player_profiles p
USING stubs s
WHERE p.id = s.profile_id
  AND (SELECT count(*) FROM drop_player_role) >= 0
  AND (SELECT count(*) FROM demote_users) >= 0;
