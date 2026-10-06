package com.studentleague.teams;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.entity.MatchLineupPlayer;
import com.studentleague.teams.dto.TeamPlayerStatResponse;
import com.studentleague.teams.service.TeamSquadStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TeamSquadStatsTest {

    @Test
    void goalAndAssistCountAndALineupWithoutAGoalStillCountsAsAGame() {
        UUID teamId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        UUID scorerId = UUID.randomUUID();
        UUID assistId = UUID.randomUUID();
        UUID benchId = UUID.randomUUID();
        UUID spareId = UUID.randomUUID();
        UUID otherClub = UUID.randomUUID();
        UUID scoredMatch = UUID.randomUUID();
        UUID lineupMatch = UUID.randomUUID();
        UUID liveMatch = UUID.randomUUID();
        UUID otherMatch = UUID.randomUUID();

        List<TeamSquadStats.Player> squad = List.of(
                new TeamSquadStats.Player(scorerId, "Кирилл", "Кучеров", "/media/k.jpg"),
                new TeamSquadStats.Player(assistId, "Сергей", "Чуприн", null),
                new TeamSquadStats.Player(benchId, "Макар", "Зыков", null),
                new TeamSquadStats.Player(spareId, "Андрей", "Алехин", null)
        );

        Match scored = finished(scoredMatch, teamId, opponentId);
        Match lined = finished(lineupMatch, opponentId, teamId);
        Match live = finished(liveMatch, teamId, opponentId);
        live.setStatus(MatchStatus.LIVE);
        Match elsewhere = finished(otherMatch, otherClub, opponentId);

        List<TeamPlayerStatResponse> rows = TeamSquadStats.aggregate(
                teamId,
                squad,
                List.of(scored, lined, live, elsewhere),
                List.of(
                        goal(scoredMatch, teamId, scorerId, assistId),
                        yellow(scoredMatch, teamId, scorerId),
                        goal(scoredMatch, opponentId, UUID.randomUUID(), null),
                        goal(liveMatch, teamId, scorerId, assistId),
                        goal(otherMatch, otherClub, scorerId, null),
                        event(scoredMatch, MatchEventType.GOAL, teamId, benchId, null, true)
                ),
                List.of(lineup(lineupMatch, teamId, benchId))
        );

        assertThat(rows).extracting(TeamPlayerStatResponse::playerId)
                .containsExactly(scorerId, assistId, benchId, spareId);

        TeamPlayerStatResponse scorer = rows.get(0);
        assertThat(scorer.firstName()).isEqualTo("Кирилл");
        assertThat(scorer.lastName()).isEqualTo("Кучеров");
        assertThat(scorer.photoUrl()).isEqualTo("/media/k.jpg");
        assertThat(scorer.goals()).isEqualTo(1);
        assertThat(scorer.assists()).isZero();
        assertThat(scorer.appearances()).isEqualTo(1);
        assertThat(scorer.yellowCards()).isEqualTo(1);

        TeamPlayerStatResponse assist = rows.get(1);
        assertThat(assist.goals()).isZero();
        assertThat(assist.assists()).isEqualTo(1);
        assertThat(assist.appearances()).isEqualTo(1);
        assertThat(assist.yellowCards()).isZero();

        TeamPlayerStatResponse bench = rows.get(2);
        assertThat(bench.goals()).isZero();
        assertThat(bench.assists()).isZero();
        assertThat(bench.appearances()).isEqualTo(1);
        assertThat(bench.yellowCards()).isZero();

        TeamPlayerStatResponse spare = rows.get(3);
        assertThat(spare.goals()).isZero();
        assertThat(spare.assists()).isZero();
        assertThat(spare.appearances()).isZero();
    }

    @Test
    void emptySquadIsAnEmptyList() {
        assertThat(TeamSquadStats.aggregate(UUID.randomUUID(), List.of(), List.of(), List.of(), List.of())).isEmpty();
    }

    @Test
    void sameTotalsSortByRussianName() {
        UUID teamId = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        UUID earlier = UUID.randomUUID();
        List<TeamPlayerStatResponse> rows = TeamSquadStats.aggregate(
                teamId,
                List.of(
                        new TeamSquadStats.Player(later, "Никита", "Остапишин", null),
                        new TeamSquadStats.Player(earlier, "Андрей", "Алехин", null)
                ),
                List.of(),
                List.of(),
                List.of()
        );
        assertThat(rows).extracting(TeamPlayerStatResponse::playerId).containsExactly(earlier, later);
    }

    private static Match finished(UUID id, UUID homeId, UUID awayId) {
        Match match = new Match();
        match.setId(id);
        match.setHomeTeamId(homeId);
        match.setAwayTeamId(awayId);
        match.setStatus(MatchStatus.FINISHED);
        return match;
    }

    private static MatchEvent goal(UUID matchId, UUID teamId, UUID playerId, UUID assistId) {
        return event(matchId, MatchEventType.GOAL, teamId, playerId, assistId, false);
    }

    private static MatchEvent yellow(UUID matchId, UUID teamId, UUID playerId) {
        return event(matchId, MatchEventType.YELLOW_CARD, teamId, playerId, null, false);
    }

    private static MatchEvent event(
            UUID matchId,
            MatchEventType type,
            UUID teamId,
            UUID playerId,
            UUID secondaryPlayerId,
            boolean voided
    ) {
        MatchEvent event = new MatchEvent();
        event.setMatchId(matchId);
        event.setEventType(type);
        event.setTeamId(teamId);
        event.setPlayerId(playerId);
        event.setSecondaryPlayerId(secondaryPlayerId);
        event.setVoided(voided);
        event.setMetadata(Map.of());
        return event;
    }

    private static MatchLineupPlayer lineup(UUID matchId, UUID teamId, UUID playerId) {
        MatchLineupPlayer row = new MatchLineupPlayer();
        row.setMatchId(matchId);
        row.setTeamId(teamId);
        row.setPlayerId(playerId);
        row.setStarter(true);
        return row;
    }
}
