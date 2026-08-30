package com.studentleague.matches.repository;

import com.studentleague.matches.entity.MatchLineupPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchLineupPlayerRepository extends JpaRepository<MatchLineupPlayer, UUID> {
    List<MatchLineupPlayer> findByMatchId(UUID matchId);

    List<MatchLineupPlayer> findByMatchIdAndTeamId(UUID matchId, UUID teamId);

    List<MatchLineupPlayer> findByPlayerId(UUID playerId);

    boolean existsByMatchIdAndTeamId(UUID matchId, UUID teamId);

    void deleteByMatchIdAndTeamId(UUID matchId, UUID teamId);
}
