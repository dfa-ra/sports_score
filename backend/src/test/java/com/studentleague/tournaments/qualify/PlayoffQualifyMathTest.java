package com.studentleague.tournaments.qualify;

import com.studentleague.tournaments.domain.QualifyStage;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.GroupSize;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.Report;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.RuleSpan;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.StageLine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayoffQualifyMathTest {

    private final UUID groupA = UUID.randomUUID();
    private final UUID groupB = UUID.randomUUID();
    private final UUID groupC = UUID.randomUUID();

    @Test
    void zakharGroupsFeedEightIntoTheRoundOf16AndEightIntoTheQuarterfinal() {
        Report report = PlayoffQualifyMath.evaluate(zakharGroups(), zakharRules());
        assertNull(report.failure());
        assertEquals(4, report.stages().size());
        assertLine(report.stages().get(0), QualifyStage.R8, 8, 0, 8);
        assertLine(report.stages().get(1), QualifyStage.QF, 4, 4, 8);
        assertLine(report.stages().get(2), QualifyStage.SF, 0, 4, 4);
        assertLine(report.stages().get(3), QualifyStage.F, 0, 2, 2);
        assertEquals(
                "В 1/4 должно быть 8 команд: 4 напрямую + 4 победителя 1/8",
                PlayoffQualifyMath.powerMessage(report.stages().get(1), QualifyStage.R8)
        );
    }

    @Test
    void placeBeyondTheGroupIsRejectedAndNothingIsTreatedAsValid() {
        Report report = PlayoffQualifyMath.evaluate(
                List.of(new GroupSize(groupA, "Группа A", 6)),
                List.of(new RuleSpan(groupA, 1, 7, QualifyStage.QF))
        );
        assertEquals(PlayoffQualifyMath.PLACE_OUT_OF_RANGE, report.failure().code());
        assertTrue(report.failure().message().contains("6"));
    }

    @Test
    void onePlaceCannotBelongToTwoStages() {
        Report report = PlayoffQualifyMath.evaluate(
                List.of(new GroupSize(groupA, "Группа A", 6)),
                List.of(
                        new RuleSpan(groupA, 1, 2, QualifyStage.QF),
                        new RuleSpan(groupA, 2, 3, QualifyStage.R8)
                )
        );
        assertEquals(PlayoffQualifyMath.PLACE_OUT_OF_RANGE, report.failure().code());
        assertTrue(report.failure().message().contains("дважды"));
    }

    @Test
    void stageThatIsNotAPowerOfTwoIsRejected() {
        Report report = PlayoffQualifyMath.evaluate(
                zakharGroups(),
                List.of(
                        new RuleSpan(groupA, 1, 2, QualifyStage.QF),
                        new RuleSpan(groupB, 1, 2, QualifyStage.QF),
                        new RuleSpan(groupA, 3, 4, QualifyStage.R8),
                        new RuleSpan(groupB, 3, 4, QualifyStage.R8),
                        new RuleSpan(groupC, 1, 3, QualifyStage.R8)
                )
        );
        assertEquals(PlayoffQualifyMath.NOT_POWER_OF_TWO, report.failure().code());
        assertTrue(report.failure().message().startsWith("В 1/8 должно быть 8 команд: 7 напрямую"));
    }

    @Test
    void neighboursWithTheSameStageCollapse() {
        List<PlayoffQualifyMath.RuleSpan> collapsed = PlayoffQualifyMath.collapse(List.of(
                new RuleSpan(groupA, 2, 2, QualifyStage.QF),
                new RuleSpan(groupA, 1, 1, QualifyStage.QF),
                new RuleSpan(groupA, 3, 3, QualifyStage.OUT)
        ));
        assertEquals(2, collapsed.size());
        assertEquals(1, collapsed.get(0).placeFrom());
        assertEquals(2, collapsed.get(0).placeTo());
        assertEquals(QualifyStage.QF, collapsed.get(0).stage());
        assertEquals(3, collapsed.get(1).placeFrom());
        assertEquals(QualifyStage.OUT, collapsed.get(1).stage());
    }

    private static void assertLine(StageLine line, QualifyStage stage, int direct, int fromPrev, int total) {
        assertEquals(stage, line.stage());
        assertEquals(direct, line.direct());
        assertEquals(fromPrev, line.fromPrev());
        assertEquals(total, line.total());
        assertTrue(line.powerOfTwo());
    }

    private List<GroupSize> zakharGroups() {
        return List.of(
                new GroupSize(groupA, "Группа A", 6),
                new GroupSize(groupB, "Группа B", 6),
                new GroupSize(groupC, "Группа C", 8)
        );
    }

    private List<RuleSpan> zakharRules() {
        return List.of(
                new RuleSpan(groupA, 1, 2, QualifyStage.QF),
                new RuleSpan(groupA, 3, 4, QualifyStage.R8),
                new RuleSpan(groupA, 5, 6, QualifyStage.OUT),
                new RuleSpan(groupB, 1, 2, QualifyStage.QF),
                new RuleSpan(groupB, 3, 4, QualifyStage.R8),
                new RuleSpan(groupB, 5, 6, QualifyStage.OUT),
                new RuleSpan(groupC, 1, 4, QualifyStage.R8),
                new RuleSpan(groupC, 5, 8, QualifyStage.OUT)
        );
    }
}
