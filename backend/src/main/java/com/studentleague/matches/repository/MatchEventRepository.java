package com.studentleague.matches.repository;

import com.studentleague.matches.entity.MatchEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MatchEventRepository extends JpaRepository<MatchEvent, UUID> {
    List<MatchEvent> findByMatchIdOrderByTimestampAsc(UUID matchId);
    List<MatchEvent> findByMatchIdAndVoidedFalseOrderByTimestampAsc(UUID matchId);
    List<MatchEvent> findByPlayerIdAndVoidedFalseOrderByTimestampDesc(UUID playerId);

    @Query("""
            select e from MatchEvent e
            where e.voided = false
              and (e.playerId = :playerId or e.secondaryPlayerId = :playerId)
            order by e.timestamp desc
            """)
    List<MatchEvent> findActiveInvolvingPlayer(@Param("playerId") UUID playerId);
}
