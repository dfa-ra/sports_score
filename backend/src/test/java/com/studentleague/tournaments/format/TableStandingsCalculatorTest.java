package com.studentleague.tournaments.format;

import com.studentleague.matches.entity.Match;
import com.studentleague.teams.entity.Team;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.dto.StandingFormMark;
import com.studentleague.tournaments.entity.TournamentTeam;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TableStandingsCalculatorTest {

    private final TableStandingsCalculator calculator = new TableStandingsCalculator();

    @Test
    void ordersByPointsThenGoalDifference() {
        UUID alpha = UUID.randomUUID();
        UUID beta = UUID.randomUUID();
        UUID gamma = UUID.randomUUID();

        var rows = calculator.compute(new StandingsContext(
                null,
                List.of(entry(alpha), entry(beta), entry(gamma)),
                List.of(
                        finished(alpha, beta, 3, 0),
                        finished(alpha, gamma, 1, 1),
                        finished(beta, gamma, 0, 2)
                ),
                Map.of(
                        alpha, team(alpha, "Alpha"),
                        beta, team(beta, "Beta"),
                        gamma, team(gamma, "Gamma")
                )
        ));

        assertThat(rows).extracting(row -> row.teamName()).containsExactly("Alpha", "Gamma", "Beta");
        assertThat(rows).extracting(row -> row.points()).containsExactly(4, 4, 0);
        assertThat(rows.get(0).goalsFor() - rows.get(0).goalsAgainst()).isEqualTo(3);
        assertThat(rows.get(1).goalsFor() - rows.get(1).goalsAgainst()).isEqualTo(2);
    }

    @Test
    void headToHeadRanksWinnerAboveTeamWithBetterGoalDifference() {
        UUID alpha = UUID.randomUUID();
        UUID beta = UUID.randomUUID();
        UUID gamma = UUID.randomUUID();

        var rows = calculator.compute(new StandingsContext(
                null,
                List.of(entry(alpha), entry(beta), entry(gamma)),
                List.of(
                        finished(alpha, beta, 1, 0),
                        finished(beta, gamma, 5, 0)
                ),
                Map.of(
                        alpha, team(alpha, "A"),
                        beta, team(beta, "B"),
                        gamma, team(gamma, "Gamma")
                )
        ));

        assertThat(rows).extracting(row -> row.teamName()).containsExactly("A", "B", "Gamma");
        assertThat(rows).extracting(row -> row.points()).containsExactly(3, 3, 0);
        assertThat(rows.get(1).goalsFor() - rows.get(1).goalsAgainst())
                .isGreaterThan(rows.get(0).goalsFor() - rows.get(0).goalsAgainst());
    }

    @Test
    void reappliesHeadToHeadAfterPointsSplitTheTiedGroup() {
        UUID alpha = UUID.randomUUID();
        UUID beta = UUID.randomUUID();
        UUID gamma = UUID.randomUUID();
        UUID delta = UUID.randomUUID();
        UUID epsilon = UUID.randomUUID();
        UUID foe = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID host = UUID.randomUUID();

        var rows = calculator.compute(new StandingsContext(
                null,
                List.of(
                        entry(alpha), entry(beta), entry(gamma),
                        entry(delta), entry(epsilon), entry(foe), entry(guest), entry(host)
                ),
                List.of(
                        finished(alpha, beta, 1, 0),
                        finished(alpha, gamma, 0, 0),
                        finished(gamma, beta, 5, 0),
                        finished(alpha, delta, 6, 0),
                        finished(gamma, epsilon, 1, 0),
                        finished(beta, foe, 1, 0),
                        finished(beta, guest, 1, 0),
                        finished(beta, host, 0, 0)
                ),
                Map.of(
                        alpha, team(alpha, "A"),
                        beta, team(beta, "B"),
                        gamma, team(gamma, "C"),
                        delta, team(delta, "D"),
                        epsilon, team(epsilon, "E"),
                        foe, team(foe, "F"),
                        guest, team(guest, "G"),
                        host, team(host, "H")
                )
        ));

        assertThat(rows).extracting(row -> row.teamName()).startsWith("A", "C", "B");
        assertThat(rows.get(0).points()).isEqualTo(7);
        assertThat(rows.get(1).points()).isEqualTo(7);
        assertThat(rows.get(2).points()).isEqualTo(7);
    }

    @Test
    void formKeepsTheLastFourResultsWithTheNewestOnTheRight() {
        UUID home = UUID.randomUUID();
        UUID away = UUID.randomUUID();
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Match match = finished(home, away, i % 2 == 0 ? 1 : 0, i % 2 == 0 ? 0 : 1);
            match.setId(UUID.randomUUID());
            match.setFinishedAt(Instant.parse("2026-01-0" + (i + 1) + "T12:00:00Z"));
            matches.add(match);
        }

        var rows = calculator.compute(new StandingsContext(
                null,
                List.of(entry(home), entry(away)),
                matches,
                Map.of(home, team(home, "Home"), away, team(away, "Away"))
        ));

        var homeForm = rows.stream().filter(row -> row.teamName().equals("Home")).findFirst().orElseThrow();
        assertThat(homeForm.form()).extracting(StandingFormMark::result).containsExactly("W", "L", "W", "L");
        assertThat(rows).allSatisfy(row -> assertThat(row.form()).hasSize(TableStandingsCalculator.FORM_LIMIT));
    }

    private static TournamentTeam entry(UUID teamId) {
        TournamentTeam row = new TournamentTeam();
        row.setTeamId(teamId);
        row.setStatus(TournamentTeamStatus.APPROVED);
        return row;
    }

    private static Team team(UUID id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        return team;
    }

    private static Match finished(UUID home, UUID away, int homeScore, int awayScore) {
        Match match = new Match();
        match.setHomeTeamId(home);
        match.setAwayTeamId(away);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        return match;
    }
}
