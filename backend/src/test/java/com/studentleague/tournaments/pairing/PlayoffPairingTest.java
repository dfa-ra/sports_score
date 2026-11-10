package com.studentleague.tournaments.pairing;

import com.studentleague.tournaments.domain.QualifyStage;
import com.studentleague.tournaments.pairing.PlayoffPairing.Entrant;
import com.studentleague.tournaments.pairing.PlayoffPairing.Side;
import com.studentleague.tournaments.pairing.PlayoffPairing.SlotDraft;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.GroupSize;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.Report;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.RuleSpan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayoffPairingTest {

    private final UUID groupA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID groupB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private final UUID groupC = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Test
    void zakharSeedsKeepDirectsBesideAWinnerAndDoNotPairAGroupWithItself() {
        List<SlotDraft> slots = PlayoffPairing.arrange(zakharStages(), zakharEntrants());
        List<SlotDraft> first = round(slots, 1);
        assertEquals(8, first.size());
        assertEquals(4, first.stream().filter(SlotDraft::bye).count());
        assertEquals(4, first.stream().filter(slot -> !slot.bye()).count());
        for (SlotDraft match : first) {
            if (!match.bye()) {
                assertFalse(match.home().groupId().equals(match.away().groupId()), match.home().groupName() + match.home().place());
            }
        }
        List<SlotDraft> quarters = round(slots, 2);
        assertEquals(4, quarters.size());
        for (SlotDraft quarter : quarters) {
            int filled = (quarter.home() == null ? 0 : 1) + (quarter.away() == null ? 0 : 1);
            assertEquals(1, filled);
            assertFalse(quarter.bye());
        }
        assertEquals(2, round(slots, 3).size());
        assertEquals(1, round(slots, 4).size());
        assertTrue(round(slots, 4).getFirst().home() == null && round(slots, 4).getFirst().away() == null);
        assertEquals(12, placed(slots).size());
        assertTrue(placed(slots).contains(groupA + ":1"));
        assertTrue(placed(slots).contains(groupC + ":4"));
    }

    @Test
    void manualEditReplacesAPairAndRejectsADuplicate() {
        List<Entrant> entrants = zakharEntrants();
        List<SlotDraft> auto = PlayoffPairing.arrange(zakharStages(), entrants);
        SlotDraft first = auto.stream().filter(slot -> !slot.bye() && slot.roundNumber() == 1 && slot.pairIndex() == 0).findFirst().orElseThrow();
        SlotDraft second = auto.stream().filter(slot -> !slot.bye() && slot.roundNumber() == 1 && slot.pairIndex() == 1).findFirst().orElseThrow();
        List<SlotDraft> edited = PlayoffPairing.arrange(zakharStages(), entrants, List.of(
                new PlayoffPairing.Edit(QualifyStage.R8, 0, second.home().groupId(), second.home().place(), first.away().groupId(), first.away().place()),
                new PlayoffPairing.Edit(QualifyStage.R8, 1, first.home().groupId(), first.home().place(), second.away().groupId(), second.away().place())
        ));
        SlotDraft rewritten = edited.stream().filter(slot -> !slot.bye() && slot.pairIndex() == 0 && slot.stage() == QualifyStage.R8).findFirst().orElseThrow();
        assertEquals(second.home().place(), rewritten.home().place());
        assertEquals(second.home().groupId(), rewritten.home().groupId());

        assertThrows(PlayoffPairing.PairingRejected.class, () -> PlayoffPairing.arrange(zakharStages(), entrants, List.of(
                new PlayoffPairing.Edit(QualifyStage.R8, 0, first.home().groupId(), first.home().place(), first.home().groupId(), first.home().place())
        )));
    }

    @Test
    void twoTeamStageIsASingleFinalMatch() {
        UUID group = UUID.randomUUID();
        Report report = PlayoffQualifyMath.evaluate(
                List.of(new GroupSize(group, "Группа A", 2)),
                List.of(new RuleSpan(group, 1, 2, QualifyStage.SF))
        );
        assertNull(report.failure());
        List<SlotDraft> slots = PlayoffPairing.arrange(report.stages(), List.of(
                new Entrant(group, "Группа A", 1, 0, QualifyStage.SF, null),
                new Entrant(group, "Группа A", 2, 0, QualifyStage.SF, null)
        ));
        assertEquals(1, slots.size());
        assertFalse(slots.getFirst().bye());
        assertEquals(1, slots.getFirst().home().place());
        assertEquals(2, slots.getFirst().away().place());
    }

    private static List<SlotDraft> round(List<SlotDraft> slots, int round) {
        return slots.stream().filter(slot -> slot.roundNumber() == round).toList();
    }

    private static Set<String> placed(List<SlotDraft> slots) {
        Set<String> keys = new HashSet<>();
        for (SlotDraft slot : slots) {
            add(keys, slot.bye() ? slot.home() : null);
            if (!slot.bye()) {
                add(keys, slot.home());
                add(keys, slot.away());
            }
        }
        return keys;
    }

    private static void add(Set<String> keys, Side side) {
        if (side != null) {
            keys.add(side.groupId() + ":" + side.place());
        }
    }

    private List<com.studentleague.tournaments.qualify.PlayoffQualifyMath.StageLine> zakharStages() {
        return PlayoffQualifyMath.evaluate(
                List.of(
                        new GroupSize(groupA, "Группа A", 6),
                        new GroupSize(groupB, "Группа B", 6),
                        new GroupSize(groupC, "Группа C", 8)
                ),
                List.of(
                        new RuleSpan(groupA, 1, 2, QualifyStage.QF),
                        new RuleSpan(groupA, 3, 4, QualifyStage.R8),
                        new RuleSpan(groupB, 1, 2, QualifyStage.QF),
                        new RuleSpan(groupB, 3, 4, QualifyStage.R8),
                        new RuleSpan(groupC, 1, 4, QualifyStage.R8)
                )
        ).stages();
    }

    private List<Entrant> zakharEntrants() {
        List<Entrant> entrants = new ArrayList<>();
        add(entrants, groupA, "Группа A", 0, 1, QualifyStage.QF);
        add(entrants, groupA, "Группа A", 0, 2, QualifyStage.QF);
        add(entrants, groupA, "Группа A", 0, 3, QualifyStage.R8);
        add(entrants, groupA, "Группа A", 0, 4, QualifyStage.R8);
        add(entrants, groupB, "Группа B", 1, 1, QualifyStage.QF);
        add(entrants, groupB, "Группа B", 1, 2, QualifyStage.QF);
        add(entrants, groupB, "Группа B", 1, 3, QualifyStage.R8);
        add(entrants, groupB, "Группа B", 1, 4, QualifyStage.R8);
        add(entrants, groupC, "Группа C", 2, 1, QualifyStage.R8);
        add(entrants, groupC, "Группа C", 2, 2, QualifyStage.R8);
        add(entrants, groupC, "Группа C", 2, 3, QualifyStage.R8);
        add(entrants, groupC, "Группа C", 2, 4, QualifyStage.R8);
        return entrants;
    }

    private static void add(List<Entrant> entrants, UUID groupId, String name, int order, int place, QualifyStage stage) {
        entrants.add(new Entrant(groupId, name, place, order, stage, null));
    }
}
