package com.studentleague.matches.service;

import com.studentleague.cache.StandingsBoardCache;
import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.clock.MatchClock;
import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.CreateMatchEventRequest;
import com.studentleague.matches.dto.MatchEventResponse;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.futsal.FutsalBoardCalculator;
import com.studentleague.matches.live.LiveMatchPublisher;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchRefereeRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.notifications.NotificationService;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.security.UserPrincipal;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.users.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RefereeMatchService {

    private static final EnumSet<MatchStatus> EVENT_ALLOWED =
            EnumSet.of(MatchStatus.LIVE, MatchStatus.PAUSED);

    private final MatchRepository matchRepository;
    private final MatchRefereeRepository matchRefereeRepository;
    private final MatchEventRepository matchEventRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final MatchScoreService matchScoreService;
    private final MatchService matchService;
    private final LiveMatchPublisher liveMatchPublisher;
    private final NotificationService notificationService;
    private final MatchMapper matchMapper;
    private final StandingsBoardCache standingsBoardCache;

    public RefereeMatchService(
            MatchRepository matchRepository,
            MatchRefereeRepository matchRefereeRepository,
            MatchEventRepository matchEventRepository,
            TeamMemberRepository teamMemberRepository,
            PlayerProfileRepository playerProfileRepository,
            MatchScoreService matchScoreService,
            MatchService matchService,
            LiveMatchPublisher liveMatchPublisher,
            NotificationService notificationService,
            MatchMapper matchMapper,
            StandingsBoardCache standingsBoardCache
    ) {
        this.matchRepository = matchRepository;
        this.matchRefereeRepository = matchRefereeRepository;
        this.matchEventRepository = matchEventRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.matchScoreService = matchScoreService;
        this.matchService = matchService;
        this.liveMatchPublisher = liveMatchPublisher;
        this.notificationService = notificationService;
        this.matchMapper = matchMapper;
        this.standingsBoardCache = standingsBoardCache;
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
        Instant now = Instant.now();
        match.setStatus(MatchStatus.LIVE);
        match.setStartedAt(now);
        match.setGameTimeSeconds(0);
        match.setPeriod(1);
        MatchClock.startRunning(match, now);
        recordPeriodEvent(match, MatchEventType.PERIOD_START, now);
        MatchResponse response = toResponse(matchRepository.save(match));
        liveMatchPublisher.publishMatchUpdate(response, null, "MATCH_STARTED");
        notificationService.publishToUser(
                principal.getId(),
                com.studentleague.notifications.NotificationEventType.MATCH_STARTING,
                "Match started",
                "Your assigned match is now live",
                java.util.Map.of("matchId", matchId.toString())
        );
        return response;
    }

    @Transactional
    public MatchResponse pause(UserPrincipal principal, UUID matchId) {
        Match match = requireAssignedMatch(principal, matchId);
        if (match.getStatus() != MatchStatus.LIVE) {
            throw ApiException.badRequest("Only LIVE matches can be paused");
        }
        MatchClock.freeze(match, Instant.now());
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
        MatchClock.startRunning(match, Instant.now());
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
        Instant now = Instant.now();
        MatchClock.freeze(match, now);
        recalculateScore(match);
        match.setStatus(MatchStatus.FINISHED);
        match.setFinishedAt(now);
        MatchResponse response = toResponse(matchRepository.save(match));
        liveMatchPublisher.publishMatchUpdate(response, null, "MATCH_FINISHED");
        notificationService.publishToUser(
                principal.getId(),
                com.studentleague.notifications.NotificationEventType.MATCH_FINISHED,
                "Match finished",
                "Final score " + response.homeScore() + ":" + response.awayScore(),
                java.util.Map.of("matchId", matchId.toString())
        );
        return response;
    }

    @Transactional
    public MatchResponse nextPeriod(UserPrincipal principal, UUID matchId) {
        Match match = requireAssignedMatch(principal, matchId);
        if (match.getStatus() != MatchStatus.LIVE && match.getStatus() != MatchStatus.PAUSED) {
            throw ApiException.badRequest("Тайм можно сменить только в живом матче");
        }
        int current = match.getPeriod() == null ? 1 : match.getPeriod();
        if (current >= match.getPeriodCount()) {
            throw ApiException.badRequest("Это последний тайм — можно заканчивать матч");
        }
        Instant now = Instant.now();
        MatchClock.freeze(match, now);
        recordPeriodEvent(match, MatchEventType.PERIOD_END, now);
        match.setPeriod(current + 1);
        match.setGameTimeSeconds(0);
        if (match.getStatus() == MatchStatus.LIVE) {
            MatchClock.startRunning(match, now);
        }
        recordPeriodEvent(match, MatchEventType.PERIOD_START, now);
        MatchResponse response = toResponse(matchRepository.save(match));
        liveMatchPublisher.publishMatchUpdate(response, null, "PERIOD_CHANGED");
        return response;
    }

    @Transactional
    public MatchEventResponse addEvent(UserPrincipal principal, UUID matchId, CreateMatchEventRequest request) {
        Match match = requireAssignedMatch(principal, matchId);
        if (match.getStatus() == MatchStatus.FINISHED) {
            if (!principal.hasRole(Role.ADMIN)) {
                throw ApiException.forbidden("После окончания матча протокол правит только админ");
            }
        } else if (!EVENT_ALLOWED.contains(match.getStatus())) {
            throw ApiException.badRequest("Events can only be added while match is LIVE or PAUSED");
        }
        validateEventPayload(match, request);

        Instant now = Instant.now();
        int period = MatchClock.periodIndex(match);
        int length = MatchClock.periodLength(match);
        // The pad may omit gameTime. The hall clock counts down, but we store
        // seconds from kickoff. An explicit gameTime is the old within-period
        // count-up (or already kickoff seconds when it is past one period).
        int kickoff = request.gameTime() != null
                ? MatchClock.toKickoffSeconds(request.gameTime(), period, length)
                : MatchClock.kickoffSeconds(match, now);
        MatchEvent event = new MatchEvent();
        event.setMatchId(matchId);
        event.setEventType(request.eventType());
        event.setTimestamp(now);
        event.setGameTime(kickoff);
        event.setPeriod(match.getPeriod());
        event.setTeamId(request.teamId());
        event.setPlayerId(request.playerId());
        event.setSecondaryPlayerId(request.secondaryPlayerId());
        event.setMetadata(stampMetadata(match, request));
        event.setVoided(false);
        matchEventRepository.save(event);

        recalculateScore(match);
        MatchResponse matchResponse = toResponse(matchRepository.save(match));
        MatchEventResponse eventResponse = toEventResponse(event);
        liveMatchPublisher.publishMatchUpdate(matchResponse, eventResponse, "MATCH_EVENT");
        if (request.eventType() == com.studentleague.matches.domain.MatchEventType.GOAL) {
            notificationService.publishToUser(
                    principal.getId(),
                    com.studentleague.notifications.NotificationEventType.GOAL,
                    "Goal!",
                    "A goal was scored",
                    java.util.Map.of("matchId", matchId.toString())
            );
        }
        return eventResponse;
    }

    @Transactional
    public MatchEventResponse voidEvent(UserPrincipal principal, UUID matchId, UUID eventId) {
        Match match = requireAssignedMatch(principal, matchId);
        if (match.getStatus() == MatchStatus.FINISHED) {
            if (!principal.hasRole(Role.ADMIN)) {
                throw ApiException.forbidden("После окончания матча протокол правит только админ");
            }
        } else if (!EVENT_ALLOWED.contains(match.getStatus())) {
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
        matchScoreService.apply(match);
    }

    private void validateEventPayload(Match match, CreateMatchEventRequest request) {
        if (request.teamId() != null
                && !request.teamId().equals(match.getHomeTeamId())
                && !request.teamId().equals(match.getAwayTeamId())) {
            throw ApiException.badRequest("teamId must be home or away team of the match");
        }
        if ((request.eventType() == MatchEventType.FOUL || request.eventType() == MatchEventType.TIMEOUT)
                && request.teamId() == null) {
            throw ApiException.badRequest("Укажите команду");
        }
        boolean needsPlayer = request.eventType() == MatchEventType.GOAL
                || request.eventType() == MatchEventType.ASSIST
                || request.eventType() == MatchEventType.YELLOW_CARD
                || request.eventType() == MatchEventType.RED_CARD
                || request.eventType() == MatchEventType.SUBSTITUTION
                || request.eventType() == MatchEventType.POINT;
        if (needsPlayer && request.playerId() == null) {
            throw ApiException.badRequest("Выберите игрока для этого события");
        }
        if (request.eventType() == MatchEventType.SUBSTITUTION && request.secondaryPlayerId() == null) {
            throw ApiException.badRequest("Для замены нужен игрок, который выходит");
        }
        if (request.playerId() != null) {
            playerProfileRepository.findById(request.playerId())
                    .orElseThrow(() -> ApiException.notFound("Player not found"));
            if (request.teamId() != null
                    && !teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                    request.teamId(), request.playerId(), TeamMemberStatus.ACTIVE)) {
                throw ApiException.badRequest("Игрок не в заявке этой команды");
            }
        }
        if (request.secondaryPlayerId() != null) {
            playerProfileRepository.findById(request.secondaryPlayerId())
                    .orElseThrow(() -> ApiException.notFound("Secondary player not found"));
            if (request.teamId() != null
                    && !teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                    request.teamId(), request.secondaryPlayerId(), TeamMemberStatus.ACTIVE)) {
                throw ApiException.badRequest("Второй игрок не в заявке этой команды");
            }
            if (request.playerId() != null && request.playerId().equals(request.secondaryPlayerId())) {
                throw ApiException.badRequest("Это должен быть другой человек");
            }
        }
    }

    private Map<String, Object> stampMetadata(Match match, CreateMatchEventRequest request) {
        if (request.eventType() != MatchEventType.FOUL && request.eventType() != MatchEventType.TIMEOUT) {
            return request.metadata();
        }
        List<MatchEvent> active = matchEventRepository.findByMatchIdAndVoidedFalseOrderByTimestampAsc(match.getId());
        if (request.eventType() == MatchEventType.TIMEOUT) {
            if (FutsalBoardCalculator.timeoutUsed(active, request.teamId(), match)) {
                throw ApiException.conflict(FutsalBoardCalculator.timeoutRejectedMessage(match));
            }
            return request.metadata();
        }
        return FutsalBoardCalculator.foulMetadata(request.metadata(), active, request.teamId(), match);
    }

    private void recordPeriodEvent(Match match, MatchEventType type, Instant now) {
        MatchEvent event = new MatchEvent();
        event.setMatchId(match.getId());
        event.setEventType(type);
        event.setTimestamp(now);
        event.setGameTime(MatchClock.kickoffSeconds(match, now));
        event.setPeriod(match.getPeriod());
        event.setVoided(false);
        matchEventRepository.save(event);
        standingsBoardCache.invalidateTournament(match.getTournamentId());
    }

    private Match requireAssignedMatch(UserPrincipal principal, UUID matchId) {
        assertReferee(principal);
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found"));
        boolean assigned = matchRefereeRepository.existsByMatchIdAndRefereeId(matchId, principal.getId());
        if (!assigned && !principal.hasRole(Role.ADMIN)) {
            throw ApiException.forbidden("Referee is not assigned to this match");
        }
        return match;
    }

    private void assertReferee(UserPrincipal principal) {
        if (!principal.hasAnyRole(Role.REFEREE, Role.ADMIN)) {
            throw ApiException.forbidden("Referee role required");
        }
    }

    private MatchResponse toResponse(Match match) {
        return matchMapper.toResponse(match);
    }

    private MatchEventResponse toEventResponse(MatchEvent event) {
        return matchMapper.toEventResponse(event);
    }
}
