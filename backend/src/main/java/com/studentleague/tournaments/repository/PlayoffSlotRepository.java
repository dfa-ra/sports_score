package com.studentleague.tournaments.repository;

import com.studentleague.tournaments.entity.PlayoffSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayoffSlotRepository extends JpaRepository<PlayoffSlot, UUID> {
    List<PlayoffSlot> findByTournamentIdOrderByRoundNumberAscSlotIndexAsc(UUID tournamentId);

    Optional<PlayoffSlot> findByTournamentIdAndRoundNumberAndSlotIndex(
            UUID tournamentId,
            int roundNumber,
            int slotIndex
    );

    Optional<PlayoffSlot> findByMatchId(UUID matchId);

    Optional<PlayoffSlot> findByIdAndTournamentId(UUID id, UUID tournamentId);

    void deleteByTournamentId(UUID tournamentId);
}
