package com.studentleague.matches.repository;

import com.studentleague.matches.entity.MatchAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchAvailabilityRepository extends JpaRepository<MatchAvailability, UUID> {
    Optional<MatchAvailability> findByMatchIdAndPlayerId(UUID matchId, UUID playerId);

    List<MatchAvailability> findByMatchId(UUID matchId);

    void deleteByMatchId(UUID matchId);
}
