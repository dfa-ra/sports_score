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
import com.studentleague.matches.scoring.OwnGoals;
import com.studentleague.notifications.NotificationService;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.security.UserPrincipal;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.tournaments.domain.TournamentStatus;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.service.PlayoffService;
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
    private final PlayoffService playoffService;
    private final TournamentRepository tournamentRepository;
    private final PenaltyShootoutService penaltyShootoutService;

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
            StandingsBoardCache standingsBoardCache,
            PlayoffService playoffService,
            TournamentRepository tournamentRepository,
            PenaltyShootoutService penaltyShootoutService
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
        this.playoffService = playoffService;
        this.tournamentRepository = tournamentRepository;
        this.penaltyShootoutService = penaltyShootoutService;
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
            throw ApiException.badRequest("Начать можно только запланированный матч");
        }
        Instant now = Instant.now();
        match.setStatus(MatchStatus.LIVE);
        match.setStartedAt(now);
        match.setGameTimeSeconds(0);
        match.setPeriod(1);
        MatchClock.startRunning(match, now);
        recordPeriodEvent(match, MatchEventType.PERIOD_START, now);
        openTournamentIfRecruiting(match.getTournamentId());
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
            throw ApiException.badRequest("Паузу можно поставить только в живом матче");
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
            throw ApiException.badRequest("Продолжить можно только матч на паузе");
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
            throw ApiException.badRequest("Завершить можно только живой матч или матч на паузе");
        }
        Instant now = Instant.now();
        MatchClock.freeze(match, now);
        recalculateScore(match);
        penaltyShootoutService.requireWinner(match);
        match.setStatus(MatchStatus.FINISHED);
        match.setFinishedAt(now);
        matchRepository.save(match);
        playoffService.advance(match);
        MatchResponse response = toResponse(match);
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
        int boundary = MatchClock.kickoffSeconds(match, now);
        recordPeriodEvent(match, MatchEventType.PERIOD_END, now, boundary);
        match.setPeriod(current + 1);
        match.setGameTimeSeconds(0);
        if (match.getStatus() == MatchStatus.LIVE) {
            MatchClock.startRunning(match, now);
        }
        recordPeriodEvent(match, MatchEventType.PERIOD_START, now, boundary);
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
            throw ApiException.badRequest("События можно добавлять, пока матч идёт или стоит на паузе");
        }
        validateEventMinute(match, request);
        validateEventPayload(match, request);
        boolean ownGoal = OwnGoals.is(request.metadata());
        if (ownGoal && request.eventType() != MatchEventType.GOAL) {
            throw ApiException.badRequest("Автогол можно отметить только у гола");
        }

        Instant now = Instant.now();
        int period = MatchClock.periodIndex(match);
        int length = MatchClock.periodLength(match);
        // The pad may omit gameTime, or send 0 while the hall clock is already
        // running (the stored base stays 0 until pause). 0 is not a minute:
        // keep a real within-period time, and otherwise store seconds from kickoff.
        int live = MatchClock.kickoffSeconds(match, now);
        Integer requested = request.gameTime();
        int kickoff = requested == null || requested == 0
                ? live
                : MatchClock.toKickoffSeconds(requested, period, length);
        MatchEvent event = new MatchEvent();
        event.setMatchId(matchId);
        event.setEventType(request.eventType());
        event.setTimestamp(now);
        event.setGameTime(kickoff);
        event.setPeriod(match.getPeriod());
        event.setTeamId(request.teamId());
        event.setPlayerId(request.playerId());
        event.setSecondaryPlayerId(ownGoal ? null : request.secondaryPlayerId());
        Map<String, Object> metadata = stampMetadata(match, request);
        if (request.eventType() == MatchEventType.GOAL) {
            metadata = OwnGoals.apply(metadata, ownGoal);
        }
        event.setMetadata(metadata);
        event.setVoided(false);
        matchEventRepository.save(event);

        recalculateScore(match);
        matchRepository.save(match);
        if (match.getStatus() == MatchStatus.FINISHED) {
            playoffService.advance(match);
        }
        MatchResponse matchResponse = toResponse(match);
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
            throw ApiException.badRequest("В этом статусе матча события убирать нельзя");
        }
        MatchEvent event = matchEventRepository.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("Событие матча не найдено"));
        if (!event.getMatchId().equals(matchId)) {
            throw ApiException.badRequest("Событие не относится к этому матчу");
        }
        if (event.isVoided()) {
            throw ApiException.conflict("Событие уже убрано из протокола");
        }
        event.setVoided(true);
        event.setVoidedAt(Instant.now());
        matchEventRepository.save(event);

        recalculateScore(match);
        matchRepository.save(match);
        if (match.getStatus() == MatchStatus.FINISHED) {
            playoffService.advance(match);
        }
        MatchResponse matchResponse = toResponse(match);
        MatchEventResponse eventResponse = toEventResponse(event);
        liveMatchPublisher.publishMatchUpdate(matchResponse, eventResponse, "MATCH_EVENT_VOIDED");
        return eventResponse;
    }

    @Transactional(readOnly = true)
    public List<MatchEventResponse> listEvents(UUID matchId) {
        matchRepository.findById(matchId).orElseThrow(() -> ApiException.notFound("Матч не найден"));
        return matchEventRepository.findByMatchIdOrderByTimestampAsc(matchId).stream()
                .map(this::toEventResponse)
                .toList();
    }

    private void recalculateScore(Match match) {
        matchScoreService.apply(match);
    }

    private void validateEventMinute(Match match, CreateMatchEventRequest request) {
        Integer gameTime = request.gameTime();
        if (gameTime == null) {
            return;
        }
        if (gameTime < 0) {
            throw ApiException.badRequest("Время события не может быть отрицательным");
        }
        int length = MatchClock.periodLength(match);
        int maxSeconds = MatchClock.maxEventSeconds(match);
        int kickoff = MatchClock.toKickoffSeconds(gameTime, MatchClock.periodIndex(match), length);
        if (kickoff > maxSeconds) {
            throw ApiException.badRequest("Время события больше длительности матча");
        }
    }

    private void validateEventPayload(Match match, CreateMatchEventRequest request) {
        if (request.teamId() != null
                && !request.teamId().equals(match.getHomeTeamId())
                && !request.teamId().equals(match.getAwayTeamId())) {
            throw ApiException.badRequest("Укажите команду этого матча");
        }
        if ((request.eventType() == MatchEventType.FOUL
                || request.eventType() == MatchEventType.TIMEOUT
                || request.eventType() == MatchEventType.GOAL)
                && request.teamId() == null) {
            throw ApiException.badRequest("Укажите команду");
        }
        boolean card = request.eventType() == MatchEventType.YELLOW_CARD
                || request.eventType() == MatchEventType.RED_CARD;
        if (card && request.playerId() == null && request.teamId() == null) {
            throw ApiException.badRequest("Укажите команду или игрока");
        }
        boolean needsPlayer = request.eventType() == MatchEventType.ASSIST
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
                    .orElseThrow(() -> ApiException.notFound("Игрок не найден"));
            if (request.teamId() != null
                    && !teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                    request.teamId(), request.playerId(), TeamMemberStatus.ACTIVE)) {
                throw ApiException.badRequest("Игрок не в заявке этой команды");
            }
        }
        if (request.secondaryPlayerId() != null) {
            playerProfileRepository.findById(request.secondaryPlayerId())
                    .orElseThrow(() -> ApiException.notFound("Второй игрок не найден"));
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
        recordPeriodEvent(match, type, now, MatchClock.kickoffSeconds(match, now));
    }

    private void recordPeriodEvent(Match match, MatchEventType type, Instant now, int gameTime) {
        MatchEvent event = new MatchEvent();
        event.setMatchId(match.getId());
        event.setEventType(type);
        event.setTimestamp(now);
        event.setGameTime(gameTime);
        event.setPeriod(match.getPeriod());
        event.setVoided(false);
        matchEventRepository.save(event);
        standingsBoardCache.invalidateTournament(match.getTournamentId());
    }

    /**
     * A recruiting tournament becomes «Идёт» when its first match starts.
     * Finished, cancelled, draft, and already active tournaments stay as they are.
     */
    static TournamentStatus statusAfterMatchStarts(TournamentStatus status) {
        if (status == TournamentStatus.REGISTRATION) {
            return TournamentStatus.ACTIVE;
        }
        return status;
    }

    private void openTournamentIfRecruiting(UUID tournamentId) {
        if (tournamentId == null) {
            return;
        }
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> ApiException.notFound("Турнир не найден"));
        TournamentStatus next = statusAfterMatchStarts(tournament.getStatus());
        if (next == tournament.getStatus()) {
            return;
        }
        tournament.setStatus(next);
        tournamentRepository.save(tournament);
    }

    private Match requireAssignedMatch(UserPrincipal principal, UUID matchId) {
        assertReferee(principal);
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Матч не найден"));
        boolean assigned = matchRefereeRepository.existsByMatchIdAndRefereeId(matchId, principal.getId());
        if (!assigned && !principal.hasRole(Role.ADMIN)) {
            throw ApiException.forbidden("Судья не назначен на этот матч");
        }
        return match;
    }

    private void assertReferee(UserPrincipal principal) {
        if (!principal.hasAnyRole(Role.REFEREE, Role.ADMIN)) {
            throw ApiException.forbidden("Нужна роль судьи");
        }
    }

    private MatchResponse toResponse(Match match) {
        return matchMapper.toResponse(match);
    }

    private MatchEventResponse toEventResponse(MatchEvent event) {
        return matchMapper.toEventResponse(event);
    }
}
