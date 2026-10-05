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
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.repository.TeamMemberRepository;
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

    public AdminMatchProtocolService(
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            PlayerProfileRepository playerProfileRepository,
            TeamMemberRepository teamMemberRepository,
            MatchScoreService matchScoreService,
            MatchMapper matchMapper,
            LiveMatchPublisher liveMatchPublisher
    ) {
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.matchScoreService = matchScoreService;
        this.matchMapper = matchMapper;
        this.liveMatchPublisher = liveMatchPublisher;
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
                .orElseThrow(() -> ApiException.notFound("Player not found"));
        if (!teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                request.teamId(), request.playerId(), TeamMemberStatus.ACTIVE)) {
            throw ApiException.badRequest("Игрок не в заявке этой команды");
        }

        MatchEvent event = new MatchEvent();
        event.setMatchId(matchId);
        event.setEventType(request.eventType());
        event.setTimestamp(Instant.now());
        applyMinute(match, event, request.minute());
        event.setTeamId(request.teamId());
        event.setPlayerId(request.playerId());
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
        matchEventRepository.save(event);
        return publish(match, event, "MATCH_EVENT");
    }

    @Transactional
    public MatchEventResponse voidEvent(UUID matchId, UUID eventId) {
        Match match = requireEditableMatch(matchId);
        MatchEvent event = requireEvent(matchId, eventId);
        if (event.isVoided()) {
            throw ApiException.conflict("Event already voided");
        }
        event.setVoided(true);
        event.setVoidedAt(Instant.now());
        matchEventRepository.save(event);
        return publish(match, event, "MATCH_EVENT_VOIDED");
    }

    private MatchEventResponse publish(Match match, MatchEvent event, String type) {
        matchEventRepository.flush();
        matchScoreService.apply(match);
        MatchResponse matchResponse = matchMapper.toResponse(matchRepository.saveAndFlush(match));
        MatchEventResponse eventResponse = matchMapper.toEventResponse(event);
        liveMatchPublisher.publishMatchUpdate(matchResponse, eventResponse, type);
        return eventResponse;
    }

    private void applyPlayers(Match match, MatchEvent event, AdminUpdateMatchEventRequest request) {
        if (event.isVoided()) {
            throw ApiException.conflict("Событие уже убрано из протокола");
        }
        if (event.getEventType() != MatchEventType.GOAL) {
            throw ApiException.badRequest("Забившего и ассистента можно менять только у гола");
        }
        if (request.playerId() == null) {
            throw ApiException.badRequest("Укажите забившего");
        }
        UUID scorerTeam = teamOf(match, request.playerId(), event.getTeamId());
        event.setPlayerId(request.playerId());
        event.setTeamId(scorerTeam);
        UUID assistId = request.secondaryPlayerId();
        if (assistId == null) {
            event.setSecondaryPlayerId(null);
            return;
        }
        if (assistId.equals(request.playerId())) {
            throw ApiException.badRequest("Ассистент и забивший — разные игроки");
        }
        teamOf(match, assistId, scorerTeam);
        event.setSecondaryPlayerId(assistId);
    }

    /** Scorer or assist must already play for one of the two clubs. The goal's team follows the scorer. */
    private UUID teamOf(Match match, UUID playerId, UUID preferredTeamId) {
        playerProfileRepository.findById(playerId)
                .orElseThrow(() -> ApiException.notFound("Player not found"));
        boolean home = teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                match.getHomeTeamId(), playerId, TeamMemberStatus.ACTIVE);
        boolean away = teamMemberRepository.existsByTeamIdAndPlayerIdAndStatus(
                match.getAwayTeamId(), playerId, TeamMemberStatus.ACTIVE);
        if (!home && !away) {
            throw ApiException.badRequest("Игрок не в заявке команд этого матча");
        }
        if (home && away
                && preferredTeamId != null
                && (preferredTeamId.equals(match.getHomeTeamId()) || preferredTeamId.equals(match.getAwayTeamId()))) {
            return preferredTeamId;
        }
        return home ? match.getHomeTeamId() : match.getAwayTeamId();
    }

    private void applyMinute(Match match, MatchEvent event, int minute) {
        // The admin types the match minute from kickoff — the same number the
        // public overview prints. 18 on a 2×15 match is 18:00, not 3:00 left.
        event.setGameTime(Math.max(0, minute) * 60);
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
                .orElseThrow(() -> ApiException.notFound("Match not found"));
        if (!EDITABLE_STATUS.contains(match.getStatus())) {
            throw ApiException.conflict("Протокол этого матча закрыт");
        }
        return match;
    }

    private MatchEvent requireEvent(UUID matchId, UUID eventId) {
        MatchEvent event = matchEventRepository.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("Match event not found"));
        if (!matchId.equals(event.getMatchId())) {
            throw ApiException.badRequest("Event does not belong to this match");
        }
        return event;
    }
}
