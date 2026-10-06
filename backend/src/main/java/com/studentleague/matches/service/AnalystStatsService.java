package com.studentleague.matches.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.AnalystCoverage;
import com.studentleague.matches.domain.AnalystStat;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.domain.PossessionSide;
import com.studentleague.matches.dto.AnalystStatsResponse;
import com.studentleague.matches.dto.TeamAnalystStatsResponse;
import com.studentleague.matches.entity.AnalystCounters;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchAnalystStats;
import com.studentleague.matches.repository.MatchAnalystStatsRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.security.UserPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AnalystStatsService {

    private final MatchRepository matchRepository;
    private final MatchAnalystStatsRepository statsRepository;
    private final AnalystAssignmentService assignmentService;

    public AnalystStatsService(
            MatchRepository matchRepository,
            MatchAnalystStatsRepository statsRepository,
            AnalystAssignmentService assignmentService
    ) {
        this.matchRepository = matchRepository;
        this.statsRepository = statsRepository;
        this.assignmentService = assignmentService;
    }

    /**
     * Live read. Not cached with the 60s standings board: each call hits the row
     * and adds possession seconds that are still running.
     */
    @Transactional(readOnly = true)
    public AnalystStatsResponse get(UUID matchId) {
        Match match = requireMatch(matchId);
        return statsRepository.findById(matchId)
                .map(row -> toResponse(row, clockAsOf(match)))
                .orElseGet(() -> empty(matchId));
    }

    @Transactional
    public AnalystStatsResponse adjust(UserPrincipal principal, UUID matchId, UUID teamId, AnalystStat stat, int delta) {
        if (delta != 1 && delta != -1) {
            throw ApiException.badRequest("Можно только прибавить или убрать единицу");
        }
        Match match = requireMatch(matchId);
        assertOpen(match);
        boolean home = teamIsHome(match, teamId);
        assertTeam(principal, match, home);
        MatchAnalystStats row = loadForUpdate(matchId);
        row.apply(home, stat, delta);
        return toResponse(statsRepository.save(row), Instant.now());
    }

    @Transactional
    public AnalystStatsResponse setPossession(UserPrincipal principal, UUID matchId, PossessionSide side) {
        Match match = requireMatch(matchId);
        assertOpen(match);
        assertPossession(principal, match, side);
        MatchAnalystStats row = loadForUpdate(matchId);
        if (side == PossessionSide.PAUSED && !row.isPossessionTracked()) {
            return toResponse(row, Instant.now());
        }
        flushPossession(row, Instant.now());
        row.setPossessionTracked(true);
        row.setPossessionSide(side);
        row.setPossessionSince(side == PossessionSide.PAUSED ? null : Instant.now());
        return toResponse(statsRepository.save(row), Instant.now());
    }

    private void flushPossession(MatchAnalystStats row, Instant now) {
        PossessionSide side = row.getPossessionSide();
        Instant since = row.getPossessionSince();
        if (side == null || side == PossessionSide.PAUSED || since == null) {
            return;
        }
        long elapsed = Duration.between(since, now).getSeconds();
        if (elapsed <= 0) {
            row.setPossessionSince(null);
            return;
        }
        AnalystCounters counters = side == PossessionSide.HOME ? row.getHome() : row.getAway();
        counters.addPossessionSeconds((int) Math.min(elapsed, Integer.MAX_VALUE));
        row.setPossessionSince(null);
    }

    private MatchAnalystStats loadForUpdate(UUID matchId) {
        return statsRepository.lockByMatchId(matchId).orElseGet(() -> insertRow(matchId));
    }

    private MatchAnalystStats insertRow(UUID matchId) {
        MatchAnalystStats created = new MatchAnalystStats();
        created.setMatchId(matchId);
        try {
            statsRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException ex) {
            return statsRepository.lockByMatchId(matchId)
                    .orElseThrow(() -> ApiException.conflict("Статистика матча уже создаётся"));
        }
        return statsRepository.lockByMatchId(matchId).orElse(created);
    }

    private boolean teamIsHome(Match match, UUID teamId) {
        if (teamId.equals(match.getHomeTeamId())) {
            return true;
        }
        if (teamId.equals(match.getAwayTeamId())) {
            return false;
        }
        throw ApiException.badRequest("Команда не играет в этом матче");
    }

    private Match requireMatch(UUID matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Матч не найден"));
    }

    private void assertOpen(Match match) {
        if (match.getStatus() == MatchStatus.FINISHED || match.getStatus() == MatchStatus.CANCELLED) {
            throw ApiException.conflict("Матч уже завершён");
        }
    }

    private void assertTeam(UserPrincipal principal, Match match, boolean homeTeam) {
        AnalystCoverage coverage = assignmentService.coverage(principal, match.getId());
        if (coverage == null) {
            throw ApiException.forbidden("Вы не назначены на этот матч");
        }
        if ((homeTeam && coverage == AnalystCoverage.AWAY) || (!homeTeam && coverage == AnalystCoverage.HOME)) {
            throw ApiException.forbidden("Вы ведёте только свою команду");
        }
    }

    private void assertPossession(UserPrincipal principal, Match match, PossessionSide side) {
        AnalystCoverage coverage = assignmentService.coverage(principal, match.getId());
        if (coverage == null) {
            throw ApiException.forbidden("Вы не назначены на этот матч");
        }
        if (coverage == AnalystCoverage.BOTH || side == PossessionSide.PAUSED) {
            return;
        }
        if ((side == PossessionSide.HOME && coverage != AnalystCoverage.HOME)
                || (side == PossessionSide.AWAY && coverage != AnalystCoverage.AWAY)) {
            throw ApiException.forbidden("Вы ведёте только свою команду");
        }
    }

    private Instant clockAsOf(Match match) {
        if (match.getStatus() != MatchStatus.FINISHED && match.getStatus() != MatchStatus.CANCELLED) {
            return Instant.now();
        }
        return match.getFinishedAt() != null ? match.getFinishedAt() : Instant.EPOCH;
    }

    private AnalystStatsResponse empty(UUID matchId) {
        return new AnalystStatsResponse(
                matchId,
                toTeam(new AnalystCounters()),
                toTeam(new AnalystCounters()),
                false,
                0,
                0,
                null,
                null,
                null
        );
    }

    private AnalystStatsResponse toResponse(MatchAnalystStats row, Instant now) {
        int homeSeconds = liveSeconds(row, PossessionSide.HOME, now);
        int awaySeconds = liveSeconds(row, PossessionSide.AWAY, now);
        Integer homePercent = null;
        Integer awayPercent = null;
        if (row.isPossessionTracked()) {
            int total = homeSeconds + awaySeconds;
            if (total <= 0) {
                homePercent = 0;
                awayPercent = 0;
            } else {
                homePercent = (int) Math.round(homeSeconds * 100.0 / total);
                awayPercent = 100 - homePercent;
            }
        }
        return new AnalystStatsResponse(
                row.getMatchId(),
                toTeam(row.getHome()),
                toTeam(row.getAway()),
                row.isPossessionTracked(),
                homeSeconds,
                awaySeconds,
                row.getPossessionSide(),
                homePercent,
                awayPercent
        );
    }

    private int liveSeconds(MatchAnalystStats row, PossessionSide side, Instant now) {
        AnalystCounters counters = side == PossessionSide.HOME ? row.getHome() : row.getAway();
        int stored = counters.getPossessionSeconds();
        if (row.getPossessionSide() != side || row.getPossessionSince() == null) {
            return stored;
        }
        long elapsed = Duration.between(row.getPossessionSince(), now).getSeconds();
        if (elapsed <= 0) {
            return stored;
        }
        return stored + (int) Math.min(elapsed, Integer.MAX_VALUE - stored);
    }

    private static TeamAnalystStatsResponse toTeam(AnalystCounters counters) {
        return new TeamAnalystStatsResponse(
                counters.getShots(),
                counters.getShotsOnTarget(),
                counters.getSaves(),
                counters.getCorners(),
                counters.getFouls(),
                counters.getFreeKicks(),
                counters.getKickIns(),
                counters.getWoodwork()
        );
    }
}
