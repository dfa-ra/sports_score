package com.studentleague.tournaments.service;

import com.studentleague.cache.StandingsBoardCache;
import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.ForfeitRules;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.matches.repository.MatchRefereeRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.notifications.NotificationEventType;
import com.studentleague.notifications.NotificationService;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.security.CurrentUser;
import com.studentleague.security.UserPrincipal;
import com.studentleague.sports.repository.SportRepository;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.TournamentCopy;
import com.studentleague.tournaments.domain.CompetitionStage;
import com.studentleague.tournaments.domain.TournamentFormat;
import com.studentleague.tournaments.domain.TournamentStatus;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.dto.CreateTournamentRequest;
import com.studentleague.tournaments.dto.RegisterTeamRequest;
import com.studentleague.tournaments.dto.ReplaceTournamentTablesRequest;
import com.studentleague.tournaments.dto.StandingRow;
import com.studentleague.tournaments.dto.StandingTableResponse;
import com.studentleague.tournaments.dto.TournamentFormatResponse;
import com.studentleague.tournaments.dto.TournamentOverviewResponse;
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
import com.studentleague.tournaments.repository.PlayoffSlotRepository;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTableRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import com.studentleague.users.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TournamentTableRepository tournamentTableRepository;
    private final PlayoffSlotRepository playoffSlotRepository;
    private final SportRepository sportRepository;
    private final TeamRepository teamRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchLineupPlayerRepository matchLineupPlayerRepository;
    private final MatchRefereeRepository matchRefereeRepository;
    private final NotificationService notificationService;
    private final TournamentFormatRegistry formatRegistry;
    private final StandingsBoardCache standingsBoardCache;
    private final TournamentRosterGuard tournamentRosterGuard;

    public TournamentService(
            TournamentRepository tournamentRepository,
            TournamentTeamRepository tournamentTeamRepository,
            TournamentTableRepository tournamentTableRepository,
            PlayoffSlotRepository playoffSlotRepository,
            SportRepository sportRepository,
            TeamRepository teamRepository,
            PlayerProfileRepository playerProfileRepository,
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            MatchLineupPlayerRepository matchLineupPlayerRepository,
            MatchRefereeRepository matchRefereeRepository,
            NotificationService notificationService,
            TournamentFormatRegistry formatRegistry,
            StandingsBoardCache standingsBoardCache,
            TournamentRosterGuard tournamentRosterGuard
    ) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.tournamentTableRepository = tournamentTableRepository;
        this.playoffSlotRepository = playoffSlotRepository;
        this.sportRepository = sportRepository;
        this.teamRepository = teamRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
        this.matchLineupPlayerRepository = matchLineupPlayerRepository;
        this.matchRefereeRepository = matchRefereeRepository;
        this.notificationService = notificationService;
        this.formatRegistry = formatRegistry;
        this.standingsBoardCache = standingsBoardCache;
        this.tournamentRosterGuard = tournamentRosterGuard;
    }

    @Transactional
    public TournamentResponse create(CreateTournamentRequest request) {
        sportRepository.findById(request.sportId())
                .orElseThrow(() -> ApiException.notFound("Вид спорта не найден"));
        requireChronologicalDates(request.startDate(), request.endDate());
        Tournament tournament = new Tournament();
        tournament.setName(request.name().trim());
        tournament.setDescription(TournamentCopy.storedDescription(request.description()));
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
        LocalDate startDate = request.startDate() != null ? request.startDate() : tournament.getStartDate();
        LocalDate endDate = request.endDate() != null ? request.endDate() : tournament.getEndDate();
        requireChronologicalDates(startDate, endDate);
        if (request.name() != null && !request.name().isBlank()) {
            tournament.setName(request.name().trim());
        }
        if (request.description() != null) {
            tournament.setDescription(TournamentCopy.storedDescription(request.description()));
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
    public TournamentOverviewResponse overview(UUID id) {
        Tournament tournament = requireTournament(id);
        int teamsCount = (int) tournamentTeamRepository.countByTournamentIdAndStatusNot(id, TournamentTeamStatus.WITHDRAWN);
        int matchesCount = (int) matchRepository.countByTournamentId(id);
        boolean hasSlots = playoffSlotRepository.countByTournamentId(id) > 0;
        List<UUID> matchIds = playoffSlotRepository.findMatchIdsByTournamentId(id);
        boolean playoffLive = false;
        if (!matchIds.isEmpty()) {
            for (Match match : matchRepository.findAllById(matchIds)) {
                if (match.getStatus() == MatchStatus.LIVE || match.getStatus() == MatchStatus.PAUSED) {
                    playoffLive = true;
                    break;
                }
            }
        }
        boolean hasPlayoff = hasSlots
                || tournament.getCompetitionStage() == CompetitionStage.PLAYOFF
                || formatIncludesPlayoff(tournament.getFormat());
        return new TournamentOverviewResponse(
                tournament.getId(),
                tournament.getName(),
                tournament.getStatus().name(),
                tournament.getFormat(),
                tournament.getSeasonYear(),
                tournament.getStartDate(),
                tournament.getEndDate(),
                teamsCount,
                matchesCount,
                tournament.getCompetitionStage().name(),
                hasPlayoff,
                playoffLive
        );
    }

    private static boolean formatIncludesPlayoff(String format) {
        if (format == null) {
            return false;
        }
        return switch (format) {
            case "CUP", "GROUPS_PLAYOFF", "DOUBLE_ELIMINATION", "KNOCKOUT" -> true;
            default -> false;
        };
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
        Team team = teamRepository.findById(request.teamId())
                .orElseThrow(() -> ApiException.notFound("Команда не найдена"));
        if (!principal.hasRole(Role.ADMIN)) {
            var profile = playerProfileRepository.findByUserId(principal.getId())
                    .orElseThrow(() -> ApiException.forbidden("Команду заявляет только её капитан"));
            if (!profile.getId().equals(team.getCaptainId())) {
                throw ApiException.forbidden("Команду заявляет только её капитан");
            }
        }
        if (tournament.getStatus() != TournamentStatus.REGISTRATION && tournament.getStatus() != TournamentStatus.DRAFT) {
            throw ApiException.badRequest("Запись на турнир закрыта");
        }
        if (team.isDisbanded()) {
            throw ApiException.badRequest("Нельзя заявить расформированную команду");
        }

        Optional<TournamentTeam> existing = tournamentTeamRepository.findByTournamentIdAndTeamId(tournamentId, team.getId());
        if (existing.isPresent() && TournamentRosterGuard.isEntered(existing.get().getStatus())) {
            throw ApiException.conflict("Команда уже заявлена на этот турнир");
        }
        tournamentRosterGuard.rejectSquadAlreadyInTournament(tournamentId, team.getId());

        TournamentTeam entry = existing.orElseGet(TournamentTeam::new);
        if (entry.getId() == null) {
            entry.setTournamentId(tournamentId);
            entry.setTeamId(team.getId());
        }
        entry.setStatus(TournamentTeamStatus.PENDING);
        entry.setApprovedAt(null);
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
                .orElseThrow(() -> ApiException.notFound("Заявка команды на турнир не найдена"));
        if (entry.getStatus() != TournamentTeamStatus.PENDING) {
            throw ApiException.conflict("Заявку можно одобрить только в статусе «На рассмотрении»");
        }
        tournamentRosterGuard.rejectSquadAlreadyInTournament(tournamentId, teamId);
        assignSoleTable(tournamentId, entry);
        entry.setStatus(TournamentTeamStatus.APPROVED);
        entry.setApprovedAt(Instant.now());
        tournamentTeamRepository.save(entry);
        standingsBoardCache.invalidateTournament(tournamentId);
        String name = teamRepository.findById(teamId).map(Team::getName).orElse(null);
        return toTeamResponse(entry, name);
    }

    @Transactional
    public TournamentTeamResponse restoreTeam(UUID tournamentId, UUID teamId) {
        requireTournament(tournamentId);
        TournamentTeam entry = tournamentTeamRepository.findByTournamentIdAndTeamId(tournamentId, teamId)
                .orElseThrow(() -> ApiException.notFound("Заявка команды на турнир не найдена"));
        if (entry.getStatus() != TournamentTeamStatus.WITHDRAWN) {
            throw ApiException.conflict("Вернуть можно только снятую заявку");
        }
        tournamentRosterGuard.rejectSquadAlreadyInTournament(tournamentId, teamId);
        entry.setStatus(TournamentTeamStatus.PENDING);
        entry.setApprovedAt(null);
        tournamentTeamRepository.save(entry);
        String name = teamRepository.findById(teamId).map(Team::getName).orElse(null);
        return toTeamResponse(entry, name);
    }

    @Transactional
    public void excludeTeam(UUID tournamentId, UUID teamId) {
        requireTournament(tournamentId);
        TournamentTeam entry = tournamentTeamRepository.findByTournamentIdAndTeamId(tournamentId, teamId)
                .orElseThrow(() -> ApiException.notFound("Заявка команды на турнир не найдена"));
        List<Match> fixtures = matchRepository.findByTournamentIdAndTeamId(tournamentId, teamId);
        for (Match match : fixtures) {
            if (match.getBracketRound() != null) {
                throw ApiException.badRequest("Команда уже стоит в сетке плей-офф");
            }
            if (match.getStatus() != MatchStatus.SCHEDULED && match.getStatus() != MatchStatus.CANCELLED) {
                continue;
            }
            matchEventRepository.deleteByMatchId(match.getId());
            matchLineupPlayerRepository.deleteByMatchId(match.getId());
            matchRefereeRepository.deleteByMatchId(match.getId());
            matchRepository.delete(match);
        }
        entry.setStatus(TournamentTeamStatus.WITHDRAWN);
        entry.setApprovedAt(null);
        entry.setTableId(null);
        tournamentTeamRepository.save(entry);
        standingsBoardCache.invalidateTournament(tournamentId);
    }

    @Transactional(readOnly = true)
    public List<TournamentTeamResponse> listTeams(UUID tournamentId) {
        requireTournament(tournamentId);
        boolean includeWithdrawn = CurrentUser.isAdmin();
        return tournamentTeamRepository.findByTournamentId(tournamentId).stream()
                .filter(entry -> includeWithdrawn || entry.getStatus() != TournamentTeamStatus.WITHDRAWN)
                .map(entry -> {
                    String name = teamRepository.findById(entry.getTeamId()).map(Team::getName).orElse(null);
                    return toTeamResponse(entry, name);
                })
                .toList();
    }

    /**
     * Only an approved entry may sit in a group. Checked before any tableId write.
     */
    private static String tableAssignmentRefusal(TournamentTeamStatus status) {
        if (status == TournamentTeamStatus.WITHDRAWN) {
            return "Команда снята с турнира";
        }
        if (status == TournamentTeamStatus.REJECTED) {
            return "Заявка отклонена";
        }
        if (status == TournamentTeamStatus.PENDING) {
            return "Заявка ещё на рассмотрении";
        }
        return "В таблицу попадают только одобренные команды";
    }

    /**
     * A tournament with a single group puts every newly approved club into it.
     * Several groups stay a manual assignment, same as a club approved for the first time.
     */
    private void assignSoleTable(UUID tournamentId, TournamentTeam entry) {
        if (entry.getTableId() != null) {
            return;
        }
        List<TournamentTable> tables = tournamentTableRepository.findByTournamentIdOrderBySortOrderAscIdAsc(tournamentId);
        if (tables.size() == 1) {
            entry.setTableId(tables.getFirst().getId());
        }
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
        Tournament tournament = requireTournament(tournamentId);
        if (tournament.getCompetitionStage() == CompetitionStage.PLAYOFF) {
            throw ApiException.badRequest("Группы закрыты: турнир уже в плей-офф");
        }
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
                TournamentTeam entry = byTeamId.get(teamId);
                if (entry == null) {
                    throw ApiException.badRequest("Команда не заявлена в этот турнир");
                }
                if (entry.getStatus() != TournamentTeamStatus.APPROVED) {
                    throw ApiException.conflict(tableAssignmentRefusal(entry.getStatus()));
                }
            }
        }

        Map<UUID, TournamentTable> existingById = tournamentTableRepository
                .findByTournamentIdOrderBySortOrderAscIdAsc(tournamentId).stream()
                .collect(Collectors.toMap(TournamentTable::getId, Function.identity()));
        Set<UUID> keep = new HashSet<>();
        for (TournamentTableWriteRequest spec : specs) {
            if (spec.id() != null && existingById.containsKey(spec.id())) {
                keep.add(spec.id());
            }
        }
        for (TournamentTeam entry : entries) {
            entry.setTableId(null);
        }
        tournamentTeamRepository.saveAll(entries);
        List<TournamentTable> removed = existingById.values().stream()
                .filter(table -> !keep.contains(table.getId()))
                .toList();
        if (!removed.isEmpty()) {
            tournamentTableRepository.deleteAll(removed);
            tournamentTableRepository.flush();
        }

        List<TournamentTable> created = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            TournamentTableWriteRequest spec = specs.get(i);
            TournamentTable table = spec.id() != null ? existingById.get(spec.id()) : null;
            if (table == null || !keep.contains(table.getId())) {
                table = new TournamentTable();
                table.setTournamentId(tournamentId);
            }
            table.setName(spec.name().trim());
            table.setSortOrder(i);
            created.add(tournamentTableRepository.save(table));
            List<UUID> teamIds = spec.teamIds() == null ? List.of() : spec.teamIds();
            for (UUID teamId : teamIds) {
                byTeamId.get(teamId).setTableId(table.getId());
            }
        }
        tournamentTeamRepository.saveAll(entries);
        standingsBoardCache.invalidateTournament(tournamentId);
        return created.stream().map(table -> toTableResponse(table, entries)).toList();
    }

    @Transactional(readOnly = true)
    public TournamentStandingsResponse standings(UUID tournamentId) {
        return standingsBoardCache.standings(tournamentId, () -> loadStandings(tournamentId));
    }

    private TournamentStandingsResponse loadStandings(UUID tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        List<TournamentTeam> entries = tournamentTeamRepository.findByTournamentId(tournamentId);
        List<Match> finished = matchRepository.findByTournamentIdAndStatus(tournamentId, MatchStatus.FINISHED).stream()
                .filter(match -> match.getBracketRound() == null)
                .toList();
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
            return new TournamentStandingsResponse(List.of(new StandingTableResponse(
                    null, null, 0, rows, mutualForfeit(finished))));
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
                    .filter(match -> table.getId().equals(match.getTableId())
                            || (match.getTableId() == null
                            && groupTeamIds.contains(match.getHomeTeamId())
                            && groupTeamIds.contains(match.getAwayTeamId())))
                    .toList();
            List<StandingRow> rows = formatRegistry.handler(tournament.getFormat())
                    .standings(new StandingsContext(tournament, groupEntries, intra, teams));
            result.add(new StandingTableResponse(
                    table.getId(), table.getName(), table.getSortOrder(), rows, mutualForfeit(intra)));
        }
        return new TournamentStandingsResponse(result);
    }

    private static boolean mutualForfeit(List<Match> matches) {
        for (Match match : matches) {
            if (ForfeitRules.mutual(match)) {
                return true;
            }
        }
        return false;
    }

    private void requireChronologicalDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw ApiException.badRequest("Дата финиша не может быть раньше даты старта.");
        }
    }

    private Tournament requireTournament(UUID id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Турнир не найден"));
    }

    private TournamentResponse toResponse(Tournament t) {
        return new TournamentResponse(
                t.getId(), t.getName(), TournamentCopy.storedDescription(t.getDescription()), t.getRegulations(), t.getSportId(), t.getSeasonYear(),
                t.getStartDate(), t.getEndDate(), t.getStatus(), t.getFormat(), t.getMaxSquadSize(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getCompetitionStage()
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

