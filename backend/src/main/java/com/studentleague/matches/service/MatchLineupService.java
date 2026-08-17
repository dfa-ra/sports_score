package com.studentleague.matches.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.dto.MatchLineupPlayerResponse;
import com.studentleague.matches.dto.MatchLineupsResponse;
import com.studentleague.matches.dto.MatchTeamLineupResponse;
import com.studentleague.matches.dto.SetMatchLineupRequest;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchLineupPlayer;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.matches.repository.MatchRefereeRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.security.UserPrincipal;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.entity.TeamMember;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.users.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MatchLineupService {

    private final MatchRepository matchRepository;
    private final MatchLineupPlayerRepository lineupRepository;
    private final MatchRefereeRepository matchRefereeRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PlayerProfileRepository playerProfileRepository;

    public MatchLineupService(
            MatchRepository matchRepository,
            MatchLineupPlayerRepository lineupRepository,
            MatchRefereeRepository matchRefereeRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            PlayerProfileRepository playerProfileRepository
    ) {
        this.matchRepository = matchRepository;
        this.lineupRepository = lineupRepository;
        this.matchRefereeRepository = matchRefereeRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.playerProfileRepository = playerProfileRepository;
    }

    @Transactional(readOnly = true)
    public MatchLineupsResponse getLineups(UUID matchId) {
        Match match = requireMatch(matchId);
        return new MatchLineupsResponse(
                teamLineup(match, match.getHomeTeamId()),
                teamLineup(match, match.getAwayTeamId())
        );
    }

    @Transactional
    public MatchLineupsResponse setLineup(UserPrincipal principal, UUID matchId, SetMatchLineupRequest request) {
        Match match = requireMatch(matchId);
        if (!request.teamId().equals(match.getHomeTeamId()) && !request.teamId().equals(match.getAwayTeamId())) {
            throw ApiException.badRequest("Команда не играет в этом матче");
        }
        assertCanSetLineup(principal, match, request.teamId());

        List<UUID> starters = request.starterPlayerIds().stream().distinct().toList();
        List<UUID> bench = request.benchPlayerIds() == null
                ? remainingRoster(request.teamId(), starters)
                : request.benchPlayerIds().stream().distinct().toList();

        Set<UUID> seen = new HashSet<>();
        List<UUID> ordered = new ArrayList<>();
        for (UUID playerId : starters) {
            if (seen.add(playerId)) {
                ordered.add(playerId);
            }
        }
        for (UUID playerId : bench) {
            if (seen.add(playerId)) {
                ordered.add(playerId);
            }
        }

        for (UUID playerId : ordered) {
            if (!teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                    request.teamId(), playerId, TeamMemberStatus.ACTIVE)) {
                throw ApiException.badRequest("В старте или на скамейке только игроки из заявки команды");
            }
        }

        lineupRepository.deleteByMatchIdAndTeamId(matchId, request.teamId());
        int order = 0;
        for (UUID playerId : starters) {
            saveRow(matchId, request.teamId(), playerId, true, order++);
        }
        for (UUID playerId : bench) {
            if (starters.contains(playerId)) {
                continue;
            }
            saveRow(matchId, request.teamId(), playerId, false, order++);
        }
        return getLineups(matchId);
    }

    private MatchTeamLineupResponse teamLineup(Match match, UUID teamId) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> ApiException.notFound("Team not found"));
        boolean confirmed = lineupRepository.existsByMatchIdAndTeamId(match.getId(), teamId);
        List<MatchLineupPlayerResponse> players;
        if (confirmed) {
            players = lineupRepository.findByMatchIdAndTeamId(match.getId(), teamId).stream()
                    .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                    .map(this::toPlayer)
                    .toList();
        } else {
            players = teamMemberRepository.findByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE).stream()
                    .map(member -> toPlayer(member.getPlayerId(), false, 0))
                    .toList();
        }
        return new MatchTeamLineupResponse(
                teamId,
                team.getName(),
                team.getCaptainId(),
                confirmed,
                players.stream().filter(MatchLineupPlayerResponse::starter).toList(),
                players.stream().filter(player -> !player.starter()).toList()
        );
    }

    private List<UUID> remainingRoster(UUID teamId, List<UUID> starters) {
        Set<UUID> starterSet = new HashSet<>(starters);
        return teamMemberRepository.findByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE).stream()
                .map(TeamMember::getPlayerId)
                .filter(id -> !starterSet.contains(id))
                .toList();
    }

    private void saveRow(UUID matchId, UUID teamId, UUID playerId, boolean starter, int sortOrder) {
        MatchLineupPlayer row = new MatchLineupPlayer();
        row.setMatchId(matchId);
        row.setTeamId(teamId);
        row.setPlayerId(playerId);
        row.setStarter(starter);
        row.setSortOrder(sortOrder);
        lineupRepository.save(row);
    }

    private MatchLineupPlayerResponse toPlayer(MatchLineupPlayer row) {
        return toPlayer(row.getPlayerId(), row.isStarter(), row.getSortOrder());
    }

    private MatchLineupPlayerResponse toPlayer(UUID playerId, boolean starter, int sortOrder) {
        PlayerProfile profile = playerProfileRepository.findById(playerId).orElse(null);
        return new MatchLineupPlayerResponse(
                playerId,
                MatchMapper.displayName(profile),
                profile == null ? null : profile.getJerseyNumber(),
                profile == null ? null : profile.getPosition(),
                starter,
                sortOrder
        );
    }

    private void assertCanSetLineup(UserPrincipal principal, Match match, UUID teamId) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (principal.getRole() == Role.REFEREE
                && matchRefereeRepository.existsByMatchIdAndRefereeId(match.getId(), principal.getId())) {
            return;
        }
        PlayerProfile profile = playerProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> ApiException.forbidden("Состав на матч ставит капитан, судья или админ"));
        Team team = teamRepository.findById(teamId).orElseThrow(() -> ApiException.notFound("Team not found"));
        if (!profile.getId().equals(team.getCaptainId())) {
            throw ApiException.forbidden("Только капитан этой команды может написать стартовый состав");
        }
    }

    private Match requireMatch(UUID matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found"));
    }
}
