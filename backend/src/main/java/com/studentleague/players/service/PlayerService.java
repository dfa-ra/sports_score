package com.studentleague.players.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.players.dto.PlayerCardResponse;
import com.studentleague.players.dto.PlayerProfileRequest;
import com.studentleague.players.dto.PlayerProfileResponse;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.statistics.dto.PlayerStatisticsResponse;
import com.studentleague.statistics.service.StatisticsService;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.entity.TeamMember;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PlayerService {

    private final PlayerProfileRepository playerProfileRepository;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final StatisticsService statisticsService;
    private final MatchEventRepository matchEventRepository;
    private final MatchRepository matchRepository;

    public PlayerService(
            PlayerProfileRepository playerProfileRepository,
            UserRepository userRepository,
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository,
            StatisticsService statisticsService,
            MatchEventRepository matchEventRepository,
            MatchRepository matchRepository
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.statisticsService = statisticsService;
        this.matchEventRepository = matchEventRepository;
        this.matchRepository = matchRepository;
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
        Map<String, Object> statistics = new HashMap<>();
        List<PlayerStatisticsResponse> stats = statisticsService.playerStatistics(null, null, null, playerId);
        if (!stats.isEmpty()) {
            PlayerStatisticsResponse s = stats.getFirst();
            statistics.put("goals", s.goals());
            statistics.put("assists", s.assists());
            statistics.put("yellowCards", s.yellowCards());
            statistics.put("redCards", s.redCards());
            statistics.put("appearances", s.appearances());
        }

        List<PlayerCardResponse.MatchHistoryItem> history = buildMatchHistory(playerId);

        return new PlayerCardResponse(
                profile.getId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                profile.getJerseyNumber(),
                profile.getPosition(),
                teamSummary,
                statistics,
                history
        );
    }

    private List<PlayerCardResponse.MatchHistoryItem> buildMatchHistory(UUID playerId) {
        List<MatchEvent> events = matchEventRepository.findByPlayerIdAndVoidedFalseOrderByTimestampDesc(playerId);
        Set<UUID> seen = new LinkedHashSet<>();
        for (MatchEvent event : events) {
            seen.add(event.getMatchId());
            if (seen.size() >= 20) {
                break;
            }
        }
        List<PlayerCardResponse.MatchHistoryItem> history = new ArrayList<>();
        for (UUID matchId : seen) {
            Match match = matchRepository.findById(matchId).orElse(null);
            if (match == null || match.getStatus() != MatchStatus.FINISHED) {
                continue;
            }
            Team home = teamRepository.findById(match.getHomeTeamId()).orElse(null);
            Team away = teamRepository.findById(match.getAwayTeamId()).orElse(null);
            String opponentName = (home == null ? "?" : home.getName()) + " vs " + (away == null ? "?" : away.getName());
            history.add(new PlayerCardResponse.MatchHistoryItem(
                    match.getId(),
                    opponentName,
                    match.getHomeScore(),
                    match.getAwayScore(),
                    match.getStatus().name()
            ));
        }
        return history;
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
