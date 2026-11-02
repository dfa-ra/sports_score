package com.studentleague.matches.service;

import com.studentleague.tournaments.domain.TournamentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TournamentStatusOnStartTest {

    @Test
    void onlyARecruitingTournamentBecomesActiveWhenAMatchStarts() {
        assertEquals(TournamentStatus.ACTIVE, RefereeMatchService.statusAfterMatchStarts(TournamentStatus.REGISTRATION));
        assertEquals(TournamentStatus.FINISHED, RefereeMatchService.statusAfterMatchStarts(TournamentStatus.FINISHED));
        assertEquals(TournamentStatus.ACTIVE, RefereeMatchService.statusAfterMatchStarts(TournamentStatus.ACTIVE));
        assertEquals(TournamentStatus.CANCELLED, RefereeMatchService.statusAfterMatchStarts(TournamentStatus.CANCELLED));
        assertEquals(TournamentStatus.DRAFT, RefereeMatchService.statusAfterMatchStarts(TournamentStatus.DRAFT));
    }
}
