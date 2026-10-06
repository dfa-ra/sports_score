package com.studentleague.matches.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.AnalystAssignmentMode;
import com.studentleague.matches.domain.AnalystCoverage;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.AnalystAssignmentResponse;
import com.studentleague.matches.dto.AnalystDutyResponse;
import com.studentleague.matches.dto.AnalystPersonResponse;
import com.studentleague.matches.dto.AssignAnalystsRequest;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchAnalystAssignment;
import com.studentleague.matches.repository.MatchAnalystAssignmentRepository;
import com.studentleague.matches.repository.MatchRefereeRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.security.UserPrincipal;
import com.studentleague.tournaments.domain.TournamentStatus;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.users.domain.Role;
import com.studentleague.users.domain.RoleStatus;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import com.studentleague.users.service.RoleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AnalystAssignmentService {

    private final MatchRepository matchRepository;
    private final MatchAnalystAssignmentRepository assignmentRepository;
    private final MatchRefereeRepository matchRefereeRepository;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final TournamentRepository tournamentRepository;

    public AnalystAssignmentService(
            MatchRepository matchRepository,
            MatchAnalystAssignmentRepository assignmentRepository,
            MatchRefereeRepository matchRefereeRepository,
            UserRepository userRepository,
            RoleService roleService,
            TournamentRepository tournamentRepository
    ) {
        this.matchRepository = matchRepository;
        this.assignmentRepository = assignmentRepository;
        this.matchRefereeRepository = matchRefereeRepository;
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.tournamentRepository = tournamentRepository;
    }

    @Transactional(readOnly = true)
    public AnalystAssignmentResponse get(UUID matchId) {
        requireMatch(matchId);
        return assignmentRepository.findById(matchId)
                .map(this::toResponse)
                .orElseGet(() -> empty(matchId));
    }

    @Transactional
    public AnalystAssignmentResponse assign(UserPrincipal principal, UUID matchId, AssignAnalystsRequest request) {
        requireMatch(matchId);
        assertCanAssign(principal, matchId);
        MatchAnalystAssignment row = assignmentRepository.findById(matchId).orElseGet(() -> {
            MatchAnalystAssignment created = new MatchAnalystAssignment();
            created.setMatchId(matchId);
            return created;
        });
        if (request.mode() == AnalystAssignmentMode.BOTH) {
            User analyst = requireAnalyst(request.userId());
            row.setMode(AnalystAssignmentMode.BOTH);
            row.setBothUserId(analyst.getId());
            row.setHomeUserId(null);
            row.setAwayUserId(null);
        } else {
            if (request.homeUserId() == null || request.awayUserId() == null) {
                throw ApiException.badRequest("Выберите аналитика на каждую команду");
            }
            if (request.homeUserId().equals(request.awayUserId())) {
                throw ApiException.badRequest("На команды нужны разные аналитики");
            }
            User home = requireAnalyst(request.homeUserId());
            User away = requireAnalyst(request.awayUserId());
            row.setMode(AnalystAssignmentMode.SPLIT);
            row.setBothUserId(null);
            row.setHomeUserId(home.getId());
            row.setAwayUserId(away.getId());
        }
        return toResponse(assignmentRepository.save(row));
    }

    @Transactional
    public void clear(UserPrincipal principal, UUID matchId) {
        requireMatch(matchId);
        assertCanAssign(principal, matchId);
        assignmentRepository.findById(matchId).ifPresent(assignmentRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<AnalystPersonResponse> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return userRepository.searchReferees(
                        q,
                        Role.ANALYST,
                        RoleStatus.APPROVED,
                        PageRequest.of(0, 15, Sort.by("lastName", "firstName", "email"))
                )
                .map(this::toPerson)
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<AnalystDutyResponse> duties(UserPrincipal principal) {
        if (principal != null && principal.hasRole(Role.ADMIN)) {
            return currentMatches().stream()
                    .map(match -> toDuty(match, AnalystCoverage.BOTH))
                    .toList();
        }
        if (principal == null) {
            return List.of();
        }
        return assignmentRepository.findByUserId(principal.getId()).stream()
                .map(row -> {
                    Match match = matchRepository.findById(row.getMatchId()).orElse(null);
                    AnalystCoverage coverage = coverageOf(row, principal.getId());
                    if (match == null || coverage == null) {
                        return null;
                    }
                    return toDuty(match, coverage);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Admin covers both teams. An analyst covers only the side they were given.
     */
    @Transactional(readOnly = true)
    public AnalystCoverage coverage(UserPrincipal principal, UUID matchId) {
        if (principal != null && principal.hasRole(Role.ADMIN)) {
            return AnalystCoverage.BOTH;
        }
        if (principal == null) {
            return null;
        }
        return assignmentRepository.findById(matchId)
                .map(row -> coverageOf(row, principal.getId()))
                .orElse(null);
    }

    private AnalystCoverage coverageOf(MatchAnalystAssignment row, UUID userId) {
        if (row.getMode() == AnalystAssignmentMode.BOTH && userId.equals(row.getBothUserId())) {
            return AnalystCoverage.BOTH;
        }
        if (row.getMode() == AnalystAssignmentMode.SPLIT && userId.equals(row.getHomeUserId())) {
            return AnalystCoverage.HOME;
        }
        if (row.getMode() == AnalystAssignmentMode.SPLIT && userId.equals(row.getAwayUserId())) {
            return AnalystCoverage.AWAY;
        }
        return null;
    }

    private List<Match> currentMatches() {
        List<Tournament> active = tournamentRepository.findByStatusOrderByStartDateDescCreatedAtDesc(TournamentStatus.ACTIVE);
        Tournament tournament = !active.isEmpty()
                ? active.getFirst()
                : tournamentRepository.findAllByOrderByStartDateDescCreatedAtDesc().stream().findFirst().orElse(null);
        if (tournament == null) {
            return List.of();
        }
        return matchRepository.findByTournamentId(tournament.getId());
    }

    private AnalystDutyResponse toDuty(Match match, AnalystCoverage coverage) {
        boolean editable = match.getStatus() != MatchStatus.FINISHED && match.getStatus() != MatchStatus.CANCELLED;
        return new AnalystDutyResponse(
                match.getId(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getHomeScore(),
                match.getAwayScore(),
                match.getStatus(),
                match.getScheduledAt(),
                MatchListFields.minute(match, Instant.now()),
                coverage,
                editable
        );
    }

    private User requireAnalyst(UUID userId) {
        if (userId == null) {
            throw ApiException.badRequest("Выберите аналитика");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
        if (!roleService.hasApproved(userId, Role.ANALYST)) {
            throw ApiException.badRequest("Сначала отметьте роль Аналитик");
        }
        return user;
    }

    private void assertCanAssign(UserPrincipal principal, UUID matchId) {
        if (principal != null && principal.hasRole(Role.ADMIN)) {
            return;
        }
        if (principal != null && matchRefereeRepository.existsByMatchIdAndRefereeId(matchId, principal.getId())) {
            return;
        }
        throw ApiException.forbidden("Назначать аналитика может судья этого матча или админ");
    }

    private Match requireMatch(UUID matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Матч не найден"));
    }

    private AnalystAssignmentResponse toResponse(MatchAnalystAssignment row) {
        return new AnalystAssignmentResponse(
                row.getMatchId(),
                row.getMode(),
                person(row.getBothUserId()),
                person(row.getHomeUserId()),
                person(row.getAwayUserId())
        );
    }

    private AnalystAssignmentResponse empty(UUID matchId) {
        return new AnalystAssignmentResponse(matchId, null, null, null, null);
    }

    private AnalystPersonResponse person(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(this::toPerson).orElse(null);
    }

    private AnalystPersonResponse toPerson(User user) {
        return new AnalystPersonResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail());
    }
}
