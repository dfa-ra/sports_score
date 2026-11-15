package com.studentleague.matches.entity;

import com.studentleague.matches.domain.ForfeitReason;
import com.studentleague.matches.domain.ForfeitReasonConverter;
import com.studentleague.matches.domain.ForfeitSide;
import com.studentleague.matches.domain.ForfeitSideConverter;
import com.studentleague.matches.domain.MatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    private UUID id;

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "sport_id", nullable = false)
    private UUID sportId;

    @Column(name = "home_team_id", nullable = false)
    private UUID homeTeamId;

    @Column(name = "away_team_id", nullable = false)
    private UUID awayTeamId;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MatchStatus status;

    @Column(name = "home_score", nullable = false)
    private int homeScore;

    @Column(name = "away_score", nullable = false)
    private int awayScore;

    @Column(name = "game_time_seconds")
    private Integer gameTimeSeconds;

    @Column
    private Integer period;

    @Column(name = "period_count", nullable = false)
    private int periodCount = 2;

    @Column(name = "period_length_seconds", nullable = false)
    private int periodLengthSeconds = 15 * 60;

    @Column(name = "clock_running_since")
    private Instant clockRunningSince;

    @Column(length = 120)
    private String venue;

    @Column(name = "player_of_the_match_id")
    private UUID playerOfTheMatchId;

    @Column(name = "home_goalkeeper_id")
    private UUID homeGoalkeeperId;

    @Column(name = "away_goalkeeper_id")
    private UUID awayGoalkeeperId;

    @Column(name = "table_id")
    private UUID tableId;

    @Column(name = "bracket_round")
    private Integer bracketRound;

    /** Calendar tour within a group. Separate from {@code bracketRound}. */
    @Column(name = "round_number")
    private Integer roundNumber;

    @Convert(converter = ForfeitSideConverter.class)
    @Column(nullable = false, length = 16)
    private ForfeitSide forfeit = ForfeitSide.NONE;

    @Convert(converter = ForfeitReasonConverter.class)
    @Column(name = "forfeit_reason", length = 32)
    private ForfeitReason forfeitReason;

    @Column(name = "forfeit_reason_note", length = 500)
    private String forfeitReasonNote;

    @Column(name = "forfeit_set_by")
    private UUID forfeitSetBy;

    @Column(name = "forfeit_set_at")
    private Instant forfeitSetAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "pre_forfeit_status", length = 32)
    private MatchStatus preForfeitStatus;

    @Column(name = "pre_forfeit_home_score")
    private Integer preForfeitHomeScore;

    @Column(name = "pre_forfeit_away_score")
    private Integer preForfeitAwayScore;

    @Column(name = "pre_forfeit_finished_at")
    private Instant preForfeitFinishedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
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

    public UUID getSportId() {
        return sportId;
    }

    public void setSportId(UUID sportId) {
        this.sportId = sportId;
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

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public void setStatus(MatchStatus status) {
        this.status = status;
    }

    public int getHomeScore() {
        return homeScore;
    }

    public void setHomeScore(int homeScore) {
        this.homeScore = homeScore;
    }

    public int getAwayScore() {
        return awayScore;
    }

    public void setAwayScore(int awayScore) {
        this.awayScore = awayScore;
    }

    public Integer getGameTimeSeconds() {
        return gameTimeSeconds;
    }

    public void setGameTimeSeconds(Integer gameTimeSeconds) {
        this.gameTimeSeconds = gameTimeSeconds;
    }

    public Integer getPeriod() {
        return period;
    }

    public void setPeriod(Integer period) {
        this.period = period;
    }

    public int getPeriodCount() {
        return periodCount;
    }

    public void setPeriodCount(int periodCount) {
        this.periodCount = periodCount;
    }

    public int getPeriodLengthSeconds() {
        return periodLengthSeconds;
    }

    public void setPeriodLengthSeconds(int periodLengthSeconds) {
        this.periodLengthSeconds = periodLengthSeconds;
    }

    public Instant getClockRunningSince() {
        return clockRunningSince;
    }

    public void setClockRunningSince(Instant clockRunningSince) {
        this.clockRunningSince = clockRunningSince;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public UUID getPlayerOfTheMatchId() {
        return playerOfTheMatchId;
    }

    public void setPlayerOfTheMatchId(UUID playerOfTheMatchId) {
        this.playerOfTheMatchId = playerOfTheMatchId;
    }

    public UUID getHomeGoalkeeperId() {
        return homeGoalkeeperId;
    }

    public void setHomeGoalkeeperId(UUID homeGoalkeeperId) {
        this.homeGoalkeeperId = homeGoalkeeperId;
    }

    public UUID getAwayGoalkeeperId() {
        return awayGoalkeeperId;
    }

    public void setAwayGoalkeeperId(UUID awayGoalkeeperId) {
        this.awayGoalkeeperId = awayGoalkeeperId;
    }

    public UUID getTableId() {
        return tableId;
    }

    public void setTableId(UUID tableId) {
        this.tableId = tableId;
    }

    public Integer getBracketRound() {
        return bracketRound;
    }

    public void setBracketRound(Integer bracketRound) {
        this.bracketRound = bracketRound;
    }

    public Integer getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(Integer roundNumber) {
        this.roundNumber = roundNumber;
    }

    public ForfeitSide getForfeit() {
        return forfeit == null ? ForfeitSide.NONE : forfeit;
    }

    public void setForfeit(ForfeitSide forfeit) {
        this.forfeit = forfeit == null ? ForfeitSide.NONE : forfeit;
    }

    public ForfeitReason getForfeitReason() {
        return forfeitReason;
    }

    public void setForfeitReason(ForfeitReason forfeitReason) {
        this.forfeitReason = forfeitReason;
    }

    public String getForfeitReasonNote() {
        return forfeitReasonNote;
    }

    public void setForfeitReasonNote(String forfeitReasonNote) {
        this.forfeitReasonNote = forfeitReasonNote;
    }

    public UUID getForfeitSetBy() {
        return forfeitSetBy;
    }

    public void setForfeitSetBy(UUID forfeitSetBy) {
        this.forfeitSetBy = forfeitSetBy;
    }

    public Instant getForfeitSetAt() {
        return forfeitSetAt;
    }

    public void setForfeitSetAt(Instant forfeitSetAt) {
        this.forfeitSetAt = forfeitSetAt;
    }

    public MatchStatus getPreForfeitStatus() {
        return preForfeitStatus;
    }

    public void setPreForfeitStatus(MatchStatus preForfeitStatus) {
        this.preForfeitStatus = preForfeitStatus;
    }

    public Integer getPreForfeitHomeScore() {
        return preForfeitHomeScore;
    }

    public void setPreForfeitHomeScore(Integer preForfeitHomeScore) {
        this.preForfeitHomeScore = preForfeitHomeScore;
    }

    public Integer getPreForfeitAwayScore() {
        return preForfeitAwayScore;
    }

    public void setPreForfeitAwayScore(Integer preForfeitAwayScore) {
        this.preForfeitAwayScore = preForfeitAwayScore;
    }

    public Instant getPreForfeitFinishedAt() {
        return preForfeitFinishedAt;
    }

    public void setPreForfeitFinishedAt(Instant preForfeitFinishedAt) {
        this.preForfeitFinishedAt = preForfeitFinishedAt;
    }
}
