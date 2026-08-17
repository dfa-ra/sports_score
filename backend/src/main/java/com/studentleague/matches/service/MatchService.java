package com.studentleague.matches.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.AssignRefereeRequest;
import com.studentleague.matches.dto.CreateMatchRequest;
import com.studentleague.matches.dto.MatchRefereeResponse;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchReferee;
import com.studentleague.matches.repository.MatchRefereeRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.notifications.NotificationEventType;
import com.studentleague.notifications.NotificationService;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchRefereeRepository matchRefereeRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public MatchService(
            MatchRepository matchRepository,
            MatchRefereeRepository matchRefereeRepository,
            TournamentRepository tournamentRepository,
            TournamentTeamRepository tournamentTeamRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.matchRepository = matchRepository;
        this.matchRefereeRepository = matchRefereeRepository;
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public MatchResponse create(CreateMatchRequest request) {
        if (request.homeTeamId().equals(request.awayTeamId())) {
            throw ApiException.badRequest("Home and away teams must differ");
        }
        Tournament tournament = tournamentRepository.findById(request.tournamentId())
                .orElseThrow(() -> ApiException.notFound("Tournament not found"));

        requireApproved(request.tournamentId(), request.homeTeamId());
        requireApproved(request.tournamentId(), request.awayTeamId());

        Match match = new Match();
        match.setTournamentId(tournament.getId());
        match.setSportId(tournament.getSportId());
        match.setHomeTeamId(request.homeTeamId());
        match.setAwayTeamId(request.awayTeamId());
        match.setScheduledAt(request.scheduledAt());
        match.setStatus(MatchStatus.SCHEDULED);
        match.setHomeScore(0);
        match.setAwayScore(0);
        Match saved = matchRepository.save(match);
        notificationService.publishToUser(
                null,
                NotificationEventType.SCHEDULE_UPDATE,
                "Match scheduled",
                "A new match was scheduled in the tournament",
                Map.of(
                        "matchId", saved.getId().toString(),
                        "tournamentId", tournament.getId().toString()
                )
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MatchResponse get(UUID id) {
        return toResponse(requireMatch(id));
    }

    @Transactional(readOnly = true)
    public Page<MatchResponse> list(UUID tournamentId, MatchStatus status, Pageable pageable) {
        Page<Match> page;
        if (tournamentId != null && status != null) {
            page = matchRepository.findByTournamentIdAndStatus(tournamentId, status, pageable);
        } else if (tournamentId != null) {
            page = matchRepository.findByTournamentId(tournamentId, pageable);
        } else if (status != null) {
            page = matchRepository.findByStatus(status, pageable);
        } else {
            page = matchRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional
    public MatchRefereeResponse assignReferee(UUID matchId, AssignRefereeRequest request) {
        requireMatch(matchId);
        User referee = userRepository.findById(request.refereeId())
                .orElseThrow(() -> ApiException.notFound("Referee user not found"));
        if (referee.getRole() != Role.REFEREE && referee.getRole() != Role.ADMIN) {
            throw ApiException.badRequest("User must have REFEREE role");
        }
        if (matchRefereeRepository.existsByMatchIdAndRefereeId(matchId, referee.getId())) {
            throw ApiException.conflict("Referee already assigned to this match");
        }
        MatchReferee assignment = new MatchReferee();
        assignment.setMatchId(matchId);
        assignment.setRefereeId(referee.getId());
        matchRefereeRepository.save(assignment);
        notificationService.publishToUser(
                referee.getId(),
                NotificationEventType.SCHEDULE_UPDATE,
                "Match assignment",
                "You were assigned to a match",
                Map.of("matchId", matchId.toString(), "refereeId", referee.getId().toString())
        );
        return new MatchRefereeResponse(
                assignment.getId(), assignment.getMatchId(), assignment.getRefereeId(), assignment.getAssignedAt());
    }

    @Transactional(readOnly = true)
    public List<MatchRefereeResponse> listReferees(UUID matchId) {
        requireMatch(matchId);
        return matchRefereeRepository.findByMatchId(matchId).stream()
                .map(a -> new MatchRefereeResponse(a.getId(), a.getMatchId(), a.getRefereeId(), a.getAssignedAt()))
                .toList();
    }

    private void requireApproved(UUID tournamentId, UUID teamId) {
        var entry = tournamentTeamRepository.findByTournamentIdAndTeamId(tournamentId, teamId)
                .orElseThrow(() -> ApiException.badRequest("Team is not registered in the tournament"));
        if (entry.getStatus() != TournamentTeamStatus.APPROVED) {
            throw ApiException.badRequest("Team must be approved before scheduling matches");
        }
    }

    private Match requireMatch(UUID id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Match not found"));
    }

    private MatchResponse toResponse(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getTournamentId(),
                match.getSportId(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getScheduledAt(),
                match.getStartedAt(),
                match.getFinishedAt(),
                match.getStatus(),
                match.getHomeScore(),
                match.getAwayScore(),
                match.getGameTimeSeconds(),
                match.getPeriod()
        );
    }
}
