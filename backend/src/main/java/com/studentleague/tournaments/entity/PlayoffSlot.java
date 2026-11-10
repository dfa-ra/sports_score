package com.studentleague.tournaments.entity;

import com.studentleague.tournaments.domain.PlayoffSlotKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "playoff_slots")
public class PlayoffSlot {

    @Id
    private UUID id;

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlayoffSlotKind kind = PlayoffSlotKind.MAIN;

    @Column(name = "match_id")
    private UUID matchId;

    @Column(name = "home_team_id")
    private UUID homeTeamId;

    @Column(name = "away_team_id")
    private UUID awayTeamId;

    @Column(name = "home_group_name", length = 200)
    private String homeGroupName;

    @Column(name = "home_place")
    private Integer homePlace;

    @Column(name = "away_group_name", length = 200)
    private String awayGroupName;

    @Column(name = "away_place")
    private Integer awayPlace;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(length = 120)
    private String venue;

    @Column(nullable = false)
    private boolean bye;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (kind == null) {
            kind = PlayoffSlotKind.MAIN;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(UUID tournamentId) {
        this.tournamentId = tournamentId;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public PlayoffSlotKind getKind() {
        return kind;
    }

    public void setKind(PlayoffSlotKind kind) {
        this.kind = kind;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public void setMatchId(UUID matchId) {
        this.matchId = matchId;
    }

    public UUID getHomeTeamId() {
        return homeTeamId;
    }

    public void setHomeTeamId(UUID homeTeamId) {
        this.homeTeamId = homeTeamId;
    }

    public UUID getAwayTeamId() {
        return awayTeamId;
    }

    public void setAwayTeamId(UUID awayTeamId) {
        this.awayTeamId = awayTeamId;
    }

    public String getHomeGroupName() {
        return homeGroupName;
    }

    public void setHomeGroupName(String homeGroupName) {
        this.homeGroupName = homeGroupName;
    }

    public Integer getHomePlace() {
        return homePlace;
    }

    public void setHomePlace(Integer homePlace) {
        this.homePlace = homePlace;
    }

    public String getAwayGroupName() {
        return awayGroupName;
    }

    public void setAwayGroupName(String awayGroupName) {
        this.awayGroupName = awayGroupName;
    }

    public Integer getAwayPlace() {
        return awayPlace;
    }

    public void setAwayPlace(Integer awayPlace) {
        this.awayPlace = awayPlace;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public boolean isBye() {
        return bye;
    }

    public void setBye(boolean bye) {
        this.bye = bye;
    }
}
