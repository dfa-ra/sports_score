-- Remove DemoDataSeeder rows. Keeps sports, Flyway history, gallery, site_settings
-- and ADMIN users (ADMIN_EMAIL). Run on the stand, not from CI.
--
-- First set APP_DEMO_DATA=false in /opt/studentleague/.env, then:
--   docker compose --project-directory /opt/studentleague --env-file /opt/studentleague/.env \
--     -f /opt/studentleague/deploy/docker-compose.yml --project-name studentleague-prod \
--     exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < deploy/wipe-demo.sql
--   docker compose ... up -d --no-deps --force-recreate backend

BEGIN;

DELETE FROM match_events;
DELETE FROM match_lineup_players;
DELETE FROM match_referees;
DELETE FROM matches;
DELETE FROM tournament_teams;
DELETE FROM tournaments;

UPDATE teams SET captain_id = NULL;
DELETE FROM team_members;
DELETE FROM teams;

DELETE FROM player_profiles
 WHERE user_id IN (SELECT id FROM users WHERE role <> 'ADMIN');

DELETE FROM refresh_tokens
 WHERE user_id IN (SELECT id FROM users WHERE role <> 'ADMIN');
DELETE FROM device_tokens
 WHERE user_id IN (SELECT id FROM users WHERE role <> 'ADMIN');
DELETE FROM user_roles
 WHERE user_id IN (SELECT id FROM users WHERE role <> 'ADMIN');
DELETE FROM users WHERE role <> 'ADMIN';

COMMIT;
