package com.studentleague.matches.shootout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShootoutRulesTest {

    private final UUID home = UUID.randomUUID();
    private final UUID away = UUID.randomUUID();

    @Test
    void fourThreeAfterFiveRoundsHasAWinner() {
        List<ShootoutRules.Kick> kicks = rounds(
                new boolean[] {true, true, true, true, false},
                new boolean[] {true, true, false, true, false}
        );
        assertTrue(ShootoutRules.decisive(home, away, kicks));
        assertEquals(home, ShootoutRules.winner(home, away, kicks));
        ShootoutRules.Tally tally = ShootoutRules.tally(home, kicks);
        assertEquals(4, tally.homeScored());
        assertEquals(3, tally.awayScored());
    }

    @Test
    void tiedSeriesIsNotClosedAndSuddenDeathEndsOnTheFirstMissThatBreaksTheTie() {
        List<ShootoutRules.Kick> level = rounds(
                new boolean[] {true, true, true, true, true},
                new boolean[] {true, true, true, true, true}
        );
        assertFalse(ShootoutRules.decisive(home, away, level));
        assertEquals(ShootoutRules.Problem.TIED, ShootoutRules.seriesProblem(home, away, level));
        assertNull(ShootoutRules.winner(home, away, level));

        List<ShootoutRules.Kick> oneSide = new ArrayList<>(level);
        oneSide.add(new ShootoutRules.Kick(home, true));
        assertFalse(ShootoutRules.decisive(home, away, oneSide));
        assertEquals(away, ShootoutRules.teamToKick(home, away, oneSide));

        oneSide.add(new ShootoutRules.Kick(away, false));
        assertTrue(ShootoutRules.decisive(home, away, oneSide));
        assertEquals(home, ShootoutRules.winner(home, away, oneSide));
        assertEquals(ShootoutRules.Problem.NONE, ShootoutRules.seriesProblem(home, away, oneSide));
    }

    @Test
    void threeFourStaysOpenUntilTheFifthKickOfTheLeadingSide() {
        List<ShootoutRules.Kick> open = rounds(
                new boolean[] {true, true, true, false},
                new boolean[] {true, true, false, true}
        );
        open.add(new ShootoutRules.Kick(home, false));
        assertFalse(ShootoutRules.decisive(home, away, open));
        List<ShootoutRules.Kick> closed = new ArrayList<>(open);
        closed.add(new ShootoutRules.Kick(away, true));
        assertEquals(3, ShootoutRules.tally(home, closed).homeScored());
        assertEquals(4, ShootoutRules.tally(home, closed).awayScored());
        assertTrue(ShootoutRules.decisive(home, away, closed));
        assertEquals(away, ShootoutRules.winner(home, away, closed));
        assertEquals(ShootoutRules.Problem.NONE, ShootoutRules.seriesProblem(home, away, closed));
    }

    @Test
    void baseRoundDoesNotStopWhileTheTrailerCanStillCatch() {
        List<ShootoutRules.Kick> early = rounds(
                new boolean[] {true},
                new boolean[] {false}
        );
        assertFalse(ShootoutRules.decisive(home, away, early));
        assertEquals(ShootoutRules.Problem.NOT_FINISHED, ShootoutRules.seriesProblem(home, away, early));

        List<ShootoutRules.Kick> outOfReach = rounds(
                new boolean[] {true, true, true},
                new boolean[] {false, false, false}
        );
        assertTrue(ShootoutRules.decisive(home, away, outOfReach));
        assertEquals(home, ShootoutRules.winner(home, away, outOfReach));
    }

    private List<ShootoutRules.Kick> rounds(boolean[] homeScored, boolean[] awayScored) {
        List<ShootoutRules.Kick> kicks = new ArrayList<>();
        for (int i = 0; i < homeScored.length; i++) {
            kicks.add(new ShootoutRules.Kick(home, homeScored[i]));
            kicks.add(new ShootoutRules.Kick(away, awayScored[i]));
        }
        return kicks;
    }
}
