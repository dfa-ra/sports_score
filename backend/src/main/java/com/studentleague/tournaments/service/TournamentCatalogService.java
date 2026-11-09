package com.studentleague.tournaments.service;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.PenaltyKick;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.matches.repository.PenaltyKickRepository;
import com.studentleague.matches.shootout.ShootoutRules;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.domain.CompetitionStage;
import com.studentleague.tournaments.domain.PlayoffSlotKind;
import com.studentleague.tournaments.domain.TournamentStatus;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.dto.StandingRow;
import com.studentleague.tournaments.dto.TournamentCatalogResponse;
import com.studentleague.tournaments.entity.PlayoffSlot;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.entity.TournamentTable;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.format.StandingsContext;
import com.studentleague.tournaments.format.TournamentFormatRegistry;
import com.studentleague.tournaments.repository.PlayoffSlotRepository;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTableRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Public directory for /tournaments. Counts, progress, the current round and
 * the winner are read by tournament id in a handful of queries, not one call
 * per tournament.
 */
@Service
public class TournamentCatalogService {

    private static final List<TournamentStatus> PUBLIC = List.of(
            TournamentStatus.REGISTRATION,
            TournamentStatus.ACTIVE,
            TournamentStatus.FINISHED
    );

    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TournamentTableRepository tournamentTableRepository;
    private final MatchRepository matchRepository;
    private final PlayoffSlotRepository playoffSlotRepository;
    private final PenaltyKickRepository penaltyKickRepository;
    private final TeamRepository teamRepository;
    private final TournamentFormatRegistry formats;

    public TournamentCatalogService(
            TournamentRepository tournamentRepository,
            TournamentTeamRepository tournamentTeamRepository,
            TournamentTableRepository tournamentTableRepository,
            MatchRepository matchRepository,
            PlayoffSlotRepository playoffSlotRepository,
            PenaltyKickRepository penaltyKickRepository,
            TeamRepository teamRepository,
            TournamentFormatRegistry formats
    ) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.tournamentTableRepository = tournamentTableRepository;
        this.matchRepository = matchRepository;
        this.playoffSlotRepository = playoffSlotRepository;
        this.penaltyKickRepository = penaltyKickRepository;
        this.teamRepository = teamRepository;
        this.formats = formats;
    }

    @Transactional(readOnly = true)
    public List<TournamentCatalogResponse> catalog() {
        List<Tournament> tournaments = tournamentRepository.findByStatusInOrderByStartDateDescCreatedAtDesc(PUBLIC);
        if (tournaments.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = tournaments.stream().map(Tournament::getId).toList();
        Map<UUID, Integer> teamCounts = teamCounts(ids);
        Map<UUID, List<Match>> matches = groupMatches(matchRepository.findByTournamentIdIn(ids));
        Map<UUID, List<PlayoffSlot>> slots = groupSlots(playoffSlotRepository.findByTournamentIdIn(ids));
        Map<UUID, List<TournamentTeam>> entries = groupEntries(tournamentTeamRepository.findByTournamentIdIn(ids));
        Map<UUID, List<TournamentTable>> tables = groupTables(tournamentTableRepository.findByTournamentIdIn(ids));
        Map<UUID, Match> matchById = new HashMap<>();
        for (List<Match> rows : matches.values()) {
            for (Match match : rows) {
                matchById.put(match.getId(), match);
            }
        }
        Map<UUID, ShootoutRules.Tally> penalties = penalties(finals(slots, matchById));
        Map<UUID, Team> teams = teams(entries, matches, slots, matchById);

        List<TournamentCatalogResponse> rows = new ArrayList<>();
        for (Tournament tournament : tournaments) {
            UUID id = tournament.getId();
            List<Match> fixture = matches.getOrDefault(id, List.of());
            List<PlayoffSlot> bracket = slots.getOrDefault(id, List.of());
            boolean playoff = wantsPlayoff(tournament);
            String roundTitle = roundTitle(tournament, bracket, matchById);
            Winner winner = winner(tournament, playoff, bracket, matchById, penalties, entries.getOrDefault(id, List.of()), fixture, tables.getOrDefault(id, List.of()), teams);
            rows.add(new TournamentCatalogResponse(
                    id,
                    tournament.getName(),
                    tournament.getStatus(),
                    tournament.getFormat(),
                    tournament.getCompetitionStage(),
                    tournament.getStartDate(),
                    tournament.getEndDate(),
                    tournament.getSeasonYear(),
                    teamCounts.getOrDefault(id, 0),
                    tournament.getCompetitionStage() == null ? null : tournament.getCompetitionStage().name(),
                    progress(tournament, fixture, playoff),
                    roundTitle,
                    winner == null ? null : winner.name(),
                    winner == null ? null : winner.teamId(),
                    liveCount(fixture)
            ));
        }
        return rows;
    }

    private Map<UUID, Integer> teamCounts(Collection<UUID> ids) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] row : tournamentTeamRepository.countByTournamentIdInAndStatusNot(ids, TournamentTeamStatus.WITHDRAWN)) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    private Map<UUID, Team> teams(
            Map<UUID, List<TournamentTeam>> entries,
            Map<UUID, List<Match>> matches,
            Map<UUID, List<PlayoffSlot>> slots,
            Map<UUID, Match> matchById
    ) {
        Set<UUID> teamIds = new HashSet<>();
        for (List<TournamentTeam> rows : entries.values()) {
            for (TournamentTeam entry : rows) {
                if (entry.getTeamId() != null) {
                    teamIds.add(entry.getTeamId());
                }
            }
        }
        for (List<Match> rows : matches.values()) {
            for (Match match : rows) {
                if (match.getHomeTeamId() != null) {
                    teamIds.add(match.getHomeTeamId());
                }
                if (match.getAwayTeamId() != null) {
                    teamIds.add(match.getAwayTeamId());
                }
            }
        }
        for (List<PlayoffSlot> rows : slots.values()) {
            for (PlayoffSlot slot : rows) {
                if (slot.getHomeTeamId() != null) {
                    teamIds.add(slot.getHomeTeamId());
                }
                if (slot.getAwayTeamId() != null) {
                    teamIds.add(slot.getAwayTeamId());
                }
                Match match = slot.getMatchId() == null ? null : matchById.get(slot.getMatchId());
                if (match == null) {
                    continue;
                }
                if (match.getHomeTeamId() != null) {
                    teamIds.add(match.getHomeTeamId());
                }
                if (match.getAwayTeamId() != null) {
                    teamIds.add(match.getAwayTeamId());
                }
            }
        }
        Map<UUID, Team> teams = new HashMap<>();
        if (!teamIds.isEmpty()) {
            for (Team team : teamRepository.findAllById(teamIds)) {
                teams.put(team.getId(), team);
            }
        }
        return teams;
    }

    private Map<UUID, ShootoutRules.Tally> penalties(List<Match> finals) {
        if (finals.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> homeByMatch = new HashMap<>();
        List<UUID> ids = new ArrayList<>();
        for (Match match : finals) {
            if (match.getHomeScore() == match.getAwayScore()) {
                homeByMatch.put(match.getId(), match.getHomeTeamId());
                ids.add(match.getId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ShootoutRules.ScoredKick> kicks = new ArrayList<>();
        for (PenaltyKick row : penaltyKickRepository.findByMatchIdInOrderByKickOrderAsc(ids)) {
            kicks.add(new ShootoutRules.ScoredKick(row.getMatchId(), row.getTeamId(), row.isScored()));
        }
        return ShootoutRules.byMatch(kicks, homeByMatch);
    }

    private static List<Match> finals(Map<UUID, List<PlayoffSlot>> slots, Map<UUID, Match> matches) {
        List<Match> finals = new ArrayList<>();
        for (List<PlayoffSlot> bracket : slots.values()) {
            PlayoffSlot slot = finalSlot(bracket);
            if (slot == null || slot.getMatchId() == null) {
                continue;
            }
            Match match = matches.get(slot.getMatchId());
            if (match != null) {
                finals.add(match);
            }
        }
        return finals;
    }

    private static Double progress(Tournament tournament, List<Match> matches, boolean playoff) {
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            return null;
        }
        List<Match> open = matches.stream().filter(match -> match.getStatus() != MatchStatus.CANCELLED).toList();
        List<Match> counted = playoff
                ? open.stream().filter(match -> match.getBracketRound() != null).toList()
                : open;
        if (playoff && counted.isEmpty()) {
            counted = open;
        }
        if (counted.isEmpty()) {
            return null;
        }
        long finished = counted.stream().filter(match -> match.getStatus() == MatchStatus.FINISHED).count();
        return finished / (double) counted.size();
    }

    private static int liveCount(List<Match> matches) {
        int live = 0;
        for (Match match : matches) {
            if (match.getStatus() == MatchStatus.LIVE) {
                live++;
            }
        }
        return live;
    }

    private static boolean wantsPlayoff(Tournament tournament) {
        if (tournament.getCompetitionStage() == CompetitionStage.PLAYOFF) {
            return true;
        }
        String format = tournament.getFormat();
        return "CUP".equals(format) || "DOUBLE_ELIMINATION".equals(format);
    }

    private static boolean bracketTitle(Tournament tournament, boolean playoff) {
        if (tournament.getStatus() == TournamentStatus.ACTIVE && playoff) {
            return true;
        }
        return tournament.getStatus() == TournamentStatus.FINISHED
                && (playoff || "GROUPS_PLAYOFF".equals(tournament.getFormat()));
    }

    private static String roundTitle(Tournament tournament, List<PlayoffSlot> slots, Map<UUID, Match> matches) {
        if (!bracketTitle(tournament, wantsPlayoff(tournament)) || slots.isEmpty()) {
            return null;
        }
        List<List<PlayoffSlot>> rounds = rounds(slots);
        if (rounds.isEmpty()) {
            return null;
        }
        int index = rounds.size() - 1;
        for (int i = 0; i < rounds.size(); i++) {
            if (rounds.get(i).stream().anyMatch(slot -> live(slot, matches))) {
                index = i;
                break;
            }
        }
        boolean foundLive = rounds.get(index).stream().anyMatch(slot -> live(slot, matches));
        if (!foundLive) {
            for (int i = 0; i < rounds.size(); i++) {
                if (rounds.get(i).stream().anyMatch(slot -> open(slot, matches))) {
                    index = i;
                    break;
                }
            }
        }
        int roundNumber = rounds.get(index).getFirst().getRoundNumber();
        return PlayoffService.roundTitle(roundNumber, mainRounds(slots));
    }

    private Winner winner(
            Tournament tournament,
            boolean playoff,
            List<PlayoffSlot> slots,
            Map<UUID, Match> matches,
            Map<UUID, ShootoutRules.Tally> penalties,
            List<TournamentTeam> entries,
            List<Match> fixture,
            List<TournamentTable> tables,
            Map<UUID, Team> teams
    ) {
        if (tournament.getStatus() != TournamentStatus.FINISHED) {
            return null;
        }
        if (playoff || "GROUPS_PLAYOFF".equals(tournament.getFormat())) {
            return playoffWinner(slots, matches, penalties, teams);
        }
        if ("ROUND_ROBIN".equals(tournament.getFormat())) {
            return tableWinner(tournament, entries, fixture, tables, teams);
        }
        return null;
    }

    private static Winner playoffWinner(
            List<PlayoffSlot> slots,
            Map<UUID, Match> matches,
            Map<UUID, ShootoutRules.Tally> penalties,
            Map<UUID, Team> teams
    ) {
        PlayoffSlot slot = finalSlot(slots);
        if (slot == null || slot.getMatchId() == null) {
            return null;
        }
        Match match = matches.get(slot.getMatchId());
        if (match == null || match.getStatus() != MatchStatus.FINISHED) {
            return null;
        }
        int home = match.getHomeScore();
        int away = match.getAwayScore();
        UUID teamId = null;
        if (home > away) {
            teamId = match.getHomeTeamId();
        } else if (away > home) {
            teamId = match.getAwayTeamId();
        } else {
            ShootoutRules.Tally tally = penalties.get(match.getId());
            if (tally != null && tally.homeScored() > tally.awayScored()) {
                teamId = match.getHomeTeamId();
            } else if (tally != null && tally.awayScored() > tally.homeScored()) {
                teamId = match.getAwayTeamId();
            }
        }
        if (teamId == null) {
            return null;
        }
        Team team = teams.get(teamId);
        String name = team == null ? null : team.getName();
        if (name == null || name.isBlank()) {
            return null;
        }
        return new Winner(teamId, name.trim());
    }

    private Winner tableWinner(
            Tournament tournament,
            List<TournamentTeam> entries,
            List<Match> matches,
            List<TournamentTable> tables,
            Map<UUID, Team> teams
    ) {
        List<Match> finished = matches.stream()
                .filter(match -> match.getStatus() == MatchStatus.FINISHED && match.getBracketRound() == null)
                .toList();
        if (tables.isEmpty()) {
            return firstRow(formats.handler(tournament.getFormat())
                    .standings(new StandingsContext(tournament, entries, finished, teams)));
        }
        List<TournamentTable> ordered = tables.stream()
                .sorted(Comparator.comparingInt(TournamentTable::getSortOrder).thenComparing(TournamentTable::getId))
                .toList();
        List<StandingRow> only = null;
        int filled = 0;
        for (TournamentTable table : ordered) {
            Set<UUID> groupIds = new HashSet<>();
            List<TournamentTeam> groupEntries = new ArrayList<>();
            for (TournamentTeam entry : entries) {
                if (table.getId().equals(entry.getTableId())) {
                    groupEntries.add(entry);
                    groupIds.add(entry.getTeamId());
                }
            }
            List<Match> intra = finished.stream()
                    .filter(match -> table.getId().equals(match.getTableId())
                            || (match.getTableId() == null
                            && groupIds.contains(match.getHomeTeamId())
                            && groupIds.contains(match.getAwayTeamId())))
                    .toList();
            List<StandingRow> rows = formats.handler(tournament.getFormat())
                    .standings(new StandingsContext(tournament, groupEntries, intra, teams));
            if (rows != null && !rows.isEmpty()) {
                filled++;
                only = rows;
            }
        }
        if (filled != 1) {
            return null;
        }
        return firstRow(only);
    }

    private static Winner firstRow(List<StandingRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        StandingRow row = rows.getFirst();
        if (row.teamName() == null || row.teamName().isBlank()) {
            return null;
        }
        return new Winner(row.teamId(), row.teamName().trim());
    }

    private static PlayoffSlot finalSlot(List<PlayoffSlot> slots) {
        List<List<PlayoffSlot>> rounds = rounds(slots);
        if (rounds.isEmpty()) {
            return null;
        }
        for (PlayoffSlot slot : rounds.getLast()) {
            if (slot.getKind() != PlayoffSlotKind.THIRD) {
                return slot;
            }
        }
        return null;
    }

    private static List<List<PlayoffSlot>> rounds(List<PlayoffSlot> slots) {
        Map<Integer, List<PlayoffSlot>> byRound = new HashMap<>();
        for (PlayoffSlot slot : slots) {
            byRound.computeIfAbsent(slot.getRoundNumber(), ignored -> new ArrayList<>()).add(slot);
        }
        List<Integer> numbers = new ArrayList<>(byRound.keySet());
        numbers.sort(Integer::compareTo);
        List<List<PlayoffSlot>> rounds = new ArrayList<>();
        for (Integer number : numbers) {
            List<PlayoffSlot> round = byRound.get(number);
            round.sort(Comparator.comparingInt(PlayoffSlot::getSlotIndex));
            rounds.add(round);
        }
        return rounds;
    }

    private static int mainRounds(List<PlayoffSlot> slots) {
        int rounds = 0;
        for (PlayoffSlot slot : slots) {
            if (slot.getKind() != PlayoffSlotKind.THIRD) {
                rounds = Math.max(rounds, slot.getRoundNumber());
            }
        }
        return rounds;
    }

    private static boolean live(PlayoffSlot slot, Map<UUID, Match> matches) {
        Match match = slot.getMatchId() == null ? null : matches.get(slot.getMatchId());
        return match != null && (match.getStatus() == MatchStatus.LIVE || match.getStatus() == MatchStatus.PAUSED);
    }

    private static boolean open(PlayoffSlot slot, Map<UUID, Match> matches) {
        Match match = slot.getMatchId() == null ? null : matches.get(slot.getMatchId());
        if (match == null) {
            return true;
        }
        return match.getStatus() != MatchStatus.FINISHED && match.getStatus() != MatchStatus.CANCELLED;
    }

    private static Map<UUID, List<Match>> groupMatches(List<Match> matches) {
        Map<UUID, List<Match>> grouped = new HashMap<>();
        for (Match match : matches) {
            grouped.computeIfAbsent(match.getTournamentId(), id -> new ArrayList<>()).add(match);
        }
        return grouped;
    }

    private static Map<UUID, List<PlayoffSlot>> groupSlots(List<PlayoffSlot> slots) {
        Map<UUID, List<PlayoffSlot>> grouped = new HashMap<>();
        for (PlayoffSlot slot : slots) {
            grouped.computeIfAbsent(slot.getTournamentId(), id -> new ArrayList<>()).add(slot);
        }
        return grouped;
    }

    private static Map<UUID, List<TournamentTeam>> groupEntries(List<TournamentTeam> entries) {
        Map<UUID, List<TournamentTeam>> grouped = new HashMap<>();
        for (TournamentTeam entry : entries) {
            grouped.computeIfAbsent(entry.getTournamentId(), id -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    private static Map<UUID, List<TournamentTable>> groupTables(List<TournamentTable> tables) {
        Map<UUID, List<TournamentTable>> grouped = new HashMap<>();
        for (TournamentTable table : tables) {
            grouped.computeIfAbsent(table.getTournamentId(), id -> new ArrayList<>()).add(table);
        }
        return grouped;
    }

    private record Winner(UUID teamId, String name) {
    }
}
