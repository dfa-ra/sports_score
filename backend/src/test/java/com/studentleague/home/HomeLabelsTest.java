package com.studentleague.home;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.tournaments.domain.CompetitionStage;
import com.studentleague.tournaments.domain.TournamentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeLabelsTest {

    @Test
    void shortNameDropsTheTailAndTheYear() {
        assertEquals("Лига ИТМО", HomeLabels.shortName("Лига ИТМО по футзалу 2026/27"));
        assertEquals("Осенняя лига", HomeLabels.shortName("Осенняя лига факультетов"));
        assertEquals("Суперкубок ИТМО", HomeLabels.shortName("Суперкубок ИТМО по фиджитал-футболу"));
        assertEquals("Студенческий", HomeLabels.shortName("Студенческий кубок 2026"));
    }

    @Test
    void groupRoundIsTheMoscowCalendarDay() {
        Match early = match(Instant.parse("2026-11-02T18:00:00Z"), MatchStatus.FINISHED, null);
        Match later = match(Instant.parse("2026-11-03T16:30:00Z"), MatchStatus.SCHEDULED, null);
        HomeLabels.RoundPlan plan = HomeLabels.plan(List.of(early, later), CompetitionStage.GROUPS);
        assertEquals(2, plan.currentRound());
        assertEquals(2, plan.roundsTotal());
        assertEquals("Тур 1", HomeLabels.matchStageLabel(early, List.of(early, later)));
        assertEquals("Тур 2", HomeLabels.matchStageLabel(later, List.of(early, later)));
        assertEquals(
                "Тур 2 из 2",
                HomeLabels.tournamentStageLabel(TournamentStatus.ACTIVE, "ROUND_ROBIN", plan)
        );
    }

    @Test
    void playoffUsesTheOpenBracketRound() {
        Match done = match(Instant.parse("2026-11-01T16:00:00Z"), MatchStatus.FINISHED, 1);
        Match open = match(Instant.parse("2026-11-04T16:00:00Z"), MatchStatus.SCHEDULED, 1);
        Match semi = match(Instant.parse("2026-11-08T16:00:00Z"), MatchStatus.SCHEDULED, 2);
        HomeLabels.RoundPlan plan = HomeLabels.plan(List.of(done, open, semi), CompetitionStage.PLAYOFF);
        assertTrue(plan.playoff());
        assertEquals(1, plan.currentRound());
        assertEquals(2, plan.roundsTotal());
        assertEquals("1/2 финала", plan.roundTitle());
        assertEquals(1, plan.playedPairs());
        assertEquals(2, plan.totalPairs());
        assertEquals("1/2 финала", HomeLabels.matchStageLabel(open, List.of(done, open, semi)));
        assertEquals("Финал", HomeLabels.matchStageLabel(semi, List.of(done, open, semi)));
    }

    @Test
    void registrationHasNoProgress() {
        assertNull(HomeLabels.progress(List.of(), TournamentStatus.REGISTRATION, false));
    }

    @Test
    void recentPrefersDifferentTournaments() {
        UUID alpha = UUID.randomUUID();
        UUID beta = UUID.randomUUID();
        Match alphaNew = finished(alpha, Instant.parse("2026-11-03T12:00:00Z"));
        Match alphaOld = finished(alpha, Instant.parse("2026-11-01T12:00:00Z"));
        Match betaNew = finished(beta, Instant.parse("2026-11-02T12:00:00Z"));
        List<Match> recent = HomeLabels.diversifiedRecent(List.of(alphaNew, betaNew, alphaOld), 3, 24);
        assertEquals(alphaNew.getId(), recent.get(0).getId());
        assertEquals(betaNew.getId(), recent.get(1).getId());
        assertEquals(alphaOld.getId(), recent.get(2).getId());
    }

    private static Match match(Instant kickoff, MatchStatus status, Integer bracket) {
        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setScheduledAt(kickoff);
        match.setStatus(status);
        match.setBracketRound(bracket);
        return match;
    }

    private static Match finished(UUID tournamentId, Instant finishedAt) {
        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setTournamentId(tournamentId);
        match.setStatus(MatchStatus.FINISHED);
        match.setScheduledAt(finishedAt.minusSeconds(3600));
        match.setFinishedAt(finishedAt);
        return match;
    }
}
