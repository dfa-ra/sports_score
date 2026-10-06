package com.studentleague.matches.entity;

import com.studentleague.matches.domain.AnalystAssignmentMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "match_analyst_assignments")
public class MatchAnalystAssignment {

    @Id
    @Column(name = "match_id")
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AnalystAssignmentMode mode;

    @Column(name = "both_user_id")
    private UUID bothUserId;

    @Column(name = "home_user_id")
    private UUID homeUserId;

    @Column(name = "away_user_id")
    private UUID awayUserId;

    public UUID getMatchId() {
        return matchId;
    }

    public void setMatchId(UUID matchId) {
        this.matchId = matchId;
    }

    public AnalystAssignmentMode getMode() {
        return mode;
    }

    public void setMode(AnalystAssignmentMode mode) {
        this.mode = mode;
    }

    public UUID getBothUserId() {
        return bothUserId;
    }

    public void setBothUserId(UUID bothUserId) {
        this.bothUserId = bothUserId;
    }

    public UUID getHomeUserId() {
        return homeUserId;
    }

    public void setHomeUserId(UUID homeUserId) {
        this.homeUserId = homeUserId;
    }

    public UUID getAwayUserId() {
        return awayUserId;
    }

    public void setAwayUserId(UUID awayUserId) {
        this.awayUserId = awayUserId;
    }
}
