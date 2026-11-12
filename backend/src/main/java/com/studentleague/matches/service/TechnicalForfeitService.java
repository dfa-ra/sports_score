package com.studentleague.matches.service;

import com.studentleague.cache.StandingsBoardCache;
import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.ForfeitReason;
import com.studentleague.matches.domain.ForfeitRules;
import com.studentleague.matches.domain.ForfeitSide;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.AwardForfeitRequest;
import com.studentleague.matches.dto.ForfeitActorResponse;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.live.LiveMatchPublisher;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.security.UserPrincipal;
import com.studentleague.tournaments.service.PlayoffService;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TechnicalForfeitService {

    static final String PLAYOFF_MUTUAL =
            "В плей-офф выберите одну команду: дальше должен пройти победитель";

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final MatchMapper matchMapper;
    private final PlayoffService playoffService;
    private final StandingsBoardCache standingsBoardCache;
    private final LiveMatchPublisher liveMatchPublisher;

    public TechnicalForfeitService(
            MatchRepository matchRepository,
            UserRepository userRepository,
            MatchMapper matchMapper,
            PlayoffService playoffService,
            StandingsBoardCache standingsBoardCache,
            LiveMatchPublisher liveMatchPublisher
    ) {
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.matchMapper = matchMapper;
        this.playoffService = playoffService;
        this.standingsBoardCache = standingsBoardCache;
        this.liveMatchPublisher = liveMatchPublisher;
    }

    @Transactional(readOnly = true)
    public MatchResponse get(UUID matchId) {
        return adminView(requireMatch(matchId));
    }

    @Transactional
    public MatchResponse award(UserPrincipal principal, UUID matchId, AwardForfeitRequest request) {
        Match match = requireMatch(matchId);
        if (ForfeitRules.active(match)) {
            throw ApiException.conflict("Техническое поражение уже засчитано. Сначала отмените его.");
        }
        if (match.getStatus() == MatchStatus.LIVE || match.getStatus() == MatchStatus.PAUSED) {
            throw ApiException.conflict("Техническое поражение нельзя засчитать, пока матч идёт или стоит на паузе");
        }
        if (match.getStatus() != MatchStatus.SCHEDULED && match.getStatus() != MatchStatus.FINISHED) {
            throw ApiException.badRequest("Техническое поражение можно засчитать до старта или после завершения матча");
        }
        if (request.side() == null || request.side() == ForfeitSide.NONE) {
            throw ApiException.badRequest("Укажите, кому засчитать поражение");
        }
        if (request.reason() == null) {
            throw ApiException.badRequest("Укажите причину");
        }
        if (request.side() == ForfeitSide.BOTH && ForfeitRules.playoff(match)) {
            throw ApiException.badRequest(PLAYOFF_MUTUAL);
        }

        match.setPreForfeitStatus(match.getStatus());
        match.setPreForfeitHomeScore(match.getHomeScore());
        match.setPreForfeitAwayScore(match.getAwayScore());
        match.setPreForfeitFinishedAt(match.getFinishedAt());
        match.setForfeit(request.side());
        match.setForfeitReason(request.reason());
        match.setForfeitReasonNote(note(request));
        match.setForfeitSetBy(principal.getId());
        match.setForfeitSetAt(Instant.now());
        match.setStatus(MatchStatus.FINISHED);
        if (match.getFinishedAt() == null) {
            match.setFinishedAt(match.getForfeitSetAt());
        }
        match.setClockRunningSince(null);
        applyScore(match, request.side());
        matchRepository.saveAndFlush(match);
        if (ForfeitRules.playoff(match)) {
            playoffService.advance(match);
        }
        standingsBoardCache.invalidateTournament(match.getTournamentId());
        MatchResponse response = adminView(match);
        liveMatchPublisher.publishMatchUpdate(response, null, "MATCH_FORFEIT");
        return response;
    }

    @Transactional
    public MatchResponse cancel(UUID matchId) {
        Match match = requireMatch(matchId);
        if (!ForfeitRules.active(match)) {
            throw ApiException.conflict("Технического поражения нет");
        }
        MatchStatus previous = match.getPreForfeitStatus() == null
                ? MatchStatus.SCHEDULED
                : match.getPreForfeitStatus();
        match.setStatus(previous);
        match.setHomeScore(match.getPreForfeitHomeScore() == null ? 0 : match.getPreForfeitHomeScore());
        match.setAwayScore(match.getPreForfeitAwayScore() == null ? 0 : match.getPreForfeitAwayScore());
        match.setFinishedAt(match.getPreForfeitFinishedAt());
        match.setForfeit(ForfeitSide.NONE);
        match.setForfeitReason(null);
        match.setForfeitReasonNote(null);
        match.setForfeitSetBy(null);
        match.setForfeitSetAt(null);
        match.setPreForfeitStatus(null);
        match.setPreForfeitHomeScore(null);
        match.setPreForfeitAwayScore(null);
        match.setPreForfeitFinishedAt(null);
        matchRepository.saveAndFlush(match);
        if (ForfeitRules.playoff(match)) {
            playoffService.syncAfterScoreChange(match);
        }
        standingsBoardCache.invalidateTournament(match.getTournamentId());
        MatchResponse response = adminView(match);
        liveMatchPublisher.publishMatchUpdate(response, null, "MATCH_FORFEIT_CANCELLED");
        return response;
    }

    private void applyScore(Match match, ForfeitSide side) {
        switch (side) {
            case HOME -> {
                match.setHomeScore(0);
                match.setAwayScore(ForfeitRules.SCORE);
            }
            case AWAY -> {
                match.setHomeScore(ForfeitRules.SCORE);
                match.setAwayScore(0);
            }
            case BOTH -> {
                match.setHomeScore(0);
                match.setAwayScore(0);
            }
            case NONE -> throw ApiException.badRequest("Укажите, кому засчитать поражение");
        }
    }

    private String note(AwardForfeitRequest request) {
        if (request.reason() != ForfeitReason.OTHER || request.note() == null) {
            return null;
        }
        String trimmed = request.note().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private MatchResponse adminView(Match match) {
        return matchMapper.toAdminResponse(match, actor(match));
    }

    private ForfeitActorResponse actor(Match match) {
        if (!ForfeitRules.active(match) || match.getForfeitSetBy() == null) {
            return null;
        }
        User user = userRepository.findById(match.getForfeitSetBy()).orElse(null);
        return new ForfeitActorResponse(match.getForfeitSetBy(), personName(user));
    }

    private static String personName(User user) {
        if (user == null) {
            return "Админ";
        }
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String name = (first + " " + last).trim();
        if (!name.isEmpty()) {
            return name;
        }
        return user.getEmail() == null ? "Админ" : user.getEmail();
    }

    private Match requireMatch(UUID matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Матч не найден"));
    }
}
