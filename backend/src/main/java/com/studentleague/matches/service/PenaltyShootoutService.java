package com.studentleague.matches.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.PenaltyKickRequest;
import com.studentleague.matches.dto.PenaltyKickResponse;
import com.studentleague.matches.dto.PenaltyShootoutResponse;
import com.studentleague.matches.dto.ReplacePenaltyShootoutRequest;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.PenaltyKick;
import com.studentleague.matches.repository.MatchRefereeRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.matches.repository.PenaltyKickRepository;
import com.studentleague.matches.shootout.ShootoutRules;
import com.studentleague.security.UserPrincipal;
import com.studentleague.tournaments.service.PlayoffService;
import com.studentleague.users.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class PenaltyShootoutService {

    private static final EnumSet<MatchStatus> OPEN = EnumSet.of(MatchStatus.LIVE, MatchStatus.PAUSED);

    private final MatchRepository matchRepository;
    private final MatchRefereeRepository matchRefereeRepository;
    private final PenaltyKickRepository penaltyKickRepository;
    private final PlayoffService playoffService;

    public PenaltyShootoutService(
            MatchRepository matchRepository,
            MatchRefereeRepository matchRefereeRepository,
            PenaltyKickRepository penaltyKickRepository,
            PlayoffService playoffService
    ) {
        this.matchRepository = matchRepository;
        this.matchRefereeRepository = matchRefereeRepository;
        this.penaltyKickRepository = penaltyKickRepository;
        this.playoffService = playoffService;
    }

    @Transactional(readOnly = true)
    public PenaltyShootoutResponse view(UUID matchId) {
        Match match = requireMatch(matchId);
        return toResponse(match, load(matchId));
    }

    @Transactional
    public PenaltyShootoutResponse record(UserPrincipal principal, UUID matchId, PenaltyKickRequest request) {
        Match match = requireOpenDraw(principal, matchId);
        List<PenaltyKick> existing = load(matchId);
        reject(ShootoutRules.appendProblem(
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                kicksOf(existing),
                request.teamId()
        ));
        PenaltyKick kick = new PenaltyKick();
        kick.setMatchId(matchId);
        kick.setTeamId(request.teamId());
        kick.setScored(Boolean.TRUE.equals(request.scored()));
        kick.setKickOrder(existing.size() + 1);
        penaltyKickRepository.save(kick);
        return toResponse(match, load(matchId));
    }

    @Transactional
    public PenaltyShootoutResponse undoLast(UserPrincipal principal, UUID matchId) {
        Match match = requireOpenDraw(principal, matchId);
        List<PenaltyKick> existing = load(matchId);
        if (existing.isEmpty()) {
            throw ApiException.badRequest("В серии нет ударов");
        }
        penaltyKickRepository.delete(existing.getLast());
        return toResponse(match, load(matchId));
    }

    @Transactional
    public PenaltyShootoutResponse replace(UUID matchId, ReplacePenaltyShootoutRequest request) {
        Match match = requireMatch(matchId);
        if (match.getStatus() != MatchStatus.FINISHED) {
            throw ApiException.badRequest("Серию пенальти админ правит у завершённого матча");
        }
        requirePlayoffDraw(match);
        List<ShootoutRules.Kick> incoming = new ArrayList<>();
        for (PenaltyKickRequest kick : request.kicks()) {
            incoming.add(new ShootoutRules.Kick(kick.teamId(), Boolean.TRUE.equals(kick.scored())));
        }
        reject(ShootoutRules.seriesProblem(match.getHomeTeamId(), match.getAwayTeamId(), incoming));
        penaltyKickRepository.deleteAll(load(matchId));
        penaltyKickRepository.flush();
        int order = 1;
        for (ShootoutRules.Kick kick : incoming) {
            PenaltyKick row = new PenaltyKick();
            row.setMatchId(matchId);
            row.setTeamId(kick.teamId());
            row.setScored(kick.scored());
            row.setKickOrder(order);
            penaltyKickRepository.save(row);
            order++;
        }
        penaltyKickRepository.flush();
        playoffService.advance(match);
        return toResponse(match, load(matchId));
    }

    public void requireWinner(Match match) {
        if (match.getBracketRound() == null || match.getHomeScore() != match.getAwayScore()) {
            return;
        }
        List<ShootoutRules.Kick> kicks = kicksOf(load(match.getId()));
        if (kicks.isEmpty()) {
            throw ApiException.badRequest("Ничья в плей-офф не закрывается без серии пенальти");
        }
        reject(ShootoutRules.seriesProblem(match.getHomeTeamId(), match.getAwayTeamId(), kicks));
    }

    private Match requireOpenDraw(UserPrincipal principal, UUID matchId) {
        Match match = requireAssigned(principal, matchId);
        if (!OPEN.contains(match.getStatus())) {
            throw ApiException.badRequest("Удары серии пенальти записывает судья, пока матч идёт");
        }
        requirePlayoffDraw(match);
        return match;
    }

    private static void requirePlayoffDraw(Match match) {
        if (match.getBracketRound() == null) {
            throw ApiException.badRequest("Серия пенальти только в матче плей-офф");
        }
        if (match.getHomeScore() != match.getAwayScore()) {
            throw ApiException.badRequest("Серия пенальти только при ничьей в основное время");
        }
    }

    private Match requireAssigned(UserPrincipal principal, UUID matchId) {
        if (principal == null || !principal.hasAnyRole(Role.REFEREE, Role.ADMIN)) {
            throw ApiException.forbidden("Нужна роль судьи");
        }
        Match match = requireMatch(matchId);
        boolean assigned = matchRefereeRepository.existsByMatchIdAndRefereeId(matchId, principal.getId());
        if (!assigned && !principal.hasRole(Role.ADMIN)) {
            throw ApiException.forbidden("Судья не назначен на этот матч");
        }
        return match;
    }

    private Match requireMatch(UUID matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Матч не найден"));
    }

    private List<PenaltyKick> load(UUID matchId) {
        return penaltyKickRepository.findByMatchIdOrderByKickOrderAsc(matchId);
    }

    private static List<ShootoutRules.Kick> kicksOf(List<PenaltyKick> rows) {
        List<ShootoutRules.Kick> kicks = new ArrayList<>();
        for (PenaltyKick row : rows) {
            kicks.add(new ShootoutRules.Kick(row.getTeamId(), row.isScored()));
        }
        return kicks;
    }

    private static void reject(ShootoutRules.Problem problem) {
        if (problem == ShootoutRules.Problem.NONE) {
            return;
        }
        throw ApiException.badRequest(ShootoutRules.message(problem));
    }

    private static PenaltyShootoutResponse toResponse(Match match, List<PenaltyKick> rows) {
        List<ShootoutRules.Kick> kicks = kicksOf(rows);
        Integer penaltyHome = null;
        Integer penaltyAway = null;
        if (!rows.isEmpty()) {
            ShootoutRules.Tally tally = ShootoutRules.tally(match.getHomeTeamId(), kicks);
            penaltyHome = tally.homeScored();
            penaltyAway = tally.awayScored();
        }
        List<PenaltyKickResponse> body = new ArrayList<>();
        for (PenaltyKick row : rows) {
            body.add(new PenaltyKickResponse(
                    row.getId(),
                    row.getTeamId(),
                    row.getKickOrder(),
                    ShootoutRules.roundOf(row.getKickOrder()),
                    row.isScored()
            ));
        }
        return new PenaltyShootoutResponse(
                penaltyHome,
                penaltyAway,
                ShootoutRules.decisive(match.getHomeTeamId(), match.getAwayTeamId(), kicks),
                body
        );
    }
}
