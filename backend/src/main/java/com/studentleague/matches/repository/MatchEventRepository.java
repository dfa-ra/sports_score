package com.studentleague.matches.repository;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.entity.MatchEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchEventRepository extends JpaRepository<MatchEvent, UUID> {
    List<MatchEvent> findByMatchIdOrderByTimestampAsc(UUID matchId);
    Optional<MatchEvent> findFirstByMatchIdOrderByTimestampDesc(UUID matchId);
    void deleteByMatchId(UUID matchId);
    boolean existsByMatchIdIn(Collection<UUID> matchIds);
    boolean existsByTeamId(UUID teamId);
    List<MatchEvent> findByMatchIdAndVoidedFalseOrderByTimestampAsc(UUID matchId);
    List<MatchEvent> findByMatchIdInAndVoidedFalse(java.util.Collection<UUID> matchIds);

    @Query("""
            select e from MatchEvent e
            where e.voided = false
              and e.eventType = :eventType
              and e.matchId in :matchIds
            order by e.timestamp desc, e.createdAt desc, e.id desc
            """)
    List<MatchEvent> findActiveByMatchIdsAndType(
            @Param("matchIds") Collection<UUID> matchIds,
            @Param("eventType") MatchEventType eventType
    );
    List<MatchEvent> findByPlayerIdAndVoidedFalseOrderByTimestampDesc(UUID playerId);

    @Query("""
            select e from MatchEvent e
            where e.voided = false
              and (e.playerId = :playerId or e.secondaryPlayerId = :playerId)
            order by e.timestamp desc
            """)
    List<MatchEvent> findActiveInvolvingPlayer(@Param("playerId") UUID playerId);
}
