package com.studentleague.tournaments.service;

import com.studentleague.cache.StandingsBoardCache;
import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.clock.MatchClock;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.PenaltyKick;
import com.studentleague.matches.repository.MatchAnalystAssignmentRepository;
import com.studentleague.matches.repository.MatchAnalystStatsRepository;
import com.studentleague.matches.repository.MatchAvailabilityRepository;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.matches.repository.MatchRefereeRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.matches.repository.PenaltyKickRepository;
import com.studentleague.matches.shootout.ShootoutRules;
import com.studentleague.notifications.NotificationEventType;
import com.studentleague.notifications.NotificationService;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.domain.CompetitionStage;
import com.studentleague.tournaments.domain.TournamentStatus;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.dto.AssignGroupMatchRequest;
import com.studentleague.tournaments.dto.OpenPlayoffRequest;
import com.studentleague.tournaments.dto.PlayoffPairRequest;
import com.studentleague.tournaments.dto.PlayoffRoundResponse;
import com.studentleague.tournaments.dto.PlayoffSlotResponse;
import com.studentleague.tournaments.dto.PlayoffViewResponse;
import com.studentleague.tournaments.dto.QualifiedTeamResponse;
import com.studentleague.tournaments.dto.SetAdvancingPlacesRequest;
import com.studentleague.tournaments.dto.StandingRow;
import com.studentleague.tournaments.dto.StandingTableResponse;
import com.studentleague.tournaments.dto.UnassignedMatchResponse;
import com.studentleague.tournaments.dto.UpdatePlayoffSlotRequest;
import com.studentleague.tournaments.entity.AdvancingPlace;
import com.studentleague.tournaments.entity.PlayoffSlot;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.entity.TournamentTable;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.repository.AdvancingPlaceRepository;
import com.studentleague.tournaments.repository.PlayoffSlotRepository;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTableRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class PlayoffService {

    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TournamentTableRepository tournamentTableRepository;
    private final AdvancingPlaceRepository advancingPlaceRepository;
    private final PlayoffSlotRepository playoffSlotRepository;
    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchLineupPlayerRepository matchLineupPlayerRepository;
    private final MatchRefereeRepository matchRefereeRepository;
    private final MatchAvailabilityRepository matchAvailabilityRepository;
    private final MatchAnalystStatsRepository matchAnalystStatsRepository;
    private final MatchAnalystAssignmentRepository matchAnalystAssignmentRepository;
    private final PenaltyKickRepository penaltyKickRepository;
    private final TeamRepository teamRepository;
    private final TournamentService tournamentService;
    private final NotificationService notificationService;
    private final StandingsBoardCache standingsBoardCache;

    public PlayoffService(
            TournamentRepository tournamentRepository,
            TournamentTeamRepository tournamentTeamRepository,
            TournamentTableRepository tournamentTableRepository,
            AdvancingPlaceRepository advancingPlaceRepository,
            PlayoffSlotRepository playoffSlotRepository,
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            MatchLineupPlayerRepository matchLineupPlayerRepository,
            MatchRefereeRepository matchRefereeRepository,
            MatchAvailabilityRepository matchAvailabilityRepository,
            MatchAnalystStatsRepository matchAnalystStatsRepository,
            MatchAnalystAssignmentRepository matchAnalystAssignmentRepository,
            PenaltyKickRepository penaltyKickRepository,
            TeamRepository teamRepository,
            TournamentService tournamentService,
            NotificationService notificationService,
            StandingsBoardCache standingsBoardCache
    ) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.tournamentTableRepository = tournamentTableRepository;
        this.advancingPlaceRepository = advancingPlaceRepository;
        this.playoffSlotRepository = playoffSlotRepository;
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
        this.matchLineupPlayerRepository = matchLineupPlayerRepository;
        this.matchRefereeRepository = matchRefereeRepository;
        this.matchAvailabilityRepository = matchAvailabilityRepository;
        this.matchAnalystStatsRepository = matchAnalystStatsRepository;
        this.matchAnalystAssignmentRepository = matchAnalystAssignmentRepository;
        this.penaltyKickRepository = penaltyKickRepository;
        this.teamRepository = teamRepository;
        this.tournamentService = tournamentService;
        this.notificationService = notificationService;
        this.standingsBoardCache = standingsBoardCache;
    }

    @Transactional(readOnly = true)
    public PlayoffViewResponse view(UUID tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        return toView(tournament);
    }

    @Transactional
    public PlayoffViewResponse setPlaces(UUID tournamentId, SetAdvancingPlacesRequest request) {
        Tournament tournament = requireGroupStage(tournamentId);
        TreeSet<Integer> places = new TreeSet<>();
        for (Integer place : request.places()) {
            if (place == null || place < 1 || place > 16) {
                throw ApiException.badRequest("Место выхода должно быть от 1 до 16");
            }
            places.add(place);
        }
        if (places.isEmpty()) {
            throw ApiException.badRequest("Укажите, с каких мест команды выходят в плей-офф");
        }
        advancingPlaceRepository.deleteAll(advancingPlaceRepository.findByTournamentIdOrderByPlaceAsc(tournamentId));
        advancingPlaceRepository.flush();
        for (int place : places) {
            AdvancingPlace row = new AdvancingPlace();
            row.setTournamentId(tournamentId);
            row.setPlace(place);
            advancingPlaceRepository.save(row);
        }
        return toView(tournament);
    }

    @Transactional
    public PlayoffViewResponse assignMatch(UUID tournamentId, UUID matchId, AssignGroupMatchRequest request) {
        Tournament tournament = requireGroupStage(tournamentId);
        Match match = requireTournamentMatch(tournamentId, matchId);
        if (match.getBracketRound() != null) {
            throw ApiException.badRequest("Матч плей-офф не относится к группе");
        }
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw ApiException.badRequest("Отменённый матч в группу не ставят");
        }
        TournamentTable table = requireTable(tournamentId, request.tableId());
        requireBothTeamsInTable(tournamentId, table.getId(), match);
        match.setTableId(table.getId());
        matchRepository.save(match);
        standingsBoardCache.invalidateTournament(tournamentId);
        return toView(tournament);
    }

    @Transactional
    public PlayoffViewResponse open(UUID tournamentId, OpenPlayoffRequest request) {
        Tournament tournament = requireGroupStage(tournamentId);
        List<TournamentTable> tables = tournamentTableRepository.findByTournamentIdOrderBySortOrderAscIdAsc(tournamentId);
        if (tables.isEmpty()) {
            throw ApiException.badRequest("Сначала соберите группы и расставьте по ним команды");
        }
        List<Integer> places = placesOf(tournamentId);
        if (places.isEmpty()) {
            throw ApiException.badRequest("Укажите, с каких мест команды выходят в плей-офф — например 1 и 2");
        }
        List<TournamentTeam> entries = tournamentTeamRepository.findByTournamentId(tournamentId);
        requireApprovedTeamsGrouped(entries);
        attachIntraGroupMatches(tournament, entries);
        List<QualifiedTeamResponse> qualified = qualify(tournamentId);
        requireBracketSize(qualified.size());
        List<PlayoffPairRequest> pairs = request.pairs();
        requirePairs(qualified, pairs);
        int rounds = roundCount(qualified.size());
        Map<UUID, QualifiedTeamResponse> byTeam = new HashMap<>();
        for (QualifiedTeamResponse team : qualified) {
            byTeam.put(team.teamId(), team);
        }
        for (int index = 0; index < pairs.size(); index++) {
            PlayoffPairRequest pair = pairs.get(index);
            QualifiedTeamResponse home = byTeam.get(pair.homeTeamId());
            QualifiedTeamResponse away = byTeam.get(pair.awayTeamId());
            Match match = newPlayoffMatch(
                    tournament,
                    pair.homeTeamId(),
                    pair.awayTeamId(),
                    pair.scheduledAt(),
                    blankToNull(pair.venue()),
                    1
            );
            Match saved = matchRepository.save(match);
            notifyScheduled(saved);
            PlayoffSlot slot = newSlot(tournamentId, 1, index);
            slot.setMatchId(saved.getId());
            slot.setHomeTeamId(home.teamId());
            slot.setAwayTeamId(away.teamId());
            slot.setHomeGroupName(home.groupName());
            slot.setHomePlace(home.place());
            slot.setAwayGroupName(away.groupName());
            slot.setAwayPlace(away.place());
            slot.setScheduledAt(pair.scheduledAt());
            slot.setVenue(blankToNull(pair.venue()));
            playoffSlotRepository.save(slot);
        }
        for (int round = 2; round <= rounds; round++) {
            int slots = qualified.size() >> round;
            for (int index = 0; index < slots; index++) {
                playoffSlotRepository.save(newSlot(tournamentId, round, index));
            }
        }
        tournament.setCompetitionStage(CompetitionStage.PLAYOFF);
        if (tournament.getStatus() == TournamentStatus.REGISTRATION) {
            tournament.setStatus(TournamentStatus.ACTIVE);
        }
        tournamentRepository.save(tournament);
        standingsBoardCache.invalidateTournament(tournamentId);
        return toView(tournament);
    }

    @Transactional
    public PlayoffSlotResponse updateSlot(UUID tournamentId, UUID slotId, UpdatePlayoffSlotRequest request) {
        requireTournament(tournamentId);
        PlayoffSlot slot = playoffSlotRepository.findByIdAndTournamentId(slotId, tournamentId)
                .orElseThrow(() -> ApiException.notFound("Пара плей-офф не найдена"));
        rejectTeamEdit(slot, request);
        if (request.scheduledAt() != null) {
            slot.setScheduledAt(request.scheduledAt());
        }
        if (request.venue() != null) {
            slot.setVenue(blankToNull(request.venue()));
        }
        if (slot.getMatchId() != null) {
            Match match = matchRepository.findById(slot.getMatchId())
                    .orElseThrow(() -> ApiException.notFound("Матч не найден"));
            if (request.scheduledAt() != null) {
                match.setScheduledAt(request.scheduledAt());
            }
            if (request.venue() != null) {
                match.setVenue(slot.getVenue());
            }
            matchRepository.save(match);
        }
        playoffSlotRepository.save(slot);
        Map<UUID, Match> matches = loadMatches(List.of(slot));
        return toSlotResponse(
                slot,
                roundCountOf(tournamentId),
                loadTeams(teamIds(List.of(slot))),
                matches,
                penaltiesOf(matches)
        );
    }

    @Transactional
    public void advance(Match match) {
        if (match.getBracketRound() == null || match.getStatus() != MatchStatus.FINISHED) {
            return;
        }
        PlayoffSlot source = playoffSlotRepository.findByMatchId(match.getId()).orElse(null);
        if (source == null) {
            return;
        }
        PlayoffSlot next = playoffSlotRepository.findByTournamentIdAndRoundNumberAndSlotIndex(
                source.getTournamentId(),
                source.getRoundNumber() + 1,
                source.getSlotIndex() / 2
        ).orElse(null);
        if (next == null) {
            return;
        }
        boolean homeSide = source.getSlotIndex() % 2 == 0;
        placeWinner(next, homeSide, winnerOf(match));
    }

    public void syncVenue(Match match) {
        if (match.getBracketRound() == null) {
            return;
        }
        playoffSlotRepository.findByMatchId(match.getId()).ifPresent(slot -> {
            slot.setVenue(match.getVenue());
            playoffSlotRepository.save(slot);
        });
    }

    private void placeWinner(PlayoffSlot next, boolean homeSide, UUID winner) {
        UUID current = homeSide ? next.getHomeTeamId() : next.getAwayTeamId();
        if (Objects.equals(current, winner)) {
            ensureMatch(next);
            return;
        }
        Match nextMatch = next.getMatchId() == null
                ? null
                : matchRepository.findById(next.getMatchId()).orElse(null);
        if (nextMatch != null && nextPairLocked(nextMatch)) {
            throw ApiException.conflict(
                    "Следующий матч плей-офф уже начался, состав пары не меняется. "
                            + "Нельзя убрать или изменить событие, которое меняет победителя."
            );
        }
        if (winner == null) {
            clearSidesFromThisMatch(next, current);
            if (nextMatch != null) {
                dropUnstartedNextMatch(next, nextMatch);
            }
        } else if (homeSide) {
            next.setHomeTeamId(winner);
        } else {
            next.setAwayTeamId(winner);
        }
        playoffSlotRepository.save(next);
        ensureMatch(next);
    }

    /**
     * The feeder no longer has a winner. Drop that team from every side of the
     * next slot it had filled. The other semifinalist, if any, stays.
     */
    private void clearSidesFromThisMatch(PlayoffSlot next, UUID previousWinner) {
        if (previousWinner == null) {
            return;
        }
        if (previousWinner.equals(next.getHomeTeamId())) {
            next.setHomeTeamId(null);
        }
        if (previousWinner.equals(next.getAwayTeamId())) {
            next.setAwayTeamId(null);
        }
    }

    /**
     * A match row cannot store a missing team. The fixture has not started, so
     * remove it and keep the slot's time, venue, and the other finalist.
     */
    private void dropUnstartedNextMatch(PlayoffSlot next, Match nextMatch) {
        next.setMatchId(null);
        playoffSlotRepository.saveAndFlush(next);
        UUID matchId = nextMatch.getId();
        matchEventRepository.deleteByMatchId(matchId);
        matchLineupPlayerRepository.deleteByMatchId(matchId);
        matchRefereeRepository.deleteByMatchId(matchId);
        matchAvailabilityRepository.deleteByMatchId(matchId);
        matchAnalystStatsRepository.deleteByMatchId(matchId);
        matchAnalystAssignmentRepository.deleteByMatchId(matchId);
        penaltyKickRepository.deleteAll(penaltyKickRepository.findByMatchIdOrderByKickOrderAsc(matchId));
        matchRepository.flush();
        matchRepository.delete(nextMatch);
        matchRepository.flush();
        standingsBoardCache.invalidateTournament(nextMatch.getTournamentId());
    }

    /**
     * A later pair that is already live, paused, finished, or has its own protocol
     * stays as played. The caller gets 409 and must not rewrite that slot.
     */
    private boolean nextPairLocked(Match nextMatch) {
        MatchStatus status = nextMatch.getStatus();
        if (status == MatchStatus.LIVE || status == MatchStatus.PAUSED || status == MatchStatus.FINISHED) {
            return true;
        }
        if (status != MatchStatus.SCHEDULED) {
            return true;
        }
        return matchEventRepository.existsByMatchId(nextMatch.getId());
    }

    private void ensureMatch(PlayoffSlot slot) {
        if (slot.getHomeTeamId() == null || slot.getAwayTeamId() == null) {
            return;
        }
        if (slot.getMatchId() != null) {
            Match match = matchRepository.findById(slot.getMatchId())
                    .orElseThrow(() -> ApiException.notFound("Матч не найден"));
            if (match.getStatus() != MatchStatus.SCHEDULED) {
                return;
            }
            match.setHomeTeamId(slot.getHomeTeamId());
            match.setAwayTeamId(slot.getAwayTeamId());
            if (slot.getScheduledAt() != null) {
                match.setScheduledAt(slot.getScheduledAt());
            }
            match.setVenue(slot.getVenue());
            matchRepository.save(match);
            return;
        }
        Tournament tournament = requireTournament(slot.getTournamentId());
        Instant kickoff = slot.getScheduledAt() != null ? slot.getScheduledAt() : Instant.now();
        Match match = newPlayoffMatch(
                tournament,
                slot.getHomeTeamId(),
                slot.getAwayTeamId(),
                kickoff,
                slot.getVenue(),
                slot.getRoundNumber()
        );
        Match saved = matchRepository.save(match);
        slot.setMatchId(saved.getId());
        if (slot.getScheduledAt() == null) {
            slot.setScheduledAt(kickoff);
        }
        playoffSlotRepository.save(slot);
        notifyScheduled(saved);
    }

    private void attachIntraGroupMatches(Tournament tournament, List<TournamentTeam> entries) {
        Map<UUID, UUID> tableByTeam = new HashMap<>();
        for (TournamentTeam entry : entries) {
            if (entry.getStatus() == TournamentTeamStatus.APPROVED && entry.getTableId() != null) {
                tableByTeam.put(entry.getTeamId(), entry.getTableId());
            }
        }
        for (Match match : matchRepository.findByTournamentId(tournament.getId())) {
            if (match.getBracketRound() != null || match.getTableId() != null || match.getStatus() == MatchStatus.CANCELLED) {
                continue;
            }
            UUID homeTable = tableByTeam.get(match.getHomeTeamId());
            UUID awayTable = tableByTeam.get(match.getAwayTeamId());
            if (homeTable != null && homeTable.equals(awayTable)) {
                match.setTableId(homeTable);
                matchRepository.save(match);
            }
        }
    }

    private void requireApprovedTeamsGrouped(List<TournamentTeam> entries) {
        List<UUID> missing = entries.stream()
                .filter(entry -> entry.getStatus() == TournamentTeamStatus.APPROVED && entry.getTableId() == null)
                .map(TournamentTeam::getTeamId)
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        String name = teamRepository.findById(missing.getFirst()).map(Team::getName).orElse("Команда");
        throw ApiException.badRequest("Команда «" + name + "» не стоит ни в одной группе");
    }

    private List<QualifiedTeamResponse> qualify(UUID tournamentId) {
        List<Integer> places = placesOf(tournamentId);
        List<QualifiedTeamResponse> qualified = new ArrayList<>();
        for (StandingTableResponse table : tournamentService.standings(tournamentId).tables()) {
            if (table.id() == null) {
                continue;
            }
            List<StandingRow> rows = table.rows();
            for (int place : places) {
                if (place < 1 || place > rows.size()) {
                    continue;
                }
                StandingRow row = rows.get(place - 1);
                qualified.add(new QualifiedTeamResponse(
                        row.teamId(),
                        row.teamName(),
                        table.id(),
                        table.name(),
                        place
                ));
            }
        }
        return qualified;
    }

    private void requireBracketSize(int count) {
        if (count == 2 || count == 4 || count == 8 || count == 16) {
            return;
        }
        throw ApiException.badRequest(
                "В плей-офф выходит " + count + " "
                        + teamsWord(count)
                        + ". Сетка строится только на 2, 4, 8 или 16 участников, без автоматических пропусков."
        );
    }

    private void requirePairs(List<QualifiedTeamResponse> qualified, List<PlayoffPairRequest> pairs) {
        int expected = qualified.size() / 2;
        if (pairs.size() != expected) {
            throw ApiException.badRequest("Нужно назначить " + expected + " пар из вышедших команд");
        }
        Set<UUID> allowed = new HashSet<>();
        for (QualifiedTeamResponse team : qualified) {
            allowed.add(team.teamId());
        }
        Set<UUID> used = new HashSet<>();
        for (PlayoffPairRequest pair : pairs) {
            if (pair.homeTeamId().equals(pair.awayTeamId())) {
                throw ApiException.badRequest("В паре должны быть две разные команды");
            }
            if (!allowed.contains(pair.homeTeamId()) || !allowed.contains(pair.awayTeamId())) {
                throw ApiException.badRequest(
                        "В сетку можно поставить только команды с выбранных мест. Остальные в плей-офф не выходят."
                );
            }
            if (!used.add(pair.homeTeamId()) || !used.add(pair.awayTeamId())) {
                throw ApiException.badRequest("Каждая вышедшая команда ставится в сетку один раз");
            }
        }
    }

    private void rejectTeamEdit(PlayoffSlot slot, UpdatePlayoffSlotRequest request) {
        if (request.homeTeamId() == null && request.awayTeamId() == null) {
            return;
        }
        if (sideLocked(slot.getHomeTeamId(), request.homeTeamId())
                || sideLocked(slot.getAwayTeamId(), request.awayTeamId())) {
            if (slot.getHomeTeamId() == null || slot.getAwayTeamId() == null) {
                throw ApiException.badRequest("Команду в следующую пару ставит только победитель предыдущего матча.");
            }
            throw ApiException.badRequest(
                    "Состав пары после создания сетки изменить нельзя. Можно править только время и место проведения."
            );
        }
    }

    private static boolean sideLocked(UUID current, UUID requested) {
        return requested != null && !requested.equals(current);
    }

    private PlayoffViewResponse toView(Tournament tournament) {
        UUID tournamentId = tournament.getId();
        List<PlayoffSlot> slots = playoffSlotRepository.findByTournamentIdOrderByRoundNumberAscSlotIndexAsc(tournamentId);
        List<QualifiedTeamResponse> qualified = tournament.getCompetitionStage() == CompetitionStage.PLAYOFF
                ? qualifiedFromBracket(slots)
                : qualify(tournamentId);
        Map<UUID, Team> teams = loadTeams(idsForView(slots, qualified, tournamentId));
        qualified = withNames(qualified, teams);
        Map<UUID, Match> matches = loadMatches(slots);
        Map<UUID, ShootoutRules.Tally> penalties = penaltiesOf(matches);
        int rounds = slots.isEmpty() ? 0 : slots.getLast().getRoundNumber();
        Map<Integer, List<PlayoffSlotResponse>> byRound = new LinkedHashMap<>();
        for (PlayoffSlot slot : slots) {
            byRound.computeIfAbsent(slot.getRoundNumber(), ignored -> new ArrayList<>())
                    .add(toSlotResponse(slot, rounds, teams, matches, penalties));
        }
        List<PlayoffRoundResponse> roundResponses = new ArrayList<>();
        for (Map.Entry<Integer, List<PlayoffSlotResponse>> entry : byRound.entrySet()) {
            roundResponses.add(new PlayoffRoundResponse(
                    entry.getKey(),
                    roundTitle(entry.getKey(), rounds),
                    entry.getValue()
            ));
        }
        return new PlayoffViewResponse(
                tournament.getCompetitionStage().name(),
                placesOf(tournamentId),
                qualified,
                roundResponses,
                unassigned(tournamentId, teams)
        );
    }

    private static List<QualifiedTeamResponse> withNames(
            List<QualifiedTeamResponse> qualified,
            Map<UUID, Team> teams
    ) {
        List<QualifiedTeamResponse> named = new ArrayList<>();
        for (QualifiedTeamResponse team : qualified) {
            String name = team.teamName();
            if (name == null || name.isBlank()) {
                name = teamName(teams, team.teamId());
            }
            named.add(new QualifiedTeamResponse(
                    team.teamId(), name, team.tableId(), team.groupName(), team.place()
            ));
        }
        return named;
    }

    private List<QualifiedTeamResponse> qualifiedFromBracket(List<PlayoffSlot> slots) {
        List<QualifiedTeamResponse> qualified = new ArrayList<>();
        for (PlayoffSlot slot : slots) {
            if (slot.getRoundNumber() != 1) {
                continue;
            }
            if (slot.getHomeTeamId() != null) {
                qualified.add(new QualifiedTeamResponse(
                        slot.getHomeTeamId(),
                        null,
                        null,
                        slot.getHomeGroupName(),
                        slot.getHomePlace() == null ? 0 : slot.getHomePlace()
                ));
            }
            if (slot.getAwayTeamId() != null) {
                qualified.add(new QualifiedTeamResponse(
                        slot.getAwayTeamId(),
                        null,
                        null,
                        slot.getAwayGroupName(),
                        slot.getAwayPlace() == null ? 0 : slot.getAwayPlace()
                ));
            }
        }
        return qualified;
    }

    private PlayoffSlotResponse toSlotResponse(
            PlayoffSlot slot,
            int rounds,
            Map<UUID, Team> teams,
            Map<UUID, Match> matches,
            Map<UUID, ShootoutRules.Tally> penalties
    ) {
        Match match = slot.getMatchId() == null ? null : matches.get(slot.getMatchId());
        Integer penaltyHome = null;
        Integer penaltyAway = null;
        if (match != null && match.getHomeScore() == match.getAwayScore()) {
            ShootoutRules.Tally tally = penalties.get(match.getId());
            if (tally != null) {
                penaltyHome = tally.homeScored();
                penaltyAway = tally.awayScored();
            }
        }
        return new PlayoffSlotResponse(
                slot.getId(),
                slot.getRoundNumber(),
                slot.getSlotIndex(),
                roundTitle(slot.getRoundNumber(), rounds),
                slot.getMatchId(),
                slot.getHomeTeamId(),
                teamName(teams, slot.getHomeTeamId()),
                slot.getHomeGroupName(),
                slot.getHomePlace(),
                slot.getAwayTeamId(),
                teamName(teams, slot.getAwayTeamId()),
                slot.getAwayGroupName(),
                slot.getAwayPlace(),
                waitingLabel(slot, true, rounds),
                waitingLabel(slot, false, rounds),
                slot.getScheduledAt(),
                slot.getVenue(),
                match == null ? null : match.getStatus().name(),
                match == null ? null : match.getHomeScore(),
                match == null ? null : match.getAwayScore(),
                penaltyHome,
                penaltyAway
        );
    }

    private Map<UUID, ShootoutRules.Tally> penaltiesOf(Map<UUID, Match> matches) {
        if (matches.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> homeByMatch = new HashMap<>();
        for (Match match : matches.values()) {
            homeByMatch.put(match.getId(), match.getHomeTeamId());
        }
        List<ShootoutRules.ScoredKick> kicks = new ArrayList<>();
        for (PenaltyKick row : penaltyKickRepository.findByMatchIdInOrderByKickOrderAsc(matches.keySet())) {
            kicks.add(new ShootoutRules.ScoredKick(row.getMatchId(), row.getTeamId(), row.isScored()));
        }
        return ShootoutRules.byMatch(kicks, homeByMatch);
    }

    private List<UnassignedMatchResponse> unassigned(UUID tournamentId, Map<UUID, Team> teams) {
        List<UnassignedMatchResponse> result = new ArrayList<>();
        for (Match match : matchRepository.findByTournamentId(tournamentId)) {
            if (match.getTableId() != null || match.getBracketRound() != null || match.getStatus() == MatchStatus.CANCELLED) {
                continue;
            }
            result.add(new UnassignedMatchResponse(
                    match.getId(),
                    match.getHomeTeamId(),
                    teamName(teams, match.getHomeTeamId()),
                    match.getAwayTeamId(),
                    teamName(teams, match.getAwayTeamId()),
                    match.getHomeScore(),
                    match.getAwayScore(),
                    match.getStatus().name()
            ));
        }
        return result;
    }

    private Set<UUID> idsForView(List<PlayoffSlot> slots, List<QualifiedTeamResponse> qualified, UUID tournamentId) {
        Set<UUID> ids = new HashSet<>();
        for (QualifiedTeamResponse team : qualified) {
            if (team.teamId() != null) {
                ids.add(team.teamId());
            }
        }
        ids.addAll(teamIds(slots));
        for (Match match : matchRepository.findByTournamentId(tournamentId)) {
            if (match.getTableId() == null && match.getBracketRound() == null) {
                ids.add(match.getHomeTeamId());
                ids.add(match.getAwayTeamId());
            }
        }
        return ids;
    }

    private static Set<UUID> teamIds(List<PlayoffSlot> slots) {
        Set<UUID> ids = new HashSet<>();
        for (PlayoffSlot slot : slots) {
            if (slot.getHomeTeamId() != null) {
                ids.add(slot.getHomeTeamId());
            }
            if (slot.getAwayTeamId() != null) {
                ids.add(slot.getAwayTeamId());
            }
        }
        return ids;
    }

    private Map<UUID, Team> loadTeams(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Team> teams = new HashMap<>();
        for (Team team : teamRepository.findAllById(ids)) {
            teams.put(team.getId(), team);
        }
        return teams;
    }

    private Map<UUID, Match> loadMatches(List<PlayoffSlot> slots) {
        List<UUID> ids = slots.stream().map(PlayoffSlot::getMatchId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Match> matches = new HashMap<>();
        for (Match match : matchRepository.findAllById(ids)) {
            matches.put(match.getId(), match);
        }
        return matches;
    }

    private List<Integer> placesOf(UUID tournamentId) {
        return advancingPlaceRepository.findByTournamentIdOrderByPlaceAsc(tournamentId).stream()
                .map(AdvancingPlace::getPlace)
                .toList();
    }

    private int roundCountOf(UUID tournamentId) {
        return playoffSlotRepository.findByTournamentIdOrderByRoundNumberAscSlotIndexAsc(tournamentId).stream()
                .mapToInt(PlayoffSlot::getRoundNumber)
                .max()
                .orElse(0);
    }

    private void requireBothTeamsInTable(UUID tournamentId, UUID tableId, Match match) {
        TournamentTeam home = tournamentTeamRepository.findByTournamentIdAndTeamId(tournamentId, match.getHomeTeamId())
                .orElseThrow(() -> ApiException.badRequest("Хозяева не заявлены в турнир"));
        TournamentTeam away = tournamentTeamRepository.findByTournamentIdAndTeamId(tournamentId, match.getAwayTeamId())
                .orElseThrow(() -> ApiException.badRequest("Гости не заявлены в турнир"));
        if (!tableId.equals(home.getTableId()) || !tableId.equals(away.getTableId())) {
            throw ApiException.badRequest("Обе команды матча должны стоять в этой группе");
        }
    }

    private Tournament requireGroupStage(UUID tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        if (tournament.getCompetitionStage() == CompetitionStage.PLAYOFF) {
            throw ApiException.badRequest("Турнир уже в плей-офф");
        }
        return tournament;
    }

    private Tournament requireTournament(UUID tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> ApiException.notFound("Турнир не найден"));
    }

    private TournamentTable requireTable(UUID tournamentId, UUID tableId) {
        return tournamentTableRepository.findById(tableId)
                .filter(table -> table.getTournamentId().equals(tournamentId))
                .orElseThrow(() -> ApiException.badRequest("Группа не относится к этому турниру"));
    }

    private Match requireTournamentMatch(UUID tournamentId, UUID matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Матч не найден"));
        if (!tournamentId.equals(match.getTournamentId())) {
            throw ApiException.notFound("Матч не найден");
        }
        return match;
    }

    private Match newPlayoffMatch(
            Tournament tournament,
            UUID homeTeamId,
            UUID awayTeamId,
            Instant scheduledAt,
            String venue,
            int round
    ) {
        Match match = new Match();
        match.setTournamentId(tournament.getId());
        match.setSportId(tournament.getSportId());
        match.setHomeTeamId(homeTeamId);
        match.setAwayTeamId(awayTeamId);
        match.setScheduledAt(scheduledAt);
        match.setStatus(MatchStatus.SCHEDULED);
        match.setHomeScore(0);
        match.setAwayScore(0);
        match.setPeriodCount(MatchClock.DEFAULT_PERIOD_COUNT);
        match.setPeriodLengthSeconds(MatchClock.DEFAULT_PERIOD_LENGTH_SECONDS);
        match.setVenue(venue);
        match.setBracketRound(round);
        return match;
    }

    private void notifyScheduled(Match match) {
        notificationService.publishToUser(
                null,
                NotificationEventType.SCHEDULE_UPDATE,
                "Match scheduled",
                "A new match was scheduled in the tournament",
                Map.of(
                        "matchId", match.getId().toString(),
                        "tournamentId", match.getTournamentId().toString()
                )
        );
    }

    private static PlayoffSlot newSlot(UUID tournamentId, int round, int index) {
        PlayoffSlot slot = new PlayoffSlot();
        slot.setTournamentId(tournamentId);
        slot.setRoundNumber(round);
        slot.setSlotIndex(index);
        return slot;
    }

    private UUID winnerOf(Match match) {
        if (match.getHomeScore() > match.getAwayScore()) {
            return match.getHomeTeamId();
        }
        if (match.getAwayScore() > match.getHomeScore()) {
            return match.getAwayTeamId();
        }
        if (match.getBracketRound() == null) {
            return null;
        }
        List<ShootoutRules.Kick> kicks = new ArrayList<>();
        for (PenaltyKick row : penaltyKickRepository.findByMatchIdOrderByKickOrderAsc(match.getId())) {
            kicks.add(new ShootoutRules.Kick(row.getTeamId(), row.isScored()));
        }
        return ShootoutRules.winner(match.getHomeTeamId(), match.getAwayTeamId(), kicks);
    }

    /**
     * Empty feeder side: «Победитель 1/2 #1». The round token is the source round
     * (1/2, 1/4, 1/8, финала, …) and the index is that pair, starting at 1.
     */
    static String waitingLabel(PlayoffSlot slot, boolean home, int rounds) {
        UUID teamId = home ? slot.getHomeTeamId() : slot.getAwayTeamId();
        if (teamId != null || slot.getRoundNumber() <= 1) {
            return null;
        }
        int feeder = slot.getSlotIndex() * 2 + (home ? 1 : 2);
        return winnerSlotLabel(roundTitle(slot.getRoundNumber() - 1, rounds), feeder);
    }

    static String winnerSlotLabel(String sourceRoundTitle, int feeder) {
        String title = shortSourceRound(sourceRoundTitle);
        if (title.isEmpty()) {
            title = "пары";
        }
        return "Победитель " + title + " #" + feeder;
    }

    static String shortSourceRound(String title) {
        if (title == null) {
            return "";
        }
        String text = title.trim();
        if (text.equalsIgnoreCase("Финал")) {
            return "финала";
        }
        return text.replaceFirst("(?i)\\s*финала\\s*$", "").trim();
    }

    private static String teamName(Map<UUID, Team> teams, UUID teamId) {
        if (teamId == null) {
            return null;
        }
        Team team = teams.get(teamId);
        return team == null ? "Команда" : team.getName();
    }

    private static String roundTitle(int round, int rounds) {
        int fromFinal = rounds - round;
        return switch (fromFinal) {
            case 0 -> "Финал";
            case 1 -> "1/2 финала";
            case 2 -> "1/4 финала";
            case 3 -> "1/8 финала";
            case 4 -> "1/16 финала";
            default -> "Раунд " + round;
        };
    }

    private static int roundCount(int teamCount) {
        int rounds = 0;
        int remaining = teamCount;
        while (remaining > 1) {
            remaining /= 2;
            rounds++;
        }
        return rounds;
    }

    private static String teamsWord(int count) {
        int tail = Math.abs(count) % 100;
        int digit = tail % 10;
        if (tail > 10 && tail < 20) {
            return "команд";
        }
        if (digit == 1) {
            return "команда";
        }
        if (digit >= 2 && digit <= 4) {
            return "команды";
        }
        return "команд";
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
