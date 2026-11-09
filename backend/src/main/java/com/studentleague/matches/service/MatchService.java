package com.studentleague.matches.service;

import com.studentleague.cache.StandingsBoardCache;
import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.clock.MatchClock;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.AssignRefereeRequest;
import com.studentleague.matches.dto.CreateMatchRequest;
import com.studentleague.matches.dto.MatchRefereeResponse;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.dto.RecentFormResponse;
import com.studentleague.matches.dto.UpdateMatchRequest;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchReferee;
import com.studentleague.matches.repository.MatchAnalystAssignmentRepository;
import com.studentleague.matches.repository.MatchAnalystStatsRepository;
import com.studentleague.matches.repository.MatchAvailabilityRepository;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.matches.repository.MatchRefereeRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.notifications.NotificationEventType;
import com.studentleague.security.CurrentUser;
import com.studentleague.notifications.NotificationService;
import com.studentleague.tournaments.domain.CompetitionStage;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTableRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import com.studentleague.security.UserPrincipal;
import com.studentleague.tournaments.service.PlayoffService;
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import com.studentleague.users.service.RoleService;
import org.springframework.data.domain.Page;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchRefereeRepository matchRefereeRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchLineupPlayerRepository matchLineupPlayerRepository;
    private final MatchAvailabilityRepository matchAvailabilityRepository;
    private final MatchAnalystStatsRepository matchAnalystStatsRepository;
    private final MatchAnalystAssignmentRepository matchAnalystAssignmentRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TournamentTableRepository tournamentTableRepository;
    private final PlayoffService playoffService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MatchMapper matchMapper;
    private final RoleService roleService;
    private final StandingsBoardCache standingsBoardCache;
    private final AbandonedLiveMatchService abandonedLiveMatchService;

    public MatchService(
            MatchRepository matchRepository,
            MatchRefereeRepository matchRefereeRepository,
            MatchEventRepository matchEventRepository,
            MatchLineupPlayerRepository matchLineupPlayerRepository,
            MatchAvailabilityRepository matchAvailabilityRepository,
            MatchAnalystStatsRepository matchAnalystStatsRepository,
            MatchAnalystAssignmentRepository matchAnalystAssignmentRepository,
            TournamentRepository tournamentRepository,
            TournamentTeamRepository tournamentTeamRepository,
            TournamentTableRepository tournamentTableRepository,
            PlayoffService playoffService,
            UserRepository userRepository,
            NotificationService notificationService,
            MatchMapper matchMapper,
            RoleService roleService,
            StandingsBoardCache standingsBoardCache,
            AbandonedLiveMatchService abandonedLiveMatchService
    ) {
        this.matchRepository = matchRepository;
        this.matchRefereeRepository = matchRefereeRepository;
        this.matchEventRepository = matchEventRepository;
        this.matchLineupPlayerRepository = matchLineupPlayerRepository;
        this.matchAvailabilityRepository = matchAvailabilityRepository;
        this.matchAnalystStatsRepository = matchAnalystStatsRepository;
        this.matchAnalystAssignmentRepository = matchAnalystAssignmentRepository;
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.tournamentTableRepository = tournamentTableRepository;
        this.playoffService = playoffService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.matchMapper = matchMapper;
        this.roleService = roleService;
        this.standingsBoardCache = standingsBoardCache;
        this.abandonedLiveMatchService = abandonedLiveMatchService;
    }

    @Transactional
    public MatchResponse create(CreateMatchRequest request) {
        if (request.homeTeamId().equals(request.awayTeamId())) {
            throw ApiException.badRequest("Хозяева и гости должны быть разными командами");
        }
        Tournament tournament = tournamentRepository.findById(request.tournamentId())
                .orElseThrow(() -> ApiException.notFound("Турнир не найден"));

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
        match.setPeriodCount(request.periodCount() == null ? MatchClock.DEFAULT_PERIOD_COUNT : request.periodCount());
        int minutes = request.periodLengthMinutes() == null
                ? MatchClock.DEFAULT_PERIOD_LENGTH_SECONDS / 60
                : request.periodLengthMinutes();
        match.setPeriodLengthSeconds(minutes * 60);
        match.setVenue(blankToNull(request.venue()));
        attachGroup(tournament, match, request.tableId());
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
        abandonedLiveMatchService.closeIdle(Instant.now());
        return toResponse(requireMatch(id));
    }

    @Transactional
    public void delete(UUID id) {
        Match match = requireMatch(id);
        if (match.getBracketRound() != null) {
            throw ApiException.badRequest("Матч плей-офф удалять нельзя: пара зафиксирована в сетке");
        }
        UUID tournamentId = match.getTournamentId();
        matchEventRepository.deleteByMatchId(id);
        matchLineupPlayerRepository.deleteByMatchId(id);
        matchRefereeRepository.deleteByMatchId(id);
        matchAvailabilityRepository.deleteByMatchId(id);
        matchAnalystStatsRepository.deleteByMatchId(id);
        matchAnalystAssignmentRepository.deleteByMatchId(id);
        matchRepository.flush();
        matchRepository.delete(match);
        standingsBoardCache.invalidateTournament(tournamentId);
    }

    @Transactional
    public MatchResponse update(UUID id, UpdateMatchRequest request) {
        Match match = requireMatch(id);
        match.setVenue(blankToNull(request.venue()));
        Match saved = matchRepository.save(match);
        playoffService.syncVenue(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<MatchResponse> list(UUID tournamentId, MatchStatus status, Pageable pageable) {
        return list(tournamentId, status, null, null, null, pageable);
    }

    /**
     * Same list as before. {@code from} and {@code to} additionally bound {@code scheduledAt}
     * (inclusive). Sort is unchanged: an unknown sort property still fails the same way.
     * {@code teamId} keeps a match when that team is home or away. Omitting it leaves the
     * previous filters and the public list unchanged.
     */
    @Transactional(readOnly = true)
    public Page<MatchResponse> list(
            UUID tournamentId,
            MatchStatus status,
            Instant from,
            Instant to,
            UUID teamId,
            Pageable pageable
    ) {
        abandonedLiveMatchService.closeIdle(Instant.now());
        if (from == null && to == null && teamId == null) {
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
            return matchMapper.toPage(page);
        }
        Specification<Match> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tournamentId != null) {
                predicates.add(cb.equal(root.get("tournamentId"), tournamentId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (teamId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("homeTeamId"), teamId),
                        cb.equal(root.get("awayTeamId"), teamId)
                ));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return matchMapper.toPage(matchRepository.findAll(spec, pageable));
    }

    /**
     * The match a home banner should call nearest: in progress, otherwise the soonest kickoff from {@code now}.
     * A scheduled match whose kickoff is already past is not upcoming.
     */
    @Transactional(readOnly = true)
    public MatchResponse nextUpcoming(Instant now) {
        return nextUpcoming(now, null);
    }

    /**
     * Nearest match for the home banner. With a tournament id, only that tournament counts.
     * Live or paused wins, including a match whose kickoff was never set.
     * Otherwise the soonest scheduled kickoff that is not before {@code now}.
     * A scheduled match with no kickoff is not nearest. A finished match is not nearest,
     * even when its planned kickoff is still ahead.
     */
    @Transactional(readOnly = true)
    public MatchResponse nextUpcoming(Instant now, UUID tournamentId) {
        abandonedLiveMatchService.closeIdle(now);
        List<MatchStatus> inPlay = List.of(MatchStatus.LIVE, MatchStatus.PAUSED);
        List<Match> found = tournamentId == null
                ? matchRepository.findNextByKickoff(inPlay, MatchStatus.SCHEDULED, now, PageRequest.of(0, 1))
                : matchRepository.findNextByKickoffForTournament(
                        tournamentId, inPlay, MatchStatus.SCHEDULED, now, PageRequest.of(0, 1));
        if (found.isEmpty()) {
            return null;
        }
        return toResponse(found.getFirst());
    }

    @Transactional
    public MatchRefereeResponse assignReferee(UUID matchId, AssignRefereeRequest request) {
        requireMatch(matchId);
        User referee = userRepository.findById(request.refereeId())
                .orElseThrow(() -> ApiException.notFound("Судья не найден"));
        if (referee.getRole() != Role.REFEREE && referee.getRole() != Role.ADMIN
                && !roleService.hasApproved(referee.getId(), Role.REFEREE)
                && !roleService.hasApproved(referee.getId(), Role.ADMIN)) {
            throw ApiException.badRequest("У пользователя должна быть роль судьи");
        }
        if (matchRefereeRepository.existsByMatchIdAndRefereeId(matchId, referee.getId())) {
            throw ApiException.conflict("Судья уже назначен на этот матч");
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
        return toRefereeResponse(assignment, true);
    }

    @Transactional(readOnly = true)
    public List<RecentFormResponse> recentForm(UUID teamId, int limit) {
        int size = Math.max(1, Math.min(limit, 10));
        return matchRepository.findRecentForm(teamId, MatchStatus.FINISHED, Pageable.ofSize(size));
    }

    @Transactional(readOnly = true)
    public Page<MatchResponse> listForTeam(UUID teamId, Pageable pageable) {
        return matchMapper.toPage(matchRepository.findByTeamId(teamId, pageable));
    }

    @Transactional(readOnly = true)
    public List<MatchRefereeResponse> listReferees(UUID matchId, UserPrincipal principal) {
        requireMatch(matchId);
        boolean includeEmail = CurrentUser.isAdmin();
        return matchRefereeRepository.findByMatchId(matchId).stream()
                .map(assignment -> toRefereeResponse(assignment, includeEmail))
                .toList();
    }

    private MatchRefereeResponse toRefereeResponse(MatchReferee assignment, boolean includeEmail) {
        User referee = assignment.getRefereeId() == null
                ? null
                : userRepository.findById(assignment.getRefereeId()).orElse(null);
        return new MatchRefereeResponse(
                assignment.getId(),
                assignment.getMatchId(),
                assignment.getRefereeId(),
                assignment.getAssignedAt(),
                referee == null ? null : referee.getFirstName(),
                referee == null ? null : referee.getLastName(),
                includeEmail && referee != null ? referee.getEmail() : null
        );
    }

    private void attachGroup(Tournament tournament, Match match, UUID tableId) {
        if (tournament.getCompetitionStage() == CompetitionStage.PLAYOFF) {
            throw ApiException.badRequest("Турнир уже в плей-офф. Новые матчи появляются из сетки.");
        }
        TournamentTeam home = tournamentTeamRepository.findByTournamentIdAndTeamId(tournament.getId(), match.getHomeTeamId())
                .orElseThrow(() -> ApiException.badRequest("Команда не заявлена в турнир"));
        TournamentTeam away = tournamentTeamRepository.findByTournamentIdAndTeamId(tournament.getId(), match.getAwayTeamId())
                .orElseThrow(() -> ApiException.badRequest("Команда не заявлена в турнир"));
        if (tableId != null) {
            boolean ownTable = tournamentTableRepository.findById(tableId)
                    .filter(table -> tournament.getId().equals(table.getTournamentId()))
                    .isPresent();
            if (!ownTable) {
                throw ApiException.badRequest("Группа не относится к этому турниру");
            }
            if (!tableId.equals(home.getTableId()) || !tableId.equals(away.getTableId())) {
                throw ApiException.badRequest("Обе команды матча должны стоять в этой группе");
            }
            match.setTableId(tableId);
            return;
        }
        if (home.getTableId() != null && home.getTableId().equals(away.getTableId())) {
            match.setTableId(home.getTableId());
            return;
        }
        if (home.getTableId() != null || away.getTableId() != null) {
            throw ApiException.badRequest("Матч группового этапа можно назначить только внутри одной группы");
        }
    }

    private void requireApproved(UUID tournamentId, UUID teamId) {
        var entry = tournamentTeamRepository.findByTournamentIdAndTeamId(tournamentId, teamId)
                .orElseThrow(() -> ApiException.badRequest("Команда не заявлена в турнир"));
        if (entry.getStatus() != TournamentTeamStatus.APPROVED) {
            throw ApiException.badRequest("Команду нужно подтвердить, прежде чем ставить матчи");
        }
    }

    private Match requireMatch(UUID id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Матч не найден"));
    }

    private MatchResponse toResponse(Match match) {
        return matchMapper.toResponse(match);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
