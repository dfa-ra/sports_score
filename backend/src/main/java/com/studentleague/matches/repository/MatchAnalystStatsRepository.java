package com.studentleague.matches.repository;

import com.studentleague.matches.entity.MatchAnalystStats;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MatchAnalystStatsRepository extends JpaRepository<MatchAnalystStats, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MatchAnalystStats s where s.matchId = :matchId")
    Optional<MatchAnalystStats> lockByMatchId(@Param("matchId") UUID matchId);

    void deleteByMatchId(UUID matchId);
}
