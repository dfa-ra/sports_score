package com.studentleague.matches.repository;

import com.studentleague.matches.entity.MatchEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchEventRepository extends JpaRepository<MatchEvent, UUID> {
    List<MatchEvent> findByMatchIdOrderByTimestampAsc(UUID matchId);
    List<MatchEvent> findByMatchIdAndVoidedFalseOrderByTimestampAsc(UUID matchId);
}
