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
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import com.studentleague.users.service.RoleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final RoleService roleService;

    public PlayerService(
            PlayerProfileRepository playerProfileRepository,
            UserRepository userRepository,
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository,
            MatchEventRepository matchEventRepository,
            MatchRepository matchRepository,
            MatchLineupPlayerRepository lineupPlayerRepository,
            TournamentRepository tournamentRepository,
            RoleService roleService
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.matchEventRepository = matchEventRepository;
        this.matchRepository = matchRepository;
        this.lineupPlayerRepository = lineupPlayerRepository;
        this.tournamentRepository = tournamentRepository;
        this.roleService = roleService;
    }

    @Transactional
    public PlayerProfileResponse createOrUpdateMyProfile(UUID userId, PlayerProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        PlayerProfile existing = playerProfileRepository.findByUserId(userId).orElse(null);
        boolean alreadyPlayer = roleService.hasApproved(userId, Role.PLAYER)
                || roleService.hasApproved(userId, Role.CAPTAIN)
                || roleService.hasApproved(userId, Role.ADMIN);
        boolean promote = false;
        if (!alreadyPlayer && (existing == null || completeRegistration(request))) {
            requirePlayerRegistration(request, existing, user);
            promote = true;
        }

        PlayerProfile profile = existing != null ? existing : new PlayerProfile();
        profile.setUserId(userId);
        apply(profile, request);
        if (!hasText(profile.getAvatarUrl())) {
            String uploaded = uploadedPhoto(null, null, user);
            if (uploaded != null) {
                profile.setAvatarUrl(uploaded);
            }
        }
        playerProfileRepository.save(profile);
        if (hasText(profile.getAvatarUrl()) && !profile.getAvatarUrl().equals(user.getPhotoUrl())) {
            user.setPhotoUrl(profile.getAvatarUrl());
            userRepository.save(user);
        }
        if (promote) {
            roleService.grantApproved(user, Role.PLAYER, profile.getAvatarUrl());
        }

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
        Pageable limited = PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), 12),
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by("lastName")
        );
        String q = query == null ? "" : query.trim();
        if (q.length() < 2 && teamId == null) {
            return new PageImpl<>(List.of(), limited, 0);
        }
        if (teamId != null) {
            var memberIds = teamMemberRepository.findByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE).stream()
                    .map(TeamMember::getPlayerId)
                    .toList();
            List<PlayerProfile> profiles = playerProfileRepository.findAllById(memberIds);
            if (q.length() >= 2) {
                String needle = q.toLowerCase();
                profiles = profiles.stream()
                        .filter(profile -> contains(profile.getFirstName(), needle)
                                || contains(profile.getLastName(), needle)
                                || contains(profile.getDisplayName(), needle))
                        .toList();
            }
            int from = Math.min(limited.getPageNumber() * limited.getPageSize(), profiles.size());
            int to = Math.min(from + limited.getPageSize(), profiles.size());
            return new PageImpl<>(toResponses(profiles.subList(from, to)), limited, profiles.size());
        }
        Page<PlayerProfile> page = playerProfileRepository
                .findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                        q, q, q, limited);
        return new PageImpl<>(toResponses(page.getContent()), limited, page.getTotalElements());
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
                avatarOf(profile),
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
        if (hasText(request.avatarUrl())) {
            profile.setAvatarUrl(request.avatarUrl().trim());
        }
        profile.setJerseyNumber(request.jerseyNumber());
        profile.setPosition(request.position());
        profile.setBio(request.bio());
    }

    private List<PlayerProfileResponse> toResponses(List<PlayerProfile> profiles) {
        Set<UUID> userIds = profiles.stream()
                .filter(profile -> !hasText(profile.getAvatarUrl()))
                .map(PlayerProfile::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, String> photos = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .filter(user -> hasText(user.getPhotoUrl()))
                        .collect(Collectors.toMap(User::getId, User::getPhotoUrl, (a, b) -> a));
        return profiles.stream()
                .map(profile -> toResponse(profile, photos.get(profile.getUserId()), true))
                .toList();
    }

    private PlayerProfileResponse toResponse(PlayerProfile profile) {
        return toResponse(profile, null, false);
    }

    private PlayerProfileResponse toResponse(PlayerProfile profile, String fallbackPhoto, boolean photoResolved) {
        String avatar = hasText(profile.getAvatarUrl()) ? profile.getAvatarUrl() : fallbackPhoto;
        if (!photoResolved && !hasText(avatar)) {
            avatar = avatarOf(profile);
        }
        return new PlayerProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getDisplayName(),
                profile.getDateOfBirth(),
                avatar,
                profile.getJerseyNumber(),
                profile.getPosition(),
                profile.getBio()
        );
    }

    private String avatarOf(PlayerProfile profile) {
        if (hasText(profile.getAvatarUrl())) {
            return profile.getAvatarUrl();
        }
        return userRepository.findById(profile.getUserId()).map(User::getPhotoUrl).orElse(null);
    }

    /**
     * Name-only or number-only edits of an existing profile stay as they are.
     * Shirt name together with number and position is the player-registration form.
     */
    private static boolean completeRegistration(PlayerProfileRequest request) {
        return hasText(request.firstName())
                && hasText(request.lastName())
                && hasText(request.displayName())
                && request.jerseyNumber() != null
                && hasText(request.position());
    }

    private static void requirePlayerRegistration(PlayerProfileRequest request, PlayerProfile existing, User user) {
        if (!hasText(request.firstName()) || !hasText(request.lastName())) {
            throw ApiException.badRequest("Укажите имя и фамилию");
        }
        if (!hasText(request.displayName())) {
            throw ApiException.badRequest("Укажите, как писать имя на майке");
        }
        if (request.jerseyNumber() == null || request.jerseyNumber() < 0 || request.jerseyNumber() > 99) {
            throw ApiException.badRequest("Укажите номер на майке");
        }
        if (!hasText(request.position())) {
            throw ApiException.badRequest("Выберите позицию");
        }
        if (uploadedPhoto(request.avatarUrl(), existing == null ? null : existing.getAvatarUrl(), user) == null) {
            throw ApiException.badRequest("Для регистрации игрока нужна фотография");
        }
    }

    /**
     * Google profile pictures are https URLs. A player photo is a file stored by the upload API under /media/.
     */
    private static String uploadedPhoto(String requested, String existing, User user) {
        if (isUploadedPhoto(requested)) {
            return requested.trim();
        }
        if (isUploadedPhoto(existing)) {
            return existing.trim();
        }
        if (user != null && isUploadedPhoto(user.getPhotoUrl())) {
            return user.getPhotoUrl().trim();
        }
        return null;
    }

    static boolean isUploadedPhoto(String url) {
        return url != null && url.contains("/media/");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
