package com.studentleague.matches.repository;

import com.studentleague.matches.entity.PenaltyKick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PenaltyKickRepository extends JpaRepository<PenaltyKick, UUID> {

    List<PenaltyKick> findByMatchIdOrderByKickOrderAsc(UUID matchId);

    List<PenaltyKick> findByMatchIdInOrderByKickOrderAsc(Collection<UUID> matchIds);
}
