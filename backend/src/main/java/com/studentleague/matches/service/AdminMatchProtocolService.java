package com.studentleague.matches.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.clock.MatchClock;
import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.AdminCreateMatchEventRequest;
import com.studentleague.matches.dto.AdminUpdateMatchEventRequest;
import com.studentleague.matches.dto.MatchEventResponse;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.live.LiveMatchPublisher;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.matches.repository.PenaltyKickRepository;
import com.studentleague.matches.scoring.OwnGoals;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.tournaments.service.PlayoffService;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.tournaments.service.TournamentRosterGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

@Service
public class AdminMatchProtocolService {

    private static final EnumSet<MatchEventType> EDITABLE = EnumSet.of(
            MatchEventType.GOAL,
            MatchEventType.YELLOW_CARD,
            MatchEventType.RED_CARD
    );

    /** Finished matches stay editable: an admin corrects the same event rows the referee pad wrote. */
    private static final EnumSet<MatchStatus> EDITABLE_STATUS = EnumSet.of(
            MatchStatus.SCHEDULED,
            MatchStatus.LIVE,
            MatchStatus.PAUSED,
            MatchStatus.FINISHED
    );

    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final MatchScoreService matchScoreService;
    private final MatchMapper matchMapper;
    private final LiveMatchPublisher liveMatchPublisher;
    private final PlayoffService playoffService;
    private final TournamentRosterGuard tournamentRosterGuard;
    private final PenaltyKickRepository penaltyKickRepository;

    public AdminMatchProtocolService(
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            PlayerProfileRepository playerProfileRepository,
            TeamMemberRepository teamMemberRepository,
            MatchScoreService matchScoreService,
            MatchMapper matchMapper,
            LiveMatchPublisher liveMatchPublisher,
            PlayoffService playoffService,
            TournamentRosterGuard tournamentRosterGuard,
            PenaltyKickRepository penaltyKickRepository
    ) {
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.matchScoreService = matchScoreService;
        this.matchMapper = matchMapper;
        this.liveMatchPublisher = liveMatchPublisher;
        this.playoffService = playoffService;
        this.tournamentRosterGuard = tournamentRosterGuard;
        this.penaltyKickRepository = penaltyKickRepository;
    }

    @Transactional
    public MatchEventResponse add(UUID matchId, AdminCreateMatchEventRequest request) {
        if (!EDITABLE.contains(request.eventType())) {
            throw ApiException.badRequest("В протокол можно добавить гол, жёлтую или красную карточку");
        }
        Match match = requireEditableMatch(matchId);
        if (!request.teamId().equals(match.getHomeTeamId()) && !request.teamId().equals(match.getAwayTeamId())) {
            throw ApiException.badRequest("Укажите команду этого матча");
        }
        playerProfileRepository.findById(request.playerId())
                .orElseThrow(() -> ApiException.notFound("Игрок не найден"));
        if (!teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                request.teamId(), request.playerId(), TeamMemberStatus.ACTIVE)) {
            throw ApiException.badRequest("Игрок не в заявке этой команды");
        }
        tournamentRosterGuard.rejectAppearanceForSecondClub(match.getTournamentId(), request.teamId(), request.playerId());

        MatchEvent event = new MatchEvent();
        event.setMatchId(matchId);
        event.setEventType(request.eventType());
        event.setTimestamp(Instant.now());
        applyMinute(match, event, request.minute());
        event.setTeamId(request.teamId());
        event.setPlayerId(request.playerId());
        if (Boolean.TRUE.equals(request.ownGoal())) {
            if (request.eventType() != MatchEventType.GOAL) {
                throw ApiException.badRequest("Автогол можно отметить только у гола");
            }
            event.setMetadata(OwnGoals.apply(null, true));
            event.setSecondaryPlayerId(null);
        }
        event.setVoided(false);
        matchEventRepository.save(event);

        return publish(match, event, "MATCH_EVENT");
    }

    @Transactional
    public MatchEventResponse update(UUID matchId, UUID eventId, AdminUpdateMatchEventRequest request) {
        Match match = requireEditableMatch(matchId);
        MatchEvent event = requireEvent(matchId, eventId);
        applyMinute(match, event, request.minute());
        // A minute-only patch omits the scorer. Sending playerId rewrites the same goal row:
        // playerId is who scored, secondaryPlayerId is the assist (null clears it).
        if (Boolean.TRUE.equals(request.updatePlayers()) || request.playerId() != null) {
            applyPlayers(match, event, request);
        }
        if (request.ownGoal() != null) {
            applyOwnGoal(event, request.ownGoal());
        }
        matchEventRepository.save(event);
        return publish(match, event, "MATCH_EVENT");
    }

    @Transactional
    public MatchEventResponse voidEvent(UUID matchId, UUID eventId) {
        Match match = requireEditableMatch(matchId);
        MatchEvent event = requireEvent(matchId, eventId);
        if (event.isVoided()) {
            throw ApiException.conflict("Событие уже убрано из протокола");
        }
        event.setVoided(true);
        event.setVoidedAt(Instant.now());
        matchEventRepository.saveAndFlush(event);
        if (event.getEventType() == MatchEventType.GOAL) {
            matchScoreService.apply(match);
            rejectPlayoffDrawWithoutShootout(match);
        }
        return publish(match, event, "MATCH_EVENT_VOIDED");
    }

    /**
     * A finished playoff match cannot be left drawn by cancelling a goal
     * unless a penalty shootout is already stored. The caller rolls back,
     * so the protocol and the next-round slot stay as they were.
     * Group-stage draws are unchanged: they have no bracket round.
     */
    private void rejectPlayoffDrawWithoutShootout(Match match) {
        if (match.getStatus() != MatchStatus.FINISHED || match.getBracketRound() == null) {
            return;
        }
        if (match.getHomeScore() != match.getAwayScore()) {
            return;
        }
        if (!penaltyKickRepository.findByMatchIdOrderByKickOrderAsc(match.getId()).isEmpty()) {
            return;
        }
        throw ApiException.conflict("После отмены гола будет ничья: сначала задайте серию пенальти");
    }

    private MatchEventResponse publish(Match match, MatchEvent event, String type) {
        matchEventRepository.flush();
        matchScoreService.apply(match);
        matchRepository.saveAndFlush(match);
        if (match.getStatus() == MatchStatus.FINISHED) {
            playoffService.advance(match);
        }
        MatchResponse matchResponse = matchMapper.toResponse(match);
        MatchEventResponse eventResponse = matchMapper.toEventResponse(event);
        liveMatchPublisher.publishMatchUpdate(matchResponse, eventResponse, type);
        return eventResponse;
    }

    private void applyPlayers(Match match, MatchEvent event, AdminUpdateMatchEventRequest request) {
        if (event.isVoided()) {
            throw ApiException.conflict("Событие уже убрано из протокола");
        }
        if (event.getEventType() != MatchEventType.GOAL) {
            throw ApiException.badRequest("Забившего и того, кто отдал передачу, можно менять только у гола");
        }
        if (request.playerId() == null) {
            throw ApiException.badRequest("Укажите забившего");
        }
        UUID scorerTeam = teamOf(match, request.playerId(), event.getTeamId());
        event.setPlayerId(request.playerId());
        event.setTeamId(scorerTeam);
        UUID assistId = request.secondaryPlayerId();
        if (Boolean.TRUE.equals(request.ownGoal()) || assistId == null) {
            event.setSecondaryPlayerId(null);
            return;
        }
        if (assistId.equals(request.playerId())) {
            throw ApiException.badRequest("Тот, кто отдал передачу, и забивший — разные игроки");
        }
        teamOf(match, assistId, scorerTeam);
        event.setSecondaryPlayerId(assistId);
    }

    private void applyOwnGoal(MatchEvent event, boolean ownGoal) {
        if (event.getEventType() != MatchEventType.GOAL) {
            throw ApiException.badRequest("Автогол можно отметить только у гола");
        }
        if (ownGoal) {
            event.setSecondaryPlayerId(null);
        }
        event.setMetadata(OwnGoals.apply(event.getMetadata(), ownGoal));
    }

    /** Scorer or assist must already play for one of the two clubs. The goal's team follows the scorer. */
    private UUID teamOf(Match match, UUID playerId, UUID preferredTeamId) {
        playerProfileRepository.findById(playerId)
                .orElseThrow(() -> ApiException.notFound("Игрок не найден"));
        boolean home = teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                match.getHomeTeamId(), playerId, TeamMemberStatus.ACTIVE);
        boolean away = teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                match.getAwayTeamId(), playerId, TeamMemberStatus.ACTIVE);
        if (!home && !away) {
            throw ApiException.badRequest("Игрок не в заявке команд этого матча");
        }
        UUID side;
        if (home && away
                && preferredTeamId != null
                && (preferredTeamId.equals(match.getHomeTeamId()) || preferredTeamId.equals(match.getAwayTeamId()))) {
            side = preferredTeamId;
        } else {
            side = home ? match.getHomeTeamId() : match.getAwayTeamId();
        }
        tournamentRosterGuard.rejectAppearanceForSecondClub(match.getTournamentId(), side, playerId);
        return side;
    }

    private void applyMinute(Match match, MatchEvent event, int minute) {
        // The admin types the match minute from kickoff — the same number the
        // public overview prints. 18 on a 2×15 match is 18:00, not 3:00 left.
        // 16' of a 2×15 is the first minute of the second half and stays valid.
        if (minute < 0) {
            throw ApiException.badRequest("Минута не может быть отрицательной");
        }
        if (minute > MatchClock.maxProtocolMinute(match)) {
            throw ApiException.badRequest("Минута больше длительности матча");
        }
        event.setGameTime(minute * 60);
        event.setPeriod(periodFor(match, minute));
    }

    private int periodFor(Match match, int minute) {
        int lengthMinutes = Math.max(1, match.getPeriodLengthSeconds() / 60);
        if (minute <= 0) {
            return 1;
        }
        // 15' is still the first half of a 2×15; 16' is the first minute of the second.
        int period = (minute - 1) / lengthMinutes + 1;
        int count = Math.max(1, match.getPeriodCount() > 0 ? match.getPeriodCount() : MatchClock.DEFAULT_PERIOD_COUNT);
        return Math.min(period, count);
    }

    private Match requireEditableMatch(UUID matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Матч не найден"));
        if (!EDITABLE_STATUS.contains(match.getStatus())) {
            throw ApiException.conflict("Протокол этого матча закрыт");
        }
        return match;
    }

    private MatchEvent requireEvent(UUID matchId, UUID eventId) {
        MatchEvent event = matchEventRepository.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("Событие матча не найдено"));
        if (!matchId.equals(event.getMatchId())) {
            throw ApiException.badRequest("Событие не относится к этому матчу");
        }
        return event;
    }
}
