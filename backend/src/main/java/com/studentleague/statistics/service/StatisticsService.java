package com.studentleague.statistics.service;

import com.studentleague.cache.StandingsBoardCache;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.entity.MatchLineupPlayer;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.statistics.dto.PlayerStatisticsResponse;
import com.studentleague.statistics.dto.StatisticsBoardResponse;
import com.studentleague.statistics.dto.TeamStatisticsResponse;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.entity.TeamMember;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    private final MatchEventRepository matchEventRepository;
    private final MatchRepository matchRepository;
    private final TournamentRepository tournamentRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final TeamRepository teamRepository;
    private final MatchLineupPlayerRepository lineupPlayerRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final StandingsBoardCache standingsBoardCache;

    public StatisticsService(
            MatchEventRepository matchEventRepository,
            MatchRepository matchRepository,
            TournamentRepository tournamentRepository,
            PlayerProfileRepository playerProfileRepository,
            TeamRepository teamRepository,
            MatchLineupPlayerRepository lineupPlayerRepository,
            TeamMemberRepository teamMemberRepository,
            UserRepository userRepository,
            StandingsBoardCache standingsBoardCache
    ) {
        this.matchEventRepository = matchEventRepository;
        this.matchRepository = matchRepository;
        this.tournamentRepository = tournamentRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.teamRepository = teamRepository;
        this.lineupPlayerRepository = lineupPlayerRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.standingsBoardCache = standingsBoardCache;
    }

    @Transactional(readOnly = true)
    public List<PlayerStatisticsResponse> playerStatistics(
            UUID tournamentId,
            Integer seasonYear,
            UUID teamId,
            UUID playerId
    ) {
        List<Match> matches = resolveMatches(tournamentId, seasonYear, teamId);
        Map<UUID, List<MatchEvent>> eventsByMatch = eventsByMatch(matches);
        Map<UUID, PlayerAccumulator> stats = new HashMap<>();

        for (Match match : matches) {
            List<MatchEvent> events = eventsByMatch.getOrDefault(match.getId(), List.of());
            Set<UUID> appeared = new HashSet<>();
            for (MatchEvent event : events) {
                if (event.getPlayerId() == null && event.getSecondaryPlayerId() == null) {
                    continue;
                }
                if (teamId != null && event.getTeamId() != null && !teamId.equals(event.getTeamId())) {
                    continue;
                }
                if (playerId != null
                        && !playerId.equals(event.getPlayerId())
                        && !playerId.equals(event.getSecondaryPlayerId())) {
                    continue;
                }
                if (event.getPlayerId() != null && (playerId == null || playerId.equals(event.getPlayerId()))) {
                    PlayerAccumulator acc = stats.computeIfAbsent(event.getPlayerId(), PlayerAccumulator::new);
                    appeared.add(event.getPlayerId());
                    switch (event.getEventType()) {
                        case GOAL -> acc.goals++;
                        case ASSIST -> acc.assists++;
                        case YELLOW_CARD -> acc.yellowCards++;
                        case RED_CARD -> acc.redCards++;
                        default -> {
                        }
                    }
                    if (event.getTeamId() != null) {
                        acc.teamId = event.getTeamId();
                    }
                }
                if (event.getEventType() == com.studentleague.matches.domain.MatchEventType.GOAL
                        && event.getSecondaryPlayerId() != null
                        && (playerId == null || playerId.equals(event.getSecondaryPlayerId()))) {
                    PlayerAccumulator assist = stats.computeIfAbsent(event.getSecondaryPlayerId(), PlayerAccumulator::new);
                    assist.assists++;
                    appeared.add(event.getSecondaryPlayerId());
                    if (event.getTeamId() != null) {
                        assist.teamId = event.getTeamId();
                    }
                }
            }
            for (UUID appearedPlayerId : appeared) {
                if (playerId != null && !playerId.equals(appearedPlayerId)) {
                    continue;
                }
                stats.computeIfAbsent(appearedPlayerId, PlayerAccumulator::new).appearances++;
            }
        }

        Map<UUID, PlayerProfile> profiles = playerProfileRepository.findAllById(stats.keySet()).stream()
                .collect(Collectors.toMap(PlayerProfile::getId, p -> p, (a, b) -> a));
        Set<UUID> eventTeamIds = stats.values().stream()
                .map(acc -> acc.teamId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        RosterMedia media = loadRoster(stats.keySet(), profiles, eventTeamIds);

        return stats.values().stream()
                .map(acc -> {
                    PlayerProfile profile = profiles.get(acc.playerId);
                    return toStat(
                            acc.playerId,
                            profile == null ? null : profile.getDisplayName(),
                            profile == null ? null : profile.getFirstName(),
                            profile == null ? null : profile.getLastName(),
                            profile,
                            acc.goals,
                            acc.assists,
                            acc.yellowCards,
                            acc.redCards,
                            acc.appearances,
                            acc.teamId,
                            0,
                            media
                    );
                })
                .sorted((a, b) -> Long.compare(b.goals(), a.goals()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlayerStatisticsResponse> scorers(UUID tournamentId, int limit) {
        return top(playerStatistics(tournamentId, null, null, null), Comparator.comparingLong(PlayerStatisticsResponse::goals), limit);
    }

    @Transactional(readOnly = true)
    public List<PlayerStatisticsResponse> assists(UUID tournamentId, int limit) {
        return top(playerStatistics(tournamentId, null, null, null), Comparator.comparingLong(PlayerStatisticsResponse::assists), limit);
    }

    @Transactional(readOnly = true)
    public StatisticsBoardResponse board(UUID tournamentId, int limit) {
        return standingsBoardCache.board(tournamentId, limit, () -> {
            List<PlayerStatisticsResponse> stats = playerStatistics(tournamentId, null, null, null);
            return new StatisticsBoardResponse(
                    top(stats, Comparator.comparingLong(PlayerStatisticsResponse::goals), limit),
                    top(stats, Comparator.comparingLong(PlayerStatisticsResponse::assists), limit),
                    rankKeepers(keeperCounts(tournamentId), stats, limit)
            );
        });
    }

    @Transactional(readOnly = true)
    public List<PlayerStatisticsResponse> goalkeepers(UUID tournamentId, int limit) {
        return rankKeepers(
                keeperCounts(tournamentId),
                playerStatistics(tournamentId, null, null, null),
                limit
        );
    }

    private List<PlayerStatisticsResponse> rankKeepers(
            KeeperCounts counts,
            List<PlayerStatisticsResponse> base,
            int limit
    ) {
        Map<UUID, Long> sheets = counts.cleanSheets;
        Map<UUID, PlayerProfile> profiles = playerProfileRepository.findAllById(sheets.keySet()).stream()
                .collect(Collectors.toMap(PlayerProfile::getId, p -> p, (a, b) -> a));
        Map<UUID, PlayerStatisticsResponse> byId = base.stream()
                .collect(Collectors.toMap(PlayerStatisticsResponse::playerId, p -> p, (a, b) -> a));
        Set<UUID> eventTeamIds = new HashSet<>(counts.teamIds.values());
        for (PlayerStatisticsResponse row : byId.values()) {
            if (row.teamId() != null) {
                eventTeamIds.add(row.teamId());
            }
        }
        RosterMedia media = loadRoster(sheets.keySet(), profiles, eventTeamIds);
        return sheets.entrySet().stream()
                .map(entry -> {
                    PlayerStatisticsResponse existing = byId.get(entry.getKey());
                    PlayerProfile profile = profiles.get(entry.getKey());
                    long games = counts.appearances.getOrDefault(entry.getKey(), entry.getValue());
                    UUID eventTeam = existing != null && existing.teamId() != null
                            ? existing.teamId()
                            : counts.teamIds.get(entry.getKey());
                    if (existing != null) {
                        return toStat(
                                existing.playerId(),
                                existing.displayName(),
                                existing.firstName(),
                                existing.lastName(),
                                profile,
                                existing.goals(),
                                existing.assists(),
                                existing.yellowCards(),
                                existing.redCards(),
                                games,
                                eventTeam,
                                entry.getValue(),
                                media
                        );
                    }
                    return toStat(
                            entry.getKey(),
                            null,
                            null,
                            null,
                            profile,
                            0, 0, 0, 0,
                            games,
                            eventTeam,
                            entry.getValue(),
                            media
                    );
                })
                .sorted((a, b) -> Long.compare(b.cleanSheets(), a.cleanSheets()))
                .limit(cap(limit))
                .toList();
    }

    private static List<PlayerStatisticsResponse> top(
            List<PlayerStatisticsResponse> stats,
            Comparator<PlayerStatisticsResponse> order,
            int limit
    ) {
        return stats.stream().sorted(order.reversed()).limit(cap(limit)).toList();
    }

    private static int cap(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 100);
    }

    private KeeperCounts keeperCounts(UUID tournamentId) {
        KeeperCounts counts = new KeeperCounts();
        List<Match> matches = resolveMatches(tournamentId, null, null).stream()
                .filter(match -> match.getStatus() == MatchStatus.FINISHED)
                .toList();
        if (matches.isEmpty()) {
            return counts;
        }
        List<UUID> matchIds = matches.stream().map(Match::getId).toList();
        Map<String, List<MatchLineupPlayer>> lineups = lineupPlayerRepository.findByMatchIdIn(matchIds).stream()
                .collect(Collectors.groupingBy(row -> row.getMatchId() + ":" + row.getTeamId()));
        Set<UUID> lineupPlayerIds = lineups.values().stream()
                .flatMap(List::stream)
                .map(MatchLineupPlayer::getPlayerId)
                .collect(Collectors.toSet());
        Map<UUID, PlayerProfile> profiles = new HashMap<>();
        if (!lineupPlayerIds.isEmpty()) {
            playerProfileRepository.findAllById(lineupPlayerIds).forEach(profile -> profiles.put(profile.getId(), profile));
        }

        List<UUID> eventMatchIds = new ArrayList<>();
        Set<String> eventSides = new HashSet<>();
        for (Match match : matches) {
            if (!countCleanSheet(counts, lineups, profiles, match, match.getHomeTeamId(), match.getAwayScore() == 0)) {
                eventMatchIds.add(match.getId());
                eventSides.add(match.getId() + ":" + match.getHomeTeamId());
            }
            if (!countCleanSheet(counts, lineups, profiles, match, match.getAwayTeamId(), match.getHomeScore() == 0)) {
                eventMatchIds.add(match.getId());
                eventSides.add(match.getId() + ":" + match.getAwayTeamId());
            }
        }
        if (!eventSides.isEmpty()) {
            Map<UUID, List<MatchEvent>> eventsByMatch = matchEventRepository.findByMatchIdInAndVoidedFalse(eventMatchIds).stream()
                    .collect(Collectors.groupingBy(MatchEvent::getMatchId));
            Set<UUID> extraIds = eventsByMatch.values().stream()
                    .flatMap(List::stream)
                    .map(MatchEvent::getPlayerId)
                    .filter(id -> id != null && !profiles.containsKey(id))
                    .collect(Collectors.toSet());
            if (!extraIds.isEmpty()) {
                playerProfileRepository.findAllById(extraIds).forEach(profile -> profiles.put(profile.getId(), profile));
            }
            for (Match match : matches) {
                List<MatchEvent> events = eventsByMatch.getOrDefault(match.getId(), List.of());
                if (eventSides.contains(match.getId() + ":" + match.getHomeTeamId())) {
                    countCleanSheetFromEvents(counts, events, profiles, match.getHomeTeamId(), match.getAwayScore() == 0);
                }
                if (eventSides.contains(match.getId() + ":" + match.getAwayTeamId())) {
                    countCleanSheetFromEvents(counts, events, profiles, match.getAwayTeamId(), match.getHomeScore() == 0);
                }
            }
        }
        return counts;
    }

    private boolean countCleanSheet(
            KeeperCounts counts,
            Map<String, List<MatchLineupPlayer>> lineups,
            Map<UUID, PlayerProfile> profiles,
            Match match,
            UUID teamId,
            boolean clean
    ) {
        List<UUID> keepers = lineups.getOrDefault(match.getId() + ":" + teamId, List.of()).stream()
                .map(MatchLineupPlayer::getPlayerId)
                .filter(playerId -> looksLikeGoalkeeper(positionOf(profiles, playerId)))
                .distinct()
                .toList();
        if (keepers.isEmpty()) {
            return !clean;
        }
        for (UUID keeperId : keepers) {
            counts.appearances.merge(keeperId, 1L, Long::sum);
            counts.noteTeam(keeperId, teamId);
            if (clean) {
                counts.cleanSheets.merge(keeperId, 1L, Long::sum);
            }
        }
        return true;
    }

    private void countCleanSheetFromEvents(
            KeeperCounts counts,
            List<MatchEvent> events,
            Map<UUID, PlayerProfile> profiles,
            UUID teamId,
            boolean clean
    ) {
        if (!clean) {
            return;
        }
        events.stream()
                .filter(event -> teamId.equals(event.getTeamId()) && event.getPlayerId() != null)
                .map(MatchEvent::getPlayerId)
                .distinct()
                .filter(playerId -> looksLikeGoalkeeper(positionOf(profiles, playerId)))
                .forEach(keeperId -> {
                    counts.cleanSheets.merge(keeperId, 1L, Long::sum);
                    counts.appearances.merge(keeperId, 1L, Long::sum);
                    counts.noteTeam(keeperId, teamId);
                });
    }

    private static String positionOf(Map<UUID, PlayerProfile> profiles, UUID playerId) {
        PlayerProfile profile = profiles.get(playerId);
        return profile == null ? null : profile.getPosition();
    }

    static boolean looksLikeGoalkeeper(String position) {
        if (position == null || position.isBlank()) {
            return false;
        }
        String value = position.toLowerCase();
        return value.contains("gk")
                || value.contains("goal")
                || value.contains("вратар")
                || value.matches(".*\\bвр\\b.*")
                || value.contains("keeper");
    }

    @Transactional(readOnly = true)
    public List<TeamStatisticsResponse> teamStatistics(UUID tournamentId, Integer seasonYear, UUID teamId) {
        List<Match> matches = resolveMatches(tournamentId, seasonYear, teamId);
        Map<UUID, TeamAccumulator> table = new HashMap<>();

        for (Match match : matches) {
            if (match.getStatus() != MatchStatus.FINISHED) {
                continue;
            }
            if (teamId != null
                    && !teamId.equals(match.getHomeTeamId())
                    && !teamId.equals(match.getAwayTeamId())) {
                continue;
            }
            TeamAccumulator home = table.computeIfAbsent(match.getHomeTeamId(), TeamAccumulator::new);
            TeamAccumulator away = table.computeIfAbsent(match.getAwayTeamId(), TeamAccumulator::new);
            home.goalsFor += match.getHomeScore();
            home.goalsAgainst += match.getAwayScore();
            away.goalsFor += match.getAwayScore();
            away.goalsAgainst += match.getHomeScore();
            if (match.getHomeScore() > match.getAwayScore()) {
                home.wins++;
                home.points += 3;
                away.losses++;
            } else if (match.getHomeScore() < match.getAwayScore()) {
                away.wins++;
                away.points += 3;
                home.losses++;
            } else {
                home.draws++;
                away.draws++;
                home.points++;
                away.points++;
            }
        }

        Map<UUID, Team> teams = teamRepository.findAllById(table.keySet()).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));

        return table.values().stream()
                .map(acc -> new TeamStatisticsResponse(
                        acc.teamId,
                        teams.containsKey(acc.teamId) ? teams.get(acc.teamId).getName() : null,
                        acc.wins,
                        acc.draws,
                        acc.losses,
                        acc.points,
                        acc.goalsFor,
                        acc.goalsAgainst
                ))
                .sorted((a, b) -> Long.compare(b.points(), a.points()))
                .toList();
    }

    private List<Match> resolveMatches(UUID tournamentId, Integer seasonYear, UUID teamId) {
        List<Match> matches;
        if (tournamentId != null) {
            matches = matchRepository.findByTournamentId(tournamentId);
        } else if (seasonYear != null) {
            Set<UUID> tournamentIds = tournamentRepository.findAll().stream()
                    .filter(t -> seasonYear.equals(t.getSeasonYear()))
                    .map(Tournament::getId)
                    .collect(Collectors.toSet());
            matches = tournamentIds.isEmpty() ? List.of() : matchRepository.findByTournamentIdIn(tournamentIds);
        } else {
            matches = matchRepository.findAll();
        }
        if (teamId == null) {
            return matches;
        }
        return matches.stream()
                .filter(match -> teamId.equals(match.getHomeTeamId()) || teamId.equals(match.getAwayTeamId()))
                .toList();
    }

    private Map<UUID, List<MatchEvent>> eventsByMatch(List<Match> matches) {
        if (matches.isEmpty()) {
            return Map.of();
        }
        List<UUID> matchIds = matches.stream().map(Match::getId).toList();
        return matchEventRepository.findByMatchIdInAndVoidedFalse(matchIds).stream()
                .collect(Collectors.groupingBy(MatchEvent::getMatchId));
    }

    private RosterMedia loadRoster(Set<UUID> playerIds, Map<UUID, PlayerProfile> profiles, Set<UUID> eventTeamIds) {
        Map<UUID, List<UUID>> memberships = new HashMap<>();
        if (!playerIds.isEmpty()) {
            for (TeamMember member : teamMemberRepository.findByPlayerIdInAndStatus(playerIds, TeamMemberStatus.ACTIVE)) {
                if (member.getPlayerId() == null || member.getTeamId() == null) {
                    continue;
                }
                memberships.computeIfAbsent(member.getPlayerId(), id -> new ArrayList<>()).add(member.getTeamId());
            }
        }
        Set<UUID> teamIds = new HashSet<>();
        if (eventTeamIds != null) {
            for (UUID teamId : eventTeamIds) {
                if (teamId != null) {
                    teamIds.add(teamId);
                }
            }
        }
        for (List<UUID> joined : memberships.values()) {
            teamIds.addAll(joined);
        }
        Map<UUID, Team> teams = teamIds.isEmpty()
                ? Map.of()
                : teamRepository.findAllById(teamIds).stream()
                        .collect(Collectors.toMap(Team::getId, team -> team, (a, b) -> a));
        Set<UUID> userIds = profiles.values().stream()
                .filter(profile -> profile.getUserId() != null && !hasText(profile.getAvatarUrl()))
                .map(PlayerProfile::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, String> photos = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .filter(user -> hasText(user.getPhotoUrl()))
                        .collect(Collectors.toMap(User::getId, User::getPhotoUrl, (a, b) -> a));
        return new RosterMedia(memberships, teams, photos);
    }

    private static PlayerStatisticsResponse toStat(
            UUID playerId,
            String displayName,
            String firstName,
            String lastName,
            PlayerProfile profile,
            long goals,
            long assists,
            long yellowCards,
            long redCards,
            long appearances,
            UUID eventTeamId,
            long cleanSheets,
            RosterMedia media
    ) {
        UUID teamId = media.resolveTeamId(playerId, eventTeamId);
        return new PlayerStatisticsResponse(
                playerId,
                displayName != null ? displayName : profile == null ? null : profile.getDisplayName(),
                goals,
                assists,
                yellowCards,
                redCards,
                appearances,
                teamId,
                cleanSheets,
                firstName != null ? firstName : profile == null ? null : profile.getFirstName(),
                lastName != null ? lastName : profile == null ? null : profile.getLastName(),
                media.avatar(profile),
                media.logo(teamId)
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class KeeperCounts {
        private final Map<UUID, Long> cleanSheets = new HashMap<>();
        private final Map<UUID, Long> appearances = new HashMap<>();
        private final Map<UUID, UUID> teamIds = new HashMap<>();

        private void noteTeam(UUID playerId, UUID teamId) {
            if (playerId != null && teamId != null) {
                teamIds.putIfAbsent(playerId, teamId);
            }
        }
    }

    /**
     * Photos and crests for a whole board, loaded once: memberships, teams, and account photos
     * for players who have no avatar of their own.
     */
    private static final class RosterMedia {
        private final Map<UUID, List<UUID>> memberships;
        private final Map<UUID, Team> teams;
        private final Map<UUID, String> userPhotos;

        private RosterMedia(
                Map<UUID, List<UUID>> memberships,
                Map<UUID, Team> teams,
                Map<UUID, String> userPhotos
        ) {
            this.memberships = memberships;
            this.teams = teams;
            this.userPhotos = userPhotos;
        }

        private UUID resolveTeamId(UUID playerId, UUID eventTeamId) {
            List<UUID> joined = memberships.getOrDefault(playerId, List.of());
            if (eventTeamId != null && joined.contains(eventTeamId) && playsFor(eventTeamId)) {
                return eventTeamId;
            }
            for (UUID joinedId : joined) {
                if (playsFor(joinedId)) {
                    return joinedId;
                }
            }
            if (eventTeamId != null && playsFor(eventTeamId)) {
                return eventTeamId;
            }
            if (eventTeamId != null && !teams.containsKey(eventTeamId)) {
                return eventTeamId;
            }
            return null;
        }

        private boolean playsFor(UUID teamId) {
            Team team = teams.get(teamId);
            return team != null && !team.isDisbanded();
        }

        private String logo(UUID teamId) {
            if (!playsFor(teamId)) {
                return null;
            }
            Team team = teams.get(teamId);
            return team != null && hasText(team.getLogoUrl()) ? team.getLogoUrl().trim() : null;
        }

        private String avatar(PlayerProfile profile) {
            if (profile == null) {
                return null;
            }
            if (hasText(profile.getAvatarUrl())) {
                return profile.getAvatarUrl().trim();
            }
            if (profile.getUserId() == null) {
                return null;
            }
            String photo = userPhotos.get(profile.getUserId());
            return hasText(photo) ? photo.trim() : null;
        }
    }

    private static final class PlayerAccumulator {
        private final UUID playerId;
        private long goals;
        private long assists;
        private long yellowCards;
        private long redCards;
        private long appearances;
        private UUID teamId;

        private PlayerAccumulator(UUID playerId) {
            this.playerId = playerId;
        }
    }

    private static final class TeamAccumulator {
        private final UUID teamId;
        private long wins;
        private long draws;
        private long losses;
        private long points;
        private long goalsFor;
        private long goalsAgainst;

        private TeamAccumulator(UUID teamId) {
            this.teamId = teamId;
        }
    }
}
