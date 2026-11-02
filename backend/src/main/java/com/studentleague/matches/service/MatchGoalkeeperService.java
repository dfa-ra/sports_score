package com.studentleague.matches.service;

import com.studentleague.cache.StandingsBoardCache;
import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.dto.SetMatchGoalkeeperRequest;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchLineupPlayer;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.matches.repository.MatchRefereeRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.security.UserPrincipal;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.users.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

@Service
public class MatchGoalkeeperService {

    private static final EnumSet<MatchStatus> OPENABLE = EnumSet.of(
            MatchStatus.SCHEDULED,
            MatchStatus.LIVE,
            MatchStatus.PAUSED,
            MatchStatus.FINISHED
    );

    private final MatchRepository matchRepository;
    private final MatchRefereeRepository matchRefereeRepository;
    private final MatchLineupPlayerRepository lineupRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final MatchMapper matchMapper;
    private final StandingsBoardCache standingsBoardCache;

    public MatchGoalkeeperService(
            MatchRepository matchRepository,
            MatchRefereeRepository matchRefereeRepository,
            MatchLineupPlayerRepository lineupRepository,
            TeamMemberRepository teamMemberRepository,
            MatchMapper matchMapper,
            StandingsBoardCache standingsBoardCache
    ) {
        this.matchRepository = matchRepository;
        this.matchRefereeRepository = matchRefereeRepository;
        this.lineupRepository = lineupRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.matchMapper = matchMapper;
        this.standingsBoardCache = standingsBoardCache;
    }

    @Transactional
    public MatchResponse setByReferee(UserPrincipal principal, UUID matchId, SetMatchGoalkeeperRequest request) {
        if (!principal.hasAnyRole(Role.REFEREE, Role.ADMIN)) {
            throw ApiException.forbidden("Referee role required");
        }
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found"));
        boolean assigned = matchRefereeRepository.existsByMatchIdAndRefereeId(matchId, principal.getId());
        if (!assigned && !principal.hasRole(Role.ADMIN)) {
            throw ApiException.forbidden("Referee is not assigned to this match");
        }
        requireTeam(request);
        return apply(match, request);
    }

    @Transactional
    public MatchResponse setByAdmin(UUID matchId, SetMatchGoalkeeperRequest request) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found"));
        requireTeam(request);
        return apply(match, request);
    }

    private static void requireTeam(SetMatchGoalkeeperRequest request) {
        if (request == null || request.teamId() == null) {
            throw ApiException.badRequest("Укажите команду");
        }
    }

    private MatchResponse apply(Match match, SetMatchGoalkeeperRequest request) {
        if (!OPENABLE.contains(match.getStatus())) {
            throw ApiException.conflict("В этом матче вратаря уже не выбрать");
        }
        UUID teamId = request.teamId();
        if (!teamId.equals(match.getHomeTeamId()) && !teamId.equals(match.getAwayTeamId())) {
            throw ApiException.badRequest("Укажите команду этого матча");
        }
        UUID playerId = request.playerId();
        if (playerId != null && !onTeam(match, teamId, playerId)) {
            throw ApiException.badRequest("Вратарь должен быть в составе этой команды");
        }
        if (teamId.equals(match.getHomeTeamId())) {
            match.setHomeGoalkeeperId(playerId);
        } else {
            match.setAwayGoalkeeperId(playerId);
        }
        standingsBoardCache.invalidateTournament(match.getTournamentId());
        return matchMapper.toResponse(matchRepository.save(match));
    }

    /**
     * A submitted lineup limits the pick to those players.
     * A side that never sent a lineup falls back to its active squad.
     */
    private boolean onTeam(Match match, UUID teamId, UUID playerId) {
        if (lineupRepository.existsByMatchIdAndTeamId(match.getId(), teamId)) {
            return lineupRepository.findByMatchIdAndTeamId(match.getId(), teamId).stream()
                    .map(MatchLineupPlayer::getPlayerId)
                    .anyMatch(playerId::equals);
        }
        return teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                teamId, playerId, TeamMemberStatus.ACTIVE);
    }
}
