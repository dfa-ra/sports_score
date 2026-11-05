package com.studentleague.tournaments.service;

import com.studentleague.tournaments.domain.PlayoffSlotKind;
import com.studentleague.tournaments.entity.PlayoffSlot;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayoffWaitingLabelTest {

    @Test
    void thirdPlaceEmptySidesNameTheSemifinalLosers() {
        PlayoffSlot slot = new PlayoffSlot();
        slot.setRoundNumber(3);
        slot.setSlotIndex(1);
        slot.setKind(PlayoffSlotKind.THIRD);

        assertEquals("Проигравший 1/2 №1", PlayoffService.waitingLabel(slot, true, 3));
        assertEquals("Проигравший 1/2 №2", PlayoffService.waitingLabel(slot, false, 3));
    }

    @Test
    void filledThirdPlaceSideHasNoPlaceholder() {
        PlayoffSlot slot = new PlayoffSlot();
        slot.setRoundNumber(3);
        slot.setSlotIndex(1);
        slot.setKind(PlayoffSlotKind.THIRD);
        slot.setHomeTeamId(UUID.randomUUID());

        assertNull(PlayoffService.waitingLabel(slot, true, 3));
        assertEquals("Проигравший 1/2 №2", PlayoffService.waitingLabel(slot, false, 3));
    }

    @Test
    void finalStillWaitsForSemifinalWinners() {
        PlayoffSlot slot = new PlayoffSlot();
        slot.setRoundNumber(3);
        slot.setSlotIndex(0);
        slot.setKind(PlayoffSlotKind.MAIN);

        assertEquals("Победитель 1/2 №1", PlayoffService.waitingLabel(slot, true, 3));
        assertEquals("Победитель 1/2 №2", PlayoffService.waitingLabel(slot, false, 3));
    }
}
