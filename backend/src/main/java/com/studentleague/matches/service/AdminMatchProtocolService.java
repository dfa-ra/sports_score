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
    public MatchEventResponse updateMinute(UUID matchId, UUID eventId, AdminUpdateMatchEventRequest request) {
        Match match = requireEditableMatch(matchId);
        MatchEvent event = requireEvent(matchId, eventId);
        applyMinute(match, event, request.minute());
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

    private void applyMinute(Match match, MatchEvent event, int minute) {
        event.setGameTime(minute * 60);
        event.setPeriod(periodFor(match, minute));
    }

    private int periodFor(Match match, int minute) {
        int lengthMinutes = Math.max(1, match.getPeriodLengthSeconds() / 60);
        int period = minute / lengthMinutes + 1;
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
