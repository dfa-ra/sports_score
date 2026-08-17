package com.studentleague.matches.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.CreateMatchEventRequest;
import com.studentleague.matches.dto.MatchEventResponse;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.live.LiveMatchPublisher;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchRefereeRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.matches.scoring.ScorePolicyRegistry;
import com.studentleague.matches.scoring.ScoreSnapshot;
import com.studentleague.notifications.NotificationService;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.security.UserPrincipal;
import com.studentleague.sports.entity.Sport;
import com.studentleague.sports.repository.SportRepository;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.users.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class RefereeMatchService {

    private static final EnumSet<MatchStatus> EVENT_ALLOWED =
            EnumSet.of(MatchStatus.LIVE, MatchStatus.PAUSED);

    private final MatchRepository matchRepository;
    private final MatchRefereeRepository matchRefereeRepository;
    private final MatchEventRepository matchEventRepository;
    private final SportRepository sportRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final ScorePolicyRegistry scorePolicyRegistry;
    private final MatchService matchService;
    private final LiveMatchPublisher liveMatchPublisher;
    private final NotificationService notificationService;

    public RefereeMatchService(
            MatchRepository matchRepository,
            MatchRefereeRepository matchRefereeRepository,
            MatchEventRepository matchEventRepository,
            SportRepository sportRepository,
            TeamMemberRepository teamMemberRepository,
            PlayerProfileRepository playerProfileRepository,
            ScorePolicyRegistry scorePolicyRegistry,
            MatchService matchService,
            LiveMatchPublisher liveMatchPublisher,
            NotificationService notificationService
    ) {
        this.matchRepository = matchRepository;
        this.matchRefereeRepository = matchRefereeRepository;
        this.matchEventRepository = matchEventRepository;
        this.sportRepository = sportRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.scorePolicyRegistry = scorePolicyRegistry;
        this.matchService = matchService;
        this.liveMatchPublisher = liveMatchPublisher;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> assignedMatches(UserPrincipal principal) {
        assertReferee(principal);
        return matchRefereeRepository.findByRefereeId(principal.getId()).stream()
                .map(assignment -> matchService.get(assignment.getMatchId()))
                .toList();
    }

    @Transactional
    public MatchResponse start(UserPrincipal principal, UUID matchId) {
        Match match = requireAssignedMatch(principal, matchId);
        if (match.getStatus() != MatchStatus.SCHEDULED) {
            throw ApiException.badRequest("Only SCHEDULED matches can be started");
        }
        match.setStatus(MatchStatus.LIVE);
        match.setStartedAt(Instant.now());
        match.setGameTimeSeconds(0);
        match.setPeriod(1);
        MatchResponse response = toResponse(matchRepository.save(match));
        liveMatchPublisher.publishMatchUpdate(response, null, "MATCH_STARTED");
        return response;
    }

    @Transactional
    public MatchResponse pause(UserPrincipal principal, UUID matchId) {
        Match match = requireAssignedMatch(principal, matchId);
        if (match.getStatus() != MatchStatus.LIVE) {
            throw ApiException.badRequest("Only LIVE matches can be paused");
        }
        match.setStatus(MatchStatus.PAUSED);
        MatchResponse response = toResponse(matchRepository.save(match));
        liveMatchPublisher.publishMatchUpdate(response, null, "MATCH_PAUSED");
        return response;
    }

    @Transactional
    public MatchResponse resume(UserPrincipal principal, UUID matchId) {
        Match match = requireAssignedMatch(principal, matchId);
        if (match.getStatus() != MatchStatus.PAUSED) {
            throw ApiException.badRequest("Only PAUSED matches can be resumed");
        }
        match.setStatus(MatchStatus.LIVE);
        MatchResponse response = toResponse(matchRepository.save(match));
        liveMatchPublisher.publishMatchUpdate(response, null, "MATCH_RESUMED");
        return response;
    }

    @Transactional
    public MatchResponse finish(UserPrincipal principal, UUID matchId) {
        Match match = requireAssignedMatch(principal, matchId);
        if (match.getStatus() != MatchStatus.LIVE && match.getStatus() != MatchStatus.PAUSED) {
            throw ApiException.badRequest("Only LIVE or PAUSED matches can be finished");
        }
        recalculateScore(match);
        match.setStatus(MatchStatus.FINISHED);
        match.setFinishedAt(Instant.now());
        MatchResponse response = toResponse(matchRepository.save(match));
        liveMatchPublisher.publishMatchUpdate(response, null, "MATCH_FINISHED");
        return response;
    }

    @Transactional
    public MatchEventResponse addEvent(UserPrincipal principal, UUID matchId, CreateMatchEventRequest request) {
        Match match = requireAssignedMatch(principal, matchId);
        if (!EVENT_ALLOWED.contains(match.getStatus())) {
            throw ApiException.badRequest("Events can only be added while match is LIVE or PAUSED");
        }
        validateEventPayload(match, request);

        MatchEvent event = new MatchEvent();
        event.setMatchId(matchId);
        event.setEventType(request.eventType());
        event.setTimestamp(Instant.now());
        event.setGameTime(request.gameTime() != null ? request.gameTime() : match.getGameTimeSeconds());
        event.setTeamId(request.teamId());
        event.setPlayerId(request.playerId());
        event.setSecondaryPlayerId(request.secondaryPlayerId());
        event.setMetadata(request.metadata());
        event.setVoided(false);
        matchEventRepository.save(event);

        recalculateScore(match);
        MatchResponse matchResponse = toResponse(matchRepository.save(match));
        MatchEventResponse eventResponse = toEventResponse(event);
        liveMatchPublisher.publishMatchUpdate(matchResponse, eventResponse, "MATCH_EVENT");
        if (request.eventType() == com.studentleague.matches.domain.MatchEventType.GOAL) {
            notificationService.publish(new com.studentleague.notifications.NotificationPayload(
                    com.studentleague.notifications.NotificationEventType.GOAL,
                    principal.getId(),
                    "Goal!",
                    "A goal was scored",
                    java.util.Map.of("matchId", matchId.toString())
            ));
        }
        return eventResponse;
    }

    @Transactional
    public MatchEventResponse voidEvent(UserPrincipal principal, UUID matchId, UUID eventId) {
        Match match = requireAssignedMatch(principal, matchId);
        if (!EVENT_ALLOWED.contains(match.getStatus()) && match.getStatus() != MatchStatus.FINISHED) {
            throw ApiException.badRequest("Cannot void events for this match status");
        }
        MatchEvent event = matchEventRepository.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("Match event not found"));
        if (!event.getMatchId().equals(matchId)) {
            throw ApiException.badRequest("Event does not belong to this match");
        }
        if (event.isVoided()) {
            throw ApiException.conflict("Event already voided");
        }
        event.setVoided(true);
        event.setVoidedAt(Instant.now());
        matchEventRepository.save(event);

        recalculateScore(match);
        MatchResponse matchResponse = toResponse(matchRepository.save(match));
        MatchEventResponse eventResponse = toEventResponse(event);
        liveMatchPublisher.publishMatchUpdate(matchResponse, eventResponse, "MATCH_EVENT_VOIDED");
        return eventResponse;
    }

    @Transactional(readOnly = true)
    public List<MatchEventResponse> listEvents(UUID matchId) {
        matchRepository.findById(matchId).orElseThrow(() -> ApiException.notFound("Match not found"));
        return matchEventRepository.findByMatchIdOrderByTimestampAsc(matchId).stream()
                .map(this::toEventResponse)
                .toList();
    }

    private void recalculateScore(Match match) {
        Sport sport = sportRepository.findById(match.getSportId())
                .orElseThrow(() -> ApiException.notFound("Sport not found"));
        List<MatchEvent> active = matchEventRepository.findByMatchIdAndVoidedFalseOrderByTimestampAsc(match.getId());
        ScoreSnapshot snapshot = scorePolicyRegistry.forSportCode(sport.getCode())
                .calculate(match.getHomeTeamId(), match.getAwayTeamId(), active);
        match.setHomeScore(snapshot.homeScore());
        match.setAwayScore(snapshot.awayScore());
    }

    private void validateEventPayload(Match match, CreateMatchEventRequest request) {
        if (request.teamId() != null
                && !request.teamId().equals(match.getHomeTeamId())
                && !request.teamId().equals(match.getAwayTeamId())) {
            throw ApiException.badRequest("teamId must be home or away team of the match");
        }
        if (request.playerId() != null) {
            playerProfileRepository.findById(request.playerId())
                    .orElseThrow(() -> ApiException.notFound("Player not found"));
            if (request.teamId() != null
                    && !teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                    request.teamId(), request.playerId(), TeamMemberStatus.ACTIVE)) {
                throw ApiException.badRequest("Player is not an active member of the selected team");
            }
        }
        if (request.secondaryPlayerId() != null) {
            playerProfileRepository.findById(request.secondaryPlayerId())
                    .orElseThrow(() -> ApiException.notFound("Secondary player not found"));
        }
    }

    private Match requireAssignedMatch(UserPrincipal principal, UUID matchId) {
        assertReferee(principal);
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found"));
        boolean assigned = matchRefereeRepository.existsByMatchIdAndRefereeId(matchId, principal.getId());
        if (!assigned && principal.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Referee is not assigned to this match");
        }
        return match;
    }

    private void assertReferee(UserPrincipal principal) {
        if (principal.getRole() != Role.REFEREE && principal.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Referee role required");
        }
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

    private MatchEventResponse toEventResponse(MatchEvent event) {
        return new MatchEventResponse(
                event.getId(),
                event.getMatchId(),
                event.getEventType(),
                event.getTimestamp(),
                event.getGameTime(),
                event.getTeamId(),
                event.getPlayerId(),
                event.getSecondaryPlayerId(),
                event.getMetadata(),
                event.isVoided(),
                event.getVoidedAt(),
                event.getCreatedAt()
        );
    }
}
