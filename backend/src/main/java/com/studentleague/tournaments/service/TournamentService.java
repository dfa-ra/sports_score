package com.studentleague.tournaments.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.matches.repository.MatchRefereeRepository;
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
import com.studentleague.tournaments.dto.ReplaceTournamentTablesRequest;
import com.studentleague.tournaments.dto.StandingRow;
import com.studentleague.tournaments.dto.StandingTableResponse;
import com.studentleague.tournaments.dto.TournamentFormatResponse;
import com.studentleague.tournaments.dto.TournamentResponse;
import com.studentleague.tournaments.dto.TournamentStandingsResponse;
import com.studentleague.tournaments.dto.TournamentTableResponse;
import com.studentleague.tournaments.dto.TournamentTableWriteRequest;
import com.studentleague.tournaments.dto.TournamentTeamResponse;
import com.studentleague.tournaments.dto.UpdateTournamentRequest;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.entity.TournamentTable;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.format.StandingsContext;
import com.studentleague.tournaments.format.TournamentFormatRegistry;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTableRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import com.studentleague.users.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TournamentTableRepository tournamentTableRepository;
    private final SportRepository sportRepository;
    private final TeamRepository teamRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchLineupPlayerRepository matchLineupPlayerRepository;
    private final MatchRefereeRepository matchRefereeRepository;
    private final NotificationService notificationService;
    private final TournamentFormatRegistry formatRegistry;

    public TournamentService(
            TournamentRepository tournamentRepository,
            TournamentTeamRepository tournamentTeamRepository,
            TournamentTableRepository tournamentTableRepository,
            SportRepository sportRepository,
            TeamRepository teamRepository,
            PlayerProfileRepository playerProfileRepository,
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            MatchLineupPlayerRepository matchLineupPlayerRepository,
            MatchRefereeRepository matchRefereeRepository,
            NotificationService notificationService,
            TournamentFormatRegistry formatRegistry
    ) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.tournamentTableRepository = tournamentTableRepository;
        this.sportRepository = sportRepository;
        this.teamRepository = teamRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
        this.matchLineupPlayerRepository = matchLineupPlayerRepository;
        this.matchRefereeRepository = matchRefereeRepository;
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
        requireTournament(tournamentId);
        TournamentTeam entry = tournamentTeamRepository.findByTournamentIdAndTeamId(tournamentId, teamId)
                .orElseThrow(() -> ApiException.notFound("Tournament registration not found"));
        List<Match> fixtures = matchRepository.findByTournamentIdAndTeamId(tournamentId, teamId);
        for (Match match : fixtures) {
            if (match.getStatus() != MatchStatus.SCHEDULED && match.getStatus() != MatchStatus.CANCELLED) {
                continue;
            }
            matchEventRepository.deleteByMatchId(match.getId());
            matchLineupPlayerRepository.deleteByMatchId(match.getId());
            matchRefereeRepository.deleteByMatchId(match.getId());
            matchRepository.delete(match);
        }
        tournamentTeamRepository.delete(entry);
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
    public List<TournamentTableResponse> listTables(UUID tournamentId) {
        requireTournament(tournamentId);
        List<TournamentTeam> entries = tournamentTeamRepository.findByTournamentId(tournamentId);
        return tournamentTableRepository.findByTournamentIdOrderBySortOrderAscIdAsc(tournamentId).stream()
                .map(table -> toTableResponse(table, entries))
                .toList();
    }

    @Transactional
    public List<TournamentTableResponse> replaceTables(UUID tournamentId, ReplaceTournamentTablesRequest request) {
        requireTournament(tournamentId);
        List<TournamentTableWriteRequest> specs = request.tables() == null ? List.of() : request.tables();
        if (specs.size() > 16) {
            throw ApiException.badRequest("Не больше 16 таблиц в турнире");
        }

        List<TournamentTeam> entries = tournamentTeamRepository.findByTournamentId(tournamentId);
        Map<UUID, TournamentTeam> byTeamId = entries.stream()
                .collect(Collectors.toMap(TournamentTeam::getTeamId, Function.identity()));
        Set<UUID> seen = new HashSet<>();
        for (TournamentTableWriteRequest spec : specs) {
            String name = spec.name() == null ? "" : spec.name().trim();
            if (name.isBlank()) {
                throw ApiException.badRequest("У каждой таблицы должно быть название");
            }
            List<UUID> teamIds = spec.teamIds() == null ? List.of() : spec.teamIds();
            for (UUID teamId : teamIds) {
                if (!seen.add(teamId)) {
                    throw ApiException.badRequest("Команда не может быть сразу в двух таблицах");
                }
                if (!byTeamId.containsKey(teamId)) {
                    throw ApiException.badRequest("Команда не заявлена в этот турнир");
                }
            }
        }

        for (TournamentTeam entry : entries) {
            entry.setTableId(null);
        }
        tournamentTeamRepository.saveAll(entries);
        tournamentTableRepository.deleteAll(
                tournamentTableRepository.findByTournamentIdOrderBySortOrderAscIdAsc(tournamentId)
        );
        tournamentTableRepository.flush();

        List<TournamentTable> created = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            TournamentTableWriteRequest spec = specs.get(i);
            TournamentTable table = new TournamentTable();
            table.setTournamentId(tournamentId);
            table.setName(spec.name().trim());
            table.setSortOrder(i);
            created.add(tournamentTableRepository.save(table));
            List<UUID> teamIds = spec.teamIds() == null ? List.of() : spec.teamIds();
            for (UUID teamId : teamIds) {
                byTeamId.get(teamId).setTableId(table.getId());
            }
        }
        tournamentTeamRepository.saveAll(entries);
        return created.stream().map(table -> toTableResponse(table, entries)).toList();
    }

    @Transactional(readOnly = true)
    public TournamentStandingsResponse standings(UUID tournamentId) {
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
        teams.values().removeIf(Objects::isNull);

        List<TournamentTable> tables = tournamentTableRepository.findByTournamentIdOrderBySortOrderAscIdAsc(tournamentId);
        if (tables.isEmpty()) {
            List<StandingRow> rows = formatRegistry.handler(tournament.getFormat())
                    .standings(new StandingsContext(tournament, entries, finished, teams));
            return new TournamentStandingsResponse(List.of(new StandingTableResponse(null, null, 0, rows)));
        }

        List<StandingTableResponse> result = new ArrayList<>();
        for (TournamentTable table : tables) {
            List<TournamentTeam> groupEntries = entries.stream()
                    .filter(entry -> table.getId().equals(entry.getTableId()))
                    .toList();
            Set<UUID> groupTeamIds = groupEntries.stream()
                    .map(TournamentTeam::getTeamId)
                    .collect(Collectors.toSet());
            List<Match> intra = finished.stream()
                    .filter(match -> groupTeamIds.contains(match.getHomeTeamId())
                            && groupTeamIds.contains(match.getAwayTeamId()))
                    .toList();
            List<StandingRow> rows = formatRegistry.handler(tournament.getFormat())
                    .standings(new StandingsContext(tournament, groupEntries, intra, teams));
            result.add(new StandingTableResponse(table.getId(), table.getName(), table.getSortOrder(), rows));
        }
        return new TournamentStandingsResponse(result);
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
                entry.getTableId(), entry.getStatus(), entry.getRegisteredAt(), entry.getApprovedAt()
        );
    }

    private TournamentTableResponse toTableResponse(TournamentTable table, List<TournamentTeam> entries) {
        List<UUID> teamIds = entries.stream()
                .filter(entry -> table.getId().equals(entry.getTableId()))
                .map(TournamentTeam::getTeamId)
                .toList();
        return new TournamentTableResponse(
                table.getId(), table.getTournamentId(), table.getName(), table.getSortOrder(), teamIds
        );
    }

}

