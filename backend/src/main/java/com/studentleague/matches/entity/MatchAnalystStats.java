package com.studentleague.matches.entity;

import com.studentleague.matches.domain.AnalystStat;
import com.studentleague.matches.domain.PossessionSide;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_analyst_stats")
public class MatchAnalystStats {

    @Id
    @Column(name = "match_id")
    private UUID matchId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "shots", column = @Column(name = "home_shots", nullable = false)),
            @AttributeOverride(name = "shotsOnTarget", column = @Column(name = "home_shots_on_target", nullable = false)),
            @AttributeOverride(name = "saves", column = @Column(name = "home_saves", nullable = false)),
            @AttributeOverride(name = "corners", column = @Column(name = "home_corners", nullable = false)),
            @AttributeOverride(name = "fouls", column = @Column(name = "home_fouls", nullable = false)),
            @AttributeOverride(name = "freeKicks", column = @Column(name = "home_free_kicks", nullable = false)),
            @AttributeOverride(name = "kickIns", column = @Column(name = "home_kick_ins", nullable = false)),
            @AttributeOverride(name = "woodwork", column = @Column(name = "home_woodwork", nullable = false)),
            @AttributeOverride(name = "possessionSeconds", column = @Column(name = "home_possession_seconds", nullable = false))
    })
    private AnalystCounters home = new AnalystCounters();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "shots", column = @Column(name = "away_shots", nullable = false)),
            @AttributeOverride(name = "shotsOnTarget", column = @Column(name = "away_shots_on_target", nullable = false)),
            @AttributeOverride(name = "saves", column = @Column(name = "away_saves", nullable = false)),
            @AttributeOverride(name = "corners", column = @Column(name = "away_corners", nullable = false)),
            @AttributeOverride(name = "fouls", column = @Column(name = "away_fouls", nullable = false)),
            @AttributeOverride(name = "freeKicks", column = @Column(name = "away_free_kicks", nullable = false)),
            @AttributeOverride(name = "kickIns", column = @Column(name = "away_kick_ins", nullable = false)),
            @AttributeOverride(name = "woodwork", column = @Column(name = "away_woodwork", nullable = false)),
            @AttributeOverride(name = "possessionSeconds", column = @Column(name = "away_possession_seconds", nullable = false))
    })
    private AnalystCounters away = new AnalystCounters();

    @Enumerated(EnumType.STRING)
    @Column(name = "possession_side", length = 16)
    private PossessionSide possessionSide;

    @Column(name = "possession_since")
    private Instant possessionSince;

    @Column(name = "possession_tracked", nullable = false)
    private boolean possessionTracked;

    public UUID getMatchId() {
        return matchId;
    }

    public void setMatchId(UUID matchId) {
        this.matchId = matchId;
    }

    public AnalystCounters getHome() {
        if (home == null) {
            home = new AnalystCounters();
        }
        return home;
    }

    public AnalystCounters getAway() {
        if (away == null) {
            away = new AnalystCounters();
        }
        return away;
    }

    public void apply(boolean homeTeam, AnalystStat stat, int delta) {
        (homeTeam ? getHome() : getAway()).apply(stat, delta);
    }

    public PossessionSide getPossessionSide() {
        return possessionSide;
    }

    public void setPossessionSide(PossessionSide possessionSide) {
        this.possessionSide = possessionSide;
    }

    public Instant getPossessionSince() {
        return possessionSince;
    }

    public void setPossessionSince(Instant possessionSince) {
        this.possessionSince = possessionSince;
    }

    public boolean isPossessionTracked() {
        return possessionTracked;
    }

    public void setPossessionTracked(boolean possessionTracked) {
        this.possessionTracked = possessionTracked;
    }
}
