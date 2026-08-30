package com.studentleague.tournaments.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.notifications.NotificationEventType;
import com.studentleague.notifications.NotificationService;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.security.UserPrincipal;
import com.studentleague.sports.repository.SportRepository;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.domain.TournamentFormat;
import com.studentleague.tournaments.domain.TournamentStatus;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.dto.CreateTournamentRequest;
import com.studentleague.tournaments.dto.RegisterTeamRequest;
import com.studentleague.tournaments.dto.StandingRow;
import com.studentleague.tournaments.dto.TournamentFormatResponse;
import com.studentleague.tournaments.dto.TournamentResponse;
import com.studentleague.tournaments.dto.TournamentTeamResponse;
import com.studentleague.tournaments.dto.UpdateTournamentRequest;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.format.StandingsContext;
import com.studentleague.tournaments.format.TournamentFormatRegistry;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import com.studentleague.users.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final SportRepository sportRepository;
    private final TeamRepository teamRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final MatchRepository matchRepository;
    private final NotificationService notificationService;
    private final TournamentFormatRegistry formatRegistry;

    public TournamentService(
            TournamentRepository tournamentRepository,
            TournamentTeamRepository tournamentTeamRepository,
            SportRepository sportRepository,
            TeamRepository teamRepository,
            PlayerProfileRepository playerProfileRepository,
            MatchRepository matchRepository,
            NotificationService notificationService,
            TournamentFormatRegistry formatRegistry
    ) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.sportRepository = sportRepository;
        this.teamRepository = teamRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.matchRepository = matchRepository;
        this.notificationService = notificationService;
        this.formatRegistry = formatRegistry;
    }

    @Transactional
    public TournamentResponse create(CreateTournamentRequest request) {
        sportRepository.findById(request.sportId())
                .orElseThrow(() -> ApiException.notFound("Sport not found"));
        Tournament tournament = new Tournament();
        tournament.setName(request.name().trim());
        tournament.setDescription(request.description());
        tournament.setRegulations(request.regulations());
        tournament.setSportId(request.sportId());
        tournament.setSeasonYear(request.seasonYear());
        tournament.setStartDate(request.startDate());
        tournament.setEndDate(request.endDate());
        tournament.setStatus(request.status() == null ? TournamentStatus.DRAFT : request.status());
        tournament.setFormat(TournamentFormat.normalize(request.format()));
        tournament.setMaxSquadSize(request.maxSquadSize());
        return toResponse(tournamentRepository.save(tournament));
    }

    @Transactional
    public TournamentResponse update(UUID id, UpdateTournamentRequest request) {
        Tournament tournament = requireTournament(id);
        if (request.name() != null && !request.name().isBlank()) {
            tournament.setName(request.name().trim());
        }
        if (request.description() != null) {
            tournament.setDescription(request.description());
        }
        if (request.regulations() != null) {
            tournament.setRegulations(request.regulations());
        }
        if (request.maxSquadSize() != null) {
            tournament.setMaxSquadSize(request.maxSquadSize());
        }
        if (request.seasonYear() != null) {
            tournament.setSeasonYear(request.seasonYear());
        }
        if (request.startDate() != null) {
            tournament.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            tournament.setEndDate(request.endDate());
        }
        if (request.status() != null) {
            tournament.setStatus(request.status());
        }
        if (request.format() != null && !request.format().isBlank()) {
            tournament.setFormat(TournamentFormat.normalize(request.format()));
        }
        return toResponse(tournamentRepository.save(tournament));
    }

    @Transactional(readOnly = true)
    public TournamentResponse get(UUID id) {
        return toResponse(requireTournament(id));
    }

    @Transactional(readOnly = true)
    public Page<TournamentResponse> list(TournamentStatus status, UUID sportId, Pageable pageable) {
        Page<Tournament> page;
        if (status != null && sportId != null) {
            page = tournamentRepository.findByStatusAndSportId(status, sportId, pageable);
        } else if (status != null) {
            page = tournamentRepository.findByStatus(status, pageable);
        } else if (sportId != null) {
            page = tournamentRepository.findBySportId(sportId, pageable);
        } else {
            page = tournamentRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional
    public TournamentTeamResponse registerTeam(UserPrincipal principal, UUID tournamentId, RegisterTeamRequest request) {
        Tournament tournament = requireTournament(tournamentId);
        if (tournament.getStatus() != TournamentStatus.REGISTRATION && tournament.getStatus() != TournamentStatus.DRAFT) {
            throw ApiException.badRequest("Tournament is not open for registration");
        }
        Team team = teamRepository.findById(request.teamId())
                .orElseThrow(() -> ApiException.notFound("Team not found"));
        if (team.isDisbanded()) {
            throw ApiException.badRequest("Нельзя заявить расформированную команду");
        }

        if (!principal.hasRole(Role.ADMIN)) {
            var profile = playerProfileRepository.findByUserId(principal.getId())
                    .orElseThrow(() -> ApiException.forbidden("Only the team captain can register the team"));
            if (!profile.getId().equals(team.getCaptainId())) {
                throw ApiException.forbidden("Only the team captain can register the team");
            }
        }

        if (tournamentTeamRepository.existsByTournamentIdAndTeamId(tournamentId, team.getId())) {
            throw ApiException.conflict("Team already registered for this tournament");
        }

        TournamentTeam entry = new TournamentTeam();
        entry.setTournamentId(tournamentId);
        entry.setTeamId(team.getId());
        entry.setStatus(TournamentTeamStatus.PENDING);
        tournamentTeamRepository.save(entry);
        notificationService.publishToUser(
                principal.getId(),
                NotificationEventType.TOURNAMENT_REGISTRATION,
                "Tournament registration submitted",
                team.getName() + " registered for " + tournament.getName(),
                Map.of(
                        "tournamentId", tournamentId.toString(),
                        "teamId", team.getId().toString()
                )
        );
        return toTeamResponse(entry, team.getName());
    }

    @Transactional
    public TournamentTeamResponse approveTeam(UUID tournamentId, UUID teamId) {
        TournamentTeam entry = tournamentTeamRepository.findByTournamentIdAndTeamId(tournamentId, teamId)
                .orElseThrow(() -> ApiException.notFound("Tournament registration not found"));
        entry.setStatus(TournamentTeamStatus.APPROVED);
        entry.setApprovedAt(Instant.now());
        tournamentTeamRepository.save(entry);
        String name = teamRepository.findById(teamId).map(Team::getName).orElse(null);
        return toTeamResponse(entry, name);
    }

    @Transactional
    public void excludeTeam(UUID tournamentId, UUID teamId) {
        TournamentTeam entry = tournamentTeamRepository.findByTournamentIdAndTeamId(tournamentId, teamId)
                .orElseThrow(() -> ApiException.notFound("Tournament registration not found"));
        entry.setStatus(TournamentTeamStatus.WITHDRAWN);
        tournamentTeamRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<TournamentTeamResponse> listTeams(UUID tournamentId) {
        requireTournament(tournamentId);
        return tournamentTeamRepository.findByTournamentId(tournamentId).stream()
                .map(entry -> {
                    String name = teamRepository.findById(entry.getTeamId()).map(Team::getName).orElse(null);
                    return toTeamResponse(entry, name);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public TournamentResponse current() {
        List<Tournament> active = tournamentRepository.findByStatusOrderByStartDateDescCreatedAtDesc(TournamentStatus.ACTIVE);
        if (!active.isEmpty()) {
            return toResponse(active.getFirst());
        }
        List<Tournament> all = tournamentRepository.findAllByOrderByStartDateDescCreatedAtDesc();
        if (all.isEmpty()) {
            return null;
        }
        return toResponse(all.getFirst());
    }

    @Transactional(readOnly = true)
    public List<TournamentFormatResponse> formats() {
        return formatRegistry.list();
    }

    @Transactional(readOnly = true)
    public List<StandingRow> standings(UUID tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        List<TournamentTeam> entries = tournamentTeamRepository.findByTournamentId(tournamentId);
        List<Match> finished = matchRepository.findByTournamentIdAndStatus(tournamentId, MatchStatus.FINISHED);
        Map<UUID, Team> teams = teamRepository.findAllById(
                entries.stream().map(TournamentTeam::getTeamId).toList()
        ).stream().collect(Collectors.toMap(Team::getId, Function.identity()));
        for (Match match : finished) {
            teams.computeIfAbsent(match.getHomeTeamId(), id -> teamRepository.findById(id).orElse(null));
            teams.computeIfAbsent(match.getAwayTeamId(), id -> teamRepository.findById(id).orElse(null));
        }
        teams.values().removeIf(java.util.Objects::isNull);
        return formatRegistry.handler(tournament.getFormat())
                .standings(new StandingsContext(tournament, entries, finished, teams));
    }

    private Tournament requireTournament(UUID id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Tournament not found"));
    }

    private TournamentResponse toResponse(Tournament t) {
        return new TournamentResponse(
                t.getId(), t.getName(), t.getDescription(), t.getRegulations(), t.getSportId(), t.getSeasonYear(),
                t.getStartDate(), t.getEndDate(), t.getStatus(), t.getFormat(), t.getMaxSquadSize(),
                t.getCreatedAt(), t.getUpdatedAt()
        );
    }

    private TournamentTeamResponse toTeamResponse(TournamentTeam entry, String teamName) {
        return new TournamentTeamResponse(
                entry.getId(), entry.getTournamentId(), entry.getTeamId(), teamName,
                entry.getStatus(), entry.getRegisteredAt(), entry.getApprovedAt()
        );
    }

}

