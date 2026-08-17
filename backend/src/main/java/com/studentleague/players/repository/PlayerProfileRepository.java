package com.studentleague.players.repository;

import com.studentleague.players.entity.PlayerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, UUID> {
    Optional<PlayerProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    Page<PlayerProfile> findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            String lastName, String firstName, String displayName, Pageable pageable
    );
}
