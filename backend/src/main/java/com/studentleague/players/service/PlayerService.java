package com.studentleague.players.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.entity.MatchLineupPlayer;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.players.dto.PlayerCardResponse;
import com.studentleague.players.dto.PlayerProfileRequest;
import com.studentleague.players.dto.PlayerProfileResponse;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.entity.TeamMember;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlayerService {

    private final PlayerProfileRepository playerProfileRepository;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchRepository matchRepository;
    private final MatchLineupPlayerRepository lineupPlayerRepository;
    private final TournamentRepository tournamentRepository;

    public PlayerService(
            PlayerProfileRepository playerProfileRepository,
            UserRepository userRepository,
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository,
            MatchEventRepository matchEventRepository,
            MatchRepository matchRepository,
            MatchLineupPlayerRepository lineupPlayerRepository,
            TournamentRepository tournamentRepository
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.matchEventRepository = matchEventRepository;
        this.matchRepository = matchRepository;
        this.lineupPlayerRepository = lineupPlayerRepository;
        this.tournamentRepository = tournamentRepository;
    }

    @Transactional
    public PlayerProfileResponse createOrUpdateMyProfile(UUID userId, PlayerProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        PlayerProfile profile = playerProfileRepository.findByUserId(userId).orElseGet(PlayerProfile::new);
        boolean creating = profile.getId() == null;
        profile.setUserId(userId);
        apply(profile, request);
        playerProfileRepository.save(profile);

        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public PlayerProfileResponse getById(UUID id) {
        return toResponse(requireProfile(id));
    }

    @Transactional(readOnly = true)
    public PlayerProfileResponse getMyProfile(UUID userId) {
        PlayerProfile profile = playerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Player profile not found"));
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public Page<PlayerProfileResponse> list(String query, UUID teamId, Pageable pageable) {
        if (teamId != null) {
            var memberIds = teamMemberRepository.findByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE).stream()
                    .map(TeamMember::getPlayerId)
                    .toList();
            List<PlayerProfile> profiles = playerProfileRepository.findAllById(memberIds);
            if (query != null && !query.isBlank()) {
                String q = query.trim().toLowerCase();
                profiles = profiles.stream()
                        .filter(profile -> contains(profile.getFirstName(), q)
                                || contains(profile.getLastName(), q)
                                || contains(profile.getDisplayName(), q))
                        .toList();
            }
            List<PlayerProfileResponse> mapped = profiles.stream().map(this::toResponse).toList();
            return new org.springframework.data.domain.PageImpl<>(mapped, pageable, mapped.size());
        }
        Page<PlayerProfile> page;
        if (query == null || query.isBlank()) {
            page = playerProfileRepository.findAll(pageable);
        } else {
            String q = query.trim();
            page = playerProfileRepository
                    .findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                            q, q, q, pageable);
        }
        return page.map(this::toResponse);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    @Transactional(readOnly = true)
    public PlayerCardResponse getPublicCard(UUID playerId) {
        PlayerProfile profile = requireProfile(playerId);
        List<TeamMember> memberships = teamMemberRepository.findByPlayerIdAndStatus(playerId, TeamMemberStatus.ACTIVE);
        PlayerCardResponse.TeamSummary teamSummary = null;
        if (!memberships.isEmpty()) {
            Team team = teamRepository.findById(memberships.getFirst().getTeamId()).orElse(null);
            if (team != null) {
                teamSummary = new PlayerCardResponse.TeamSummary(
                        team.getId(), team.getName(), team.getShortName(), team.getLogoUrl());
            }
        }
        List<PlayerCardResponse.MatchHistoryItem> history = buildMatchHistory(playerId, memberships);
        Map<String, Object> statistics = seasonTotals(history, profile.getPosition());

        return new PlayerCardResponse(
                profile.getId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                profile.getJerseyNumber(),
                profile.getPosition(),
                profile.getDateOfBirth(),
                teamSummary,
                statistics,
                history
        );
    }

    private Map<String, Object> seasonTotals(List<PlayerCardResponse.MatchHistoryItem> history, String position) {
        Map<String, Object> statistics = new LinkedHashMap<>();
        List<PlayerCardResponse.MatchHistoryItem> finished = history.stream()
                .filter(item -> MatchStatus.FINISHED.name().equals(item.status()))
                .toList();
        statistics.put("appearances", finished.size());
        statistics.put("goals", finished.stream().mapToInt(PlayerCardResponse.MatchHistoryItem::goals).sum());
        statistics.put("assists", finished.stream().mapToInt(PlayerCardResponse.MatchHistoryItem::assists).sum());
        statistics.put("yellowCards", finished.stream().mapToInt(PlayerCardResponse.MatchHistoryItem::yellowCards).sum());
        statistics.put("redCards", finished.stream().mapToInt(PlayerCardResponse.MatchHistoryItem::redCards).sum());
        if (looksLikeGoalkeeper(position)) {
            long cleanSheets = finished.stream().filter(PlayerService::isCleanSheet).count();
            statistics.put("cleanSheets", cleanSheets);
        }
        return statistics;
    }

    private static boolean isCleanSheet(PlayerCardResponse.MatchHistoryItem item) {
        int conceded = item.home() ? nz(item.awayScore()) : nz(item.homeScore());
        return conceded == 0;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private List<PlayerCardResponse.MatchHistoryItem> buildMatchHistory(UUID playerId, List<TeamMember> memberships) {
        List<MatchLineupPlayer> lineups = lineupPlayerRepository.findByPlayerId(playerId);
        List<MatchEvent> involvement = matchEventRepository.findActiveInvolvingPlayer(playerId);

        Map<UUID, MatchLineupPlayer> lineupByMatch = new HashMap<>();
        for (MatchLineupPlayer row : lineups) {
            lineupByMatch.putIfAbsent(row.getMatchId(), row);
        }
        Map<UUID, List<MatchEvent>> eventsByMatch = involvement.stream()
                .collect(Collectors.groupingBy(MatchEvent::getMatchId));

        Set<UUID> matchIds = new HashSet<>();
        matchIds.addAll(lineupByMatch.keySet());
        matchIds.addAll(eventsByMatch.keySet());
        if (matchIds.isEmpty()) {
            return List.of();
        }

        List<Match> matches = matchRepository.findAllById(matchIds).stream()
                .filter(match -> match.getStatus() == MatchStatus.FINISHED || match.getStatus() == MatchStatus.LIVE)
                .sorted(Comparator.comparing(Match::getScheduledAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(50)
                .toList();

        Set<UUID> teamIds = new HashSet<>();
        Set<UUID> tournamentIds = new HashSet<>();
        for (Match match : matches) {
            teamIds.add(match.getHomeTeamId());
            teamIds.add(match.getAwayTeamId());
            tournamentIds.add(match.getTournamentId());
        }
        Map<UUID, Team> teams = teamRepository.findAllById(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, team -> team));
        Map<UUID, Tournament> tournaments = tournamentRepository.findAllById(tournamentIds).stream()
                .collect(Collectors.toMap(Tournament::getId, tournament -> tournament));

        Set<UUID> memberTeamIds = memberships.stream()
                .map(TeamMember::getTeamId)
                .collect(Collectors.toSet());

        List<PlayerCardResponse.MatchHistoryItem> history = new ArrayList<>();
        for (Match match : matches) {
            List<MatchEvent> events = eventsByMatch.getOrDefault(match.getId(), List.of());
            MatchLineupPlayer lineup = lineupByMatch.get(match.getId());
            UUID teamId = resolvePlayerTeamId(match, lineup, events, playerId, memberTeamIds);
            boolean home = teamId != null && teamId.equals(match.getHomeTeamId());

            Team homeTeam = teams.get(match.getHomeTeamId());
            Team awayTeam = teams.get(match.getAwayTeamId());
            String homeName = homeTeam == null ? "?" : homeTeam.getName();
            String awayName = awayTeam == null ? "?" : awayTeam.getName();
            String opponentName = home ? awayName : homeName;
            Tournament tournament = tournaments.get(match.getTournamentId());

            int goals = 0;
            int assists = 0;
            int yellowCards = 0;
            int redCards = 0;
            Integer lastMinute = null;
            for (MatchEvent event : events) {
                if (event.getGameTime() != null) {
                    lastMinute = event.getGameTime();
                }
                if (playerId.equals(event.getPlayerId())) {
                    switch (event.getEventType()) {
                        case GOAL -> goals++;
                        case ASSIST -> assists++;
                        case YELLOW_CARD -> yellowCards++;
                        case RED_CARD -> redCards++;
                        default -> {
                        }
                    }
                }
                if (event.getEventType() == MatchEventType.GOAL
                        && playerId.equals(event.getSecondaryPlayerId())) {
                    assists++;
                }
            }

            history.add(new PlayerCardResponse.MatchHistoryItem(
                    match.getId(),
                    match.getScheduledAt(),
                    tournament == null ? null : tournament.getName(),
                    homeName,
                    awayName,
                    homeTeam == null ? null : homeTeam.getLogoUrl(),
                    awayTeam == null ? null : awayTeam.getLogoUrl(),
                    opponentName,
                    home,
                    match.getHomeScore(),
                    match.getAwayScore(),
                    match.getStatus().name(),
                    outcomeOf(match, home),
                    goals,
                    assists,
                    yellowCards,
                    redCards,
                    minutesPlayed(match, lineup, lastMinute)
            ));
        }
        return history;
    }

    private static UUID resolvePlayerTeamId(
            Match match,
            MatchLineupPlayer lineup,
            List<MatchEvent> events,
            UUID playerId,
            Set<UUID> memberTeamIds
    ) {
        if (lineup != null) {
            return lineup.getTeamId();
        }
        for (MatchEvent event : events) {
            if (event.getTeamId() != null && (playerId.equals(event.getPlayerId())
                    || playerId.equals(event.getSecondaryPlayerId()))) {
                return event.getTeamId();
            }
        }
        if (memberTeamIds.contains(match.getHomeTeamId())) {
            return match.getHomeTeamId();
        }
        if (memberTeamIds.contains(match.getAwayTeamId())) {
            return match.getAwayTeamId();
        }
        return null;
    }

    private static String outcomeOf(Match match, boolean home) {
        if (match.getStatus() != MatchStatus.FINISHED) {
            return null;
        }
        int scored = home ? match.getHomeScore() : match.getAwayScore();
        int conceded = home ? match.getAwayScore() : match.getHomeScore();
        if (scored > conceded) {
            return "WIN";
        }
        if (scored < conceded) {
            return "LOSS";
        }
        return "DRAW";
    }

    private static Integer minutesPlayed(Match match, MatchLineupPlayer lineup, Integer lastEventMinute) {
        if (lineup != null && lineup.isStarter() && match.getGameTimeSeconds() != null) {
            return Math.max(1, match.getGameTimeSeconds() / 60);
        }
        if (lineup != null && lineup.isStarter()) {
            return 40;
        }
        return lastEventMinute;
    }

    private static boolean looksLikeGoalkeeper(String position) {
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

    private PlayerProfile requireProfile(UUID id) {
        return playerProfileRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Player not found"));
    }

    private void apply(PlayerProfile profile, PlayerProfileRequest request) {
        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        profile.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? request.firstName() + " " + request.lastName()
                : request.displayName());
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setAvatarUrl(request.avatarUrl());
        profile.setJerseyNumber(request.jerseyNumber());
        profile.setPosition(request.position());
        profile.setBio(request.bio());
    }

    private PlayerProfileResponse toResponse(PlayerProfile profile) {
        return new PlayerProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getDisplayName(),
                profile.getDateOfBirth(),
                profile.getAvatarUrl(),
                profile.getJerseyNumber(),
                profile.getPosition(),
                profile.getBio()
        );
    }
}
