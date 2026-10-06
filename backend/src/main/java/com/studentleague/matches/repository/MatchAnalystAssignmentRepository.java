package com.studentleague.matches.repository;

import com.studentleague.matches.entity.MatchAnalystAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MatchAnalystAssignmentRepository extends JpaRepository<MatchAnalystAssignment, UUID> {

    @Query("""
            select a from MatchAnalystAssignment a
            where a.bothUserId = :userId
               or a.homeUserId = :userId
               or a.awayUserId = :userId
            """)
    List<MatchAnalystAssignment> findByUserId(@Param("userId") UUID userId);

    void deleteByMatchId(UUID matchId);
}
