package com.studentleague.players.repository;

import com.studentleague.players.entity.PlayerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, UUID>, JpaSpecificationExecutor<PlayerProfile> {
    Optional<PlayerProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}
