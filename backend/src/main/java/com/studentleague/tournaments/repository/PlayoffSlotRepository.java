package com.studentleague.tournaments.repository;

import com.studentleague.tournaments.entity.PlayoffSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayoffSlotRepository extends JpaRepository<PlayoffSlot, UUID> {
    List<PlayoffSlot> findByTournamentIdOrderByRoundNumberAscSlotIndexAsc(UUID tournamentId);

    List<PlayoffSlot> findByTournamentIdIn(Collection<UUID> tournamentIds);

    long countByTournamentId(UUID tournamentId);

    @Query("select s.matchId from PlayoffSlot s where s.tournamentId = :tournamentId and s.matchId is not null")
    List<UUID> findMatchIdsByTournamentId(@Param("tournamentId") UUID tournamentId);

    Optional<PlayoffSlot> findByTournamentIdAndRoundNumberAndSlotIndex(
            UUID tournamentId,
            int roundNumber,
            int slotIndex
    );

    Optional<PlayoffSlot> findByMatchId(UUID matchId);

    Optional<PlayoffSlot> findByIdAndTournamentId(UUID id, UUID tournamentId);
}
