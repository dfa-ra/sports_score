package com.studentleague.home.service;

import com.studentleague.home.HomeLabels;
import com.studentleague.home.HomeLabels.RoundPlan;
import com.studentleague.home.dto.HomeLeaderResponse;
import com.studentleague.home.dto.HomeMatchResponse;
import com.studentleague.home.dto.HomeOverviewResponse;
import com.studentleague.home.dto.HomeScorerResponse;
import com.studentleague.home.dto.HomeScorersResponse;
import com.studentleague.home.dto.HomeTournamentResponse;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.matches.service.AbandonedLiveMatchService;
import com.studentleague.matches.service.MatchMapper;
import com.studentleague.statistics.dto.LeagueSummaryResponse;
import com.studentleague.statistics.dto.PlayerStatisticsResponse;
import com.studentleague.statistics.service.StatisticsService;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.domain.CompetitionStage;
import com.studentleague.tournaments.domain.PlayoffSlotKind;
import com.studentleague.tournaments.domain.TournamentStatus;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.dto.StandingRow;
import com.studentleague.tournaments.dto.StandingTableResponse;
import com.studentleague.tournaments.entity.PlayoffSlot;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.repository.PlayoffSlotRepository;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import com.studentleague.tournaments.service.TournamentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class HomeOverviewService {

    private static final Logger log = LoggerFactory.getLogger(HomeOverviewService.class);
    private static final int FEED_LIMIT = 24;
    private static final int PER_TOURNAMENT = 3;
    private static final int LIVE_LIMIT = 10;
    private static final int SCORER_LIMIT = 5;

    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final MatchRepository matchRepository;
    private final PlayoffSlotRepository playoffSlotRepository;
    private final TeamRepository teamRepository;
    private final TournamentService tournamentService;
    private final StatisticsService statisticsService;
    private final MatchMapper matchMapper;
    private final AbandonedLiveMatchService abandonedLiveMatchService;

    public HomeOverviewService(
            TournamentRepository tournamentRepository,
            TournamentTeamRepository tournamentTeamRepository,
            MatchRepository matchRepository,
            PlayoffSlotRepository playoffSlotRepository,
            TeamRepository teamRepository,
            TournamentService tournamentService,
            StatisticsService statisticsService,
            MatchMapper matchMapper,
            AbandonedLiveMatchService abandonedLiveMatchService
    ) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.matchRepository = matchRepository;
        this.playoffSlotRepository = playoffSlotRepository;
        this.teamRepository = teamRepository;
        this.tournamentService = tournamentService;
        this.statisticsService = statisticsService;
        this.matchMapper = matchMapper;
        this.abandonedLiveMatchService = abandonedLiveMatchService;
    }

    @Transactional(readOnly = true)
    public HomeOverviewResponse overview(Instant now) {
        abandonedLiveMatchService.closeIdle(now);
        List<Tournament> running = runningTournaments();
        List<Match> upcomingEntities = matchRepository.findScheduledFrom(
                MatchStatus.SCHEDULED, now, PageRequest.of(0, FEED_LIMIT));
        List<Match> recentEntities = HomeLabels.diversifiedRecent(
                matchRepository.findRecentByStatus(MatchStatus.FINISHED, PageRequest.of(0, 80)),
                PER_TOURNAMENT,
                FEED_LIMIT
        );
        List<Match> liveEntities = liveMatches();

        Set<UUID> tournamentIds = new HashSet<>();
        running.forEach(tournament -> tournamentIds.add(tournament.getId()));
        collectTournaments(tournamentIds, upcomingEntities);
        collectTournaments(tournamentIds, recentEntities);
        collectTournaments(tournamentIds, liveEntities);

        List<Match> corpus = tournamentIds.isEmpty()
                ? List.of()
                : matchRepository.findByTournamentIdIn(tournamentIds);
        Map<UUID, List<Match>> byTournament = new HashMap<>();
        for (Match match : corpus) {
            byTournament.computeIfAbsent(match.getTournamentId(), id -> new ArrayList<>()).add(match);
        }

        Map<UUID, Tournament> tournamentsById = new HashMap<>();
        if (!tournamentIds.isEmpty()) {
            for (Tournament tournament : tournamentRepository.findAllById(tournamentIds)) {
                tournamentsById.put(tournament.getId(), tournament);
            }
        }

        TeamIndex roster = loadTeams(corpus, running);
        Map<UUID, Team> teams = roster.teams();
        BracketIndex bracket = bracketIndex(tournamentIds);
        Map<UUID, MatchResponse> mapped = mapMatches(displayMatches(liveEntities, upcomingEntities, recentEntities, running, byTournament));

        HomeMatchResponse next = nearest(now, corpus, tournamentsById, teams, byTournament, mapped, bracket);

        List<HomeTournamentResponse> cards = new ArrayList<>();
        List<HomeScorersResponse> scorers = new ArrayList<>();
        for (Tournament tournament : running) {
            List<Match> matches = byTournament.getOrDefault(tournament.getId(), List.of());
            cards.add(card(tournament, matches, now, tournamentsById, teams, roster.approvedCounts(), mapped, bracket));
            if (tournament.getStatus() == TournamentStatus.ACTIVE) {
                scorers.add(scorersOf(tournament, teams));
            }
        }

        LeagueSummaryResponse summary = statisticsService.summary(null);
        return new HomeOverviewResponse(
                now,
                next,
                toHomeList(liveEntities, tournamentsById, teams, byTournament, mapped, bracket),
                toHomeList(upcomingEntities, tournamentsById, teams, byTournament, mapped, bracket),
                toHomeList(recentEntities, tournamentsById, teams, byTournament, mapped, bracket),
                cards,
                scorers,
                summary
        );
    }

    /**
     * «Ближайший матч» is the earliest SCHEDULED kickoff that is not in the past,
     * across every ACTIVE tournament. Live and paused matches stay in {@code live}.
     * A match with no date is skipped.
     */
    private HomeMatchResponse nearest(
            Instant now,
            List<Match> matches,
            Map<UUID, Tournament> tournaments,
            Map<UUID, Team> teams,
            Map<UUID, List<Match>> byTournament,
            Map<UUID, MatchResponse> mapped,
            BracketIndex bracket
    ) {
        Match chosen = matches.stream()
                .filter(match -> match.getStatus() == MatchStatus.SCHEDULED)
                .filter(match -> match.getScheduledAt() != null && !match.getScheduledAt().isBefore(now))
                .filter(match -> {
                    Tournament tournament = tournaments.get(match.getTournamentId());
                    return tournament != null && tournament.getStatus() == TournamentStatus.ACTIVE;
                })
                .min(Comparator.comparing(Match::getScheduledAt).thenComparing(Match::getId))
                .orElse(null);
        if (chosen == null) {
            return null;
        }
        return toHome(chosen, tournaments, teams, byTournament, mapped, bracket);
    }

    private BracketIndex bracketIndex(Set<UUID> tournamentIds) {
        Map<UUID, Integer> rounds = new HashMap<>();
        Set<UUID> thirdPlaceMatchIds = new HashSet<>();
        if (tournamentIds.isEmpty()) {
            return new BracketIndex(rounds, thirdPlaceMatchIds);
        }
        for (PlayoffSlot slot : playoffSlotRepository.findByTournamentIdIn(tournamentIds)) {
            if (slot.getKind() == PlayoffSlotKind.THIRD) {
                if (slot.getMatchId() != null) {
                    thirdPlaceMatchIds.add(slot.getMatchId());
                }
                continue;
            }
            rounds.merge(slot.getTournamentId(), slot.getRoundNumber(), Math::max);
        }
        return new BracketIndex(rounds, thirdPlaceMatchIds);
    }

    private List<Tournament> runningTournaments() {
        List<Tournament> active = new ArrayList<>(
                tournamentRepository.findByStatusOrderByStartDateDescCreatedAtDesc(TournamentStatus.ACTIVE));
        List<Tournament> registration = tournamentRepository.findByStatusOrderByStartDateDescCreatedAtDesc(
                TournamentStatus.REGISTRATION);
        active.sort(byStart());
        registration.sort(byStart());
        List<Tournament> all = new ArrayList<>(active.size() + registration.size());
        all.addAll(active);
        all.addAll(registration);
        return all;
    }

    private static Comparator<Tournament> byStart() {
        return Comparator
                .comparing(Tournament::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Tournament::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private List<Match> liveMatches() {
        List<Match> live = new ArrayList<>(matchRepository.findByStatusIn(List.of(MatchStatus.LIVE, MatchStatus.PAUSED)));
        live.sort(Comparator
                .comparingInt((Match match) -> match.getStatus() == MatchStatus.LIVE ? 0 : 1)
                .thenComparing(Match::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Match::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        if (live.size() > LIVE_LIMIT) {
            return live.subList(0, LIVE_LIMIT);
        }
        return live;
    }

    private HomeTournamentResponse card(
            Tournament tournament,
            List<Match> matches,
            Instant now,
            Map<UUID, Tournament> tournamentsById,
            Map<UUID, Team> teams,
            Map<UUID, Integer> approvedCounts,
            Map<UUID, MatchResponse> mapped,
            BracketIndex bracket
    ) {
        int rounds = bracket.rounds().getOrDefault(tournament.getId(), 0);
        RoundPlan plan = HomeLabels.plan(
                matches,
                tournament.getCompetitionStage(),
                rounds,
                bracket.thirdPlaceMatchIds()
        );
        int teamsCount = approvedCounts.getOrDefault(tournament.getId(), 0);
        HomeLeaderResponse leader = tournament.getStatus() == TournamentStatus.REGISTRATION
                ? null
                : leaderOf(tournament.getId(), teams);
        Match last = matches.stream()
                .filter(match -> match.getStatus() == MatchStatus.FINISHED)
                .max(Comparator.comparing(HomeLabels::recency))
                .orElse(null);
        Match next = matches.stream()
                .filter(match -> match.getStatus() == MatchStatus.SCHEDULED)
                .filter(match -> match.getScheduledAt() != null && !match.getScheduledAt().isBefore(now))
                .min(Comparator.comparing(Match::getScheduledAt).thenComparing(Match::getId))
                .orElse(null);
        boolean playoff = tournament.getCompetitionStage() == CompetitionStage.PLAYOFF;
        return new HomeTournamentResponse(
                tournament.getId(),
                tournament.getName(),
                HomeLabels.shortName(tournament.getName()),
                tournament.getStatus(),
                tournament.getSeasonYear(),
                HomeLabels.seasonLabel(tournament.getSeasonYear()),
                tournament.getStartDate(),
                tournament.getEndDate(),
                tournament.getFormat(),
                HomeLabels.formatTitle(tournament.getFormat()),
                teamsCount,
                tournament.getCompetitionStage(),
                HomeLabels.tournamentStageLabel(tournament.getStatus(), tournament.getFormat(), plan),
                plan.currentRound(),
                plan.roundsTotal(),
                HomeLabels.progress(matches, tournament.getStatus(), playoff),
                leader,
                playoff ? plan.roundTitle() : null,
                playoff ? plan.playedPairs() : null,
                playoff ? plan.totalPairs() : null,
                tournament.getStatus() == TournamentStatus.REGISTRATION ? tournament.getStartDate() : null,
                last == null ? null : toHome(last, tournamentsById, teams, Map.of(tournament.getId(), matches), mapped, bracket),
                next == null ? null : toHome(next, tournamentsById, teams, Map.of(tournament.getId(), matches), mapped, bracket)
        );
    }

    private HomeLeaderResponse leaderOf(UUID tournamentId, Map<UUID, Team> teams) {
        try {
            List<StandingTableResponse> tables = tournamentService.standings(tournamentId).tables();
            if (tables == null) {
                return null;
            }
            for (StandingTableResponse table : tables) {
                if (table.rows() == null || table.rows().isEmpty()) {
                    continue;
                }
                StandingRow row = table.rows().getFirst();
                if (row.teamId() != null && !teams.containsKey(row.teamId())) {
                    rememberTeams(teams, List.of(row.teamId()));
                }
                Team team = row.teamId() == null ? null : teams.get(row.teamId());
                String name = team != null ? team.getName() : row.teamName();
                return new HomeLeaderResponse(
                        row.teamId(),
                        name,
                        teamShort(team, name),
                        team == null ? null : blankToNull(team.getLogoUrl()),
                        row.points(),
                        row.played(),
                        table.name()
                );
            }
            return null;
        } catch (RuntimeException ex) {
            log.warn("Home leader skipped for {}: {}", tournamentId, ex.getMessage());
            return null;
        }
    }

    private HomeScorersResponse scorersOf(Tournament tournament, Map<UUID, Team> teams) {
        List<HomeScorerResponse> rows = new ArrayList<>();
        try {
            List<PlayerStatisticsResponse> scorers = statisticsService.board(tournament.getId(), SCORER_LIMIT).scorers();
            if (scorers != null) {
                List<PlayerStatisticsResponse> picked = new ArrayList<>();
                List<UUID> missingTeams = new ArrayList<>();
                for (PlayerStatisticsResponse scorer : scorers) {
                    if (scorer.goals() <= 0) {
                        continue;
                    }
                    picked.add(scorer);
                    if (scorer.teamId() != null && !teams.containsKey(scorer.teamId())) {
                        missingTeams.add(scorer.teamId());
                    }
                    if (picked.size() >= SCORER_LIMIT) {
                        break;
                    }
                }
                rememberTeams(teams, missingTeams);
                for (PlayerStatisticsResponse scorer : picked) {
                    Team team = scorer.teamId() == null ? null : teams.get(scorer.teamId());
                    String teamName = team != null ? team.getName() : null;
                    rows.add(new HomeScorerResponse(
                            scorer.playerId(),
                            displayName(scorer),
                            scorer.firstName(),
                            scorer.lastName(),
                            scorer.goals(),
                            scorer.assists(),
                            scorer.appearances(),
                            scorer.teamId(),
                            teamName,
                            scorer.avatarUrl(),
                            scorer.teamLogoUrl() != null ? scorer.teamLogoUrl() : (team == null ? null : blankToNull(team.getLogoUrl()))
                    ));
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Home scorers skipped for {}: {}", tournament.getId(), ex.getMessage());
        }
        return new HomeScorersResponse(
                tournament.getId(),
                tournament.getName(),
                HomeLabels.shortName(tournament.getName()),
                rows
        );
    }

    private static String displayName(PlayerStatisticsResponse scorer) {
        if (scorer.displayName() != null && !scorer.displayName().isBlank()) {
            return scorer.displayName();
        }
        String first = scorer.firstName() == null ? "" : scorer.firstName().trim();
        String last = scorer.lastName() == null ? "" : scorer.lastName().trim();
        String joined = (first + " " + last).trim();
        return joined.isBlank() ? "Игрок" : joined;
    }

    private TeamIndex loadTeams(List<Match> matches, List<Tournament> running) {
        Set<UUID> ids = new HashSet<>();
        for (Match match : matches) {
            if (match.getHomeTeamId() != null) {
                ids.add(match.getHomeTeamId());
            }
            if (match.getAwayTeamId() != null) {
                ids.add(match.getAwayTeamId());
            }
        }
        Map<UUID, Integer> approvedCounts = new HashMap<>();
        if (!running.isEmpty()) {
            List<UUID> tournamentIds = running.stream().map(Tournament::getId).toList();
            for (TournamentTeam entry : tournamentTeamRepository.findByTournamentIdIn(tournamentIds)) {
                if (entry.getStatus() != TournamentTeamStatus.APPROVED) {
                    continue;
                }
                approvedCounts.merge(entry.getTournamentId(), 1, Integer::sum);
                if (entry.getTeamId() != null) {
                    ids.add(entry.getTeamId());
                }
            }
        }
        Map<UUID, Team> teams = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Team team : teamRepository.findAllById(ids)) {
                teams.put(team.getId(), team);
            }
        }
        return new TeamIndex(teams, approvedCounts);
    }

    private void rememberTeams(Map<UUID, Team> teams, List<UUID> ids) {
        Set<UUID> missing = new HashSet<>();
        for (UUID id : ids) {
            if (id != null && !teams.containsKey(id)) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        for (Team team : teamRepository.findAllById(missing)) {
            teams.put(team.getId(), team);
        }
    }

    private List<Match> displayMatches(
            List<Match> live,
            List<Match> upcoming,
            List<Match> recent,
            List<Tournament> running,
            Map<UUID, List<Match>> byTournament
    ) {
        LinkedHashMap<UUID, Match> unique = new LinkedHashMap<>();
        addAll(unique, live);
        addAll(unique, upcoming);
        addAll(unique, recent);
        Instant now = Instant.now();
        for (Tournament tournament : running) {
            List<Match> matches = byTournament.getOrDefault(tournament.getId(), List.of());
            matches.stream()
                    .filter(match -> match.getStatus() == MatchStatus.FINISHED)
                    .max(Comparator.comparing(HomeLabels::recency))
                    .ifPresent(match -> unique.putIfAbsent(match.getId(), match));
            matches.stream()
                    .filter(match -> match.getStatus() == MatchStatus.SCHEDULED)
                    .filter(match -> match.getScheduledAt() != null && !match.getScheduledAt().isBefore(now))
                    .min(Comparator.comparing(Match::getScheduledAt))
                    .ifPresent(match -> unique.putIfAbsent(match.getId(), match));
        }
        return new ArrayList<>(unique.values());
    }

    private Map<UUID, MatchResponse> mapMatches(List<Match> matches) {
        Map<UUID, MatchResponse> mapped = new HashMap<>();
        for (MatchResponse response : matchMapper.toList(matches)) {
            mapped.put(response.id(), response);
        }
        return mapped;
    }

    private List<HomeMatchResponse> toHomeList(
            List<Match> matches,
            Map<UUID, Tournament> tournaments,
            Map<UUID, Team> teams,
            Map<UUID, List<Match>> byTournament,
            Map<UUID, MatchResponse> mapped,
            BracketIndex bracket
    ) {
        List<HomeMatchResponse> rows = new ArrayList<>();
        for (Match match : matches) {
            rows.add(toHome(match, tournaments, teams, byTournament, mapped, bracket));
        }
        return rows;
    }

    private HomeMatchResponse toHome(
            Match match,
            Map<UUID, Tournament> tournaments,
            Map<UUID, Team> teams,
            Map<UUID, List<Match>> byTournament,
            Map<UUID, MatchResponse> mapped,
            BracketIndex bracket
    ) {
        MatchResponse response = mapped.get(match.getId());
        if (response == null) {
            response = matchMapper.toResponse(match);
        }
        Tournament tournament = tournaments.get(match.getTournamentId());
        String tournamentName = tournament == null ? "" : tournament.getName();
        Team home = teams.get(match.getHomeTeamId());
        Team away = teams.get(match.getAwayTeamId());
        List<Match> siblings = byTournament.getOrDefault(match.getTournamentId(), List.of(match));
        return new HomeMatchResponse(
                match.getId(),
                match.getTournamentId(),
                tournamentName,
                HomeLabels.shortName(tournamentName),
                match.getScheduledAt(),
                match.getStartedAt(),
                match.getFinishedAt(),
                match.getStatus(),
                match.getHomeTeamId(),
                teamName(home),
                teamShort(home, teamName(home)),
                home == null ? null : blankToNull(home.getLogoUrl()),
                match.getAwayTeamId(),
                teamName(away),
                teamShort(away, teamName(away)),
                away == null ? null : blankToNull(away.getLogoUrl()),
                response.homeScore(),
                response.awayScore(),
                response.minute(),
                response.gameTimeSeconds(),
                response.period(),
                response.periodCount(),
                response.periodLengthSeconds(),
                response.clockRunningSince(),
                response.bracketRound(),
                response.penaltyHome(),
                response.penaltyAway(),
                HomeLabels.matchStageLabel(
                        match,
                        siblings,
                        bracket.rounds().getOrDefault(match.getTournamentId(), 0),
                        bracket.thirdPlaceMatchIds().contains(match.getId())
                ),
                match.getBracketRound() != null ? match.getBracketRound() : HomeLabels.groupRoundOf(match, siblings)
        );
    }

    private static void collectTournaments(Set<UUID> ids, List<Match> matches) {
        for (Match match : matches) {
            if (match.getTournamentId() != null) {
                ids.add(match.getTournamentId());
            }
        }
    }

    private static void addAll(Map<UUID, Match> unique, List<Match> matches) {
        for (Match match : matches) {
            if (match.getId() != null) {
                unique.putIfAbsent(match.getId(), match);
            }
        }
    }

    private static String teamName(Team team) {
        return team == null || team.getName() == null || team.getName().isBlank() ? "Команда" : team.getName();
    }

    private static String teamShort(Team team, String fallback) {
        if (team != null && team.getShortName() != null && !team.getShortName().isBlank()) {
            return team.getShortName();
        }
        return HomeLabels.shortName(fallback);
    }

    private record BracketIndex(Map<UUID, Integer> rounds, Set<UUID> thirdPlaceMatchIds) {
    }

    private record TeamIndex(Map<UUID, Team> teams, Map<UUID, Integer> approvedCounts) {
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
