package com.studentleague.matches.futsal;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.dto.FutsalBoardResponse;
import com.studentleague.matches.dto.FutsalBoardResponse.TeamFutsalState;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FutsalBoardCalculatorTest {

    private final UUID home = UUID.randomUUID();
    private final UUID away = UUID.randomUUID();

    @Test
    void foulCountGrowsAndSixthIsFlaggedUntilTheHalfChanges() {
        Match match = periodMatch(1);
        List<MatchEvent> events = new ArrayList<>();

        assertThat(board(match, events).teams().get(0).fouls()).isZero();

        for (int i = 1; i <= 5; i++) {
            events.add(foul(home, 1));
            TeamFutsalState side = board(match, events).teams().get(0);
            assertThat(side.fouls()).isEqualTo(i);
            assertThat(side.tenMeters()).isFalse();
            assertThat(FutsalBoardCalculator.foulMetadata(null, events.subList(0, i - 1), home, match))
                    .isNull();
        }

        Map<String, Object> sixth = FutsalBoardCalculator.foulMetadata(null, events, home, match);
        assertThat(sixth).containsEntry("tenMeters", true);
        events.add(foul(home, 1));
        TeamFutsalState flagged = board(match, events).teams().get(0);
        assertThat(flagged.fouls()).isEqualTo(6);
        assertThat(flagged.tenMeters()).isTrue();
        assertThat(board(match, events).foulScope()).isEqualTo(FutsalBoardCalculator.PERIOD_SCOPE);

        events.add(foul(home, 2));
        match.setPeriod(2);
        TeamFutsalState nextHalf = board(match, events).teams().get(0);
        assertThat(nextHalf.fouls()).isEqualTo(1);
        assertThat(nextHalf.tenMeters()).isFalse();
    }

    @Test
    void secondTimeoutInTheSameHalfIsUsedAndTheNextHalfIsFree() {
        Match match = periodMatch(1);
        List<MatchEvent> events = new ArrayList<>();
        events.add(event(MatchEventType.TIMEOUT, home, 1, 10, null));

        assertThat(FutsalBoardCalculator.timeoutUsed(events, home, match)).isTrue();
        assertThat(FutsalBoardCalculator.timeoutUsed(events, away, match)).isFalse();
        assertThat(FutsalBoardCalculator.timeoutRejectedMessage(match)).contains("тайме");
        assertThat(board(match, events).teams().get(0).timeoutAvailable()).isFalse();
        assertThat(board(match, events).teams().get(1).timeoutAvailable()).isTrue();

        match.setPeriod(2);
        assertThat(FutsalBoardCalculator.timeoutUsed(events, home, match)).isFalse();
        assertThat(board(match, events).teams().get(0).timeoutAvailable()).isTrue();
    }

    @Test
    void withoutPeriodsFoulsAndTimeoutsCoverTheWholeMatch() {
        Match match = new Match();
        match.setHomeTeamId(home);
        match.setAwayTeamId(away);
        match.setPeriodCount(0);
        match.setPeriod(null);

        List<MatchEvent> events = List.of(foul(home, 1), foul(home, 2), event(MatchEventType.TIMEOUT, away, 1, 5, null));
        FutsalBoardResponse board = board(match, events);

        assertThat(board.foulScope()).isEqualTo(FutsalBoardCalculator.MATCH_SCOPE);
        assertThat(board.scopeNote()).isEqualTo(FutsalBoardCalculator.MATCH_NOTE);
        assertThat(board.teams().get(0).fouls()).isEqualTo(2);
        assertThat(board.teams().get(1).timeoutAvailable()).isFalse();
        assertThat(FutsalBoardCalculator.timeoutRejectedMessage(match)).contains("матче");
    }

    @Test
    void redCardIsShortHandedForTwoMinutesOrUntilTheOtherSideScores() {
        Match match = periodMatch(1);
        MatchEvent red = event(MatchEventType.RED_CARD, home, 1, 30, null);
        List<MatchEvent> events = new ArrayList<>();
        events.add(red);

        TeamFutsalState running = board(match, events, 40).teams().get(0);
        assertThat(running.shortHanded()).isTrue();
        assertThat(running.shortHandedRemainingSeconds()).isEqualTo(110);
        assertThat(running.shortHandedEndsAtGameTime()).isEqualTo(150);

        events.add(event(MatchEventType.GOAL, home, 1, 50, null));
        assertThat(board(match, events, 60).teams().get(0).shortHanded()).isTrue();

        events.add(event(MatchEventType.GOAL, away, 1, 70, null));
        assertThat(board(match, events, 80).teams().get(0).shortHanded()).isFalse();
        assertThat(board(match, events, 60).teams().get(0).shortHandedRemainingSeconds()).isEqualTo(10);
    }

    @Test
    void redCardStoredFromKickoffStillCountsInsideTheSecondHalf() {
        Match match = periodMatch(2);
        match.setPeriodLengthSeconds(15 * 60);
        // 0:30 into the second half is 15:30 from kickoff, not 30 seconds remaining.
        MatchEvent red = event(MatchEventType.RED_CARD, home, 2, 15 * 60 + 30, null);
        TeamFutsalState running = board(match, List.of(red), 40).teams().get(0);
        assertThat(running.shortHanded()).isTrue();
        assertThat(running.shortHandedRemainingSeconds()).isEqualTo(110);
    }

    @Test
    void ownGoalByTheShortHandedTeamEndsThePenalty() {
        Match match = periodMatch(1);
        List<MatchEvent> events = List.of(
                event(MatchEventType.RED_CARD, home, 1, 10, null),
                event(MatchEventType.GOAL, home, 1, 25, Map.of("ownGoal", true))
        );
        assertThat(board(match, events, 30).teams().get(0).shortHanded()).isFalse();
        assertThat(board(match, events, 20).teams().get(0).shortHandedRemainingSeconds()).isEqualTo(5);
    }

    private FutsalBoardResponse board(Match match, List<MatchEvent> events) {
        return board(match, events, 0);
    }

    private FutsalBoardResponse board(Match match, List<MatchEvent> events, int elapsed) {
        return FutsalBoardCalculator.build(match, events, elapsed);
    }

    private Match periodMatch(int period) {
        Match match = new Match();
        match.setHomeTeamId(home);
        match.setAwayTeamId(away);
        match.setPeriodCount(2);
        match.setPeriod(period);
        return match;
    }

    private MatchEvent foul(UUID teamId, int period) {
        return event(MatchEventType.FOUL, teamId, period, 0, null);
    }

    private MatchEvent event(
            MatchEventType type,
            UUID teamId,
            int period,
            int gameTime,
            Map<String, Object> metadata
    ) {
        MatchEvent event = new MatchEvent();
        event.setEventType(type);
        event.setTeamId(teamId);
        event.setPeriod(period);
        event.setGameTime(gameTime);
        event.setMetadata(metadata);
        event.setVoided(false);
        return event;
    }
}
