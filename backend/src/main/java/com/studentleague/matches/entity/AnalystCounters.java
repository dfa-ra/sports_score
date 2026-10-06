package com.studentleague.matches.entity;

import com.studentleague.matches.domain.AnalystStat;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class AnalystCounters {

    @Column(nullable = false)
    private int shots;

    @Column(nullable = false)
    private int shotsOnTarget;

    @Column(nullable = false)
    private int saves;

    @Column(nullable = false)
    private int corners;

    @Column(nullable = false)
    private int fouls;

    @Column(nullable = false)
    private int freeKicks;

    @Column(nullable = false)
    private int kickIns;

    @Column(nullable = false)
    private int woodwork;

    @Column(nullable = false)
    private int possessionSeconds;

    public void apply(AnalystStat stat, int delta) {
        switch (stat) {
            case SHOT_OFF -> {
                if (delta > 0) {
                    shots += 1;
                } else if (shots > shotsOnTarget) {
                    shots -= 1;
                }
            }
            case SHOT_ON_TARGET -> {
                if (delta > 0) {
                    shots += 1;
                    shotsOnTarget += 1;
                } else if (shotsOnTarget > 0) {
                    shotsOnTarget -= 1;
                    if (shots > 0) {
                        shots -= 1;
                    }
                }
            }
            case SAVE -> saves = shift(saves, delta);
            case CORNER -> corners = shift(corners, delta);
            case FOUL -> fouls = shift(fouls, delta);
            case FREE_KICK -> freeKicks = shift(freeKicks, delta);
            case KICK_IN -> kickIns = shift(kickIns, delta);
            case WOODWORK -> woodwork = shift(woodwork, delta);
        }
    }

    public void addPossessionSeconds(int seconds) {
        if (seconds > 0) {
            possessionSeconds += seconds;
        }
    }

    private static int shift(int value, int delta) {
        return Math.max(0, value + delta);
    }

    public int getShots() {
        return shots;
    }

    public int getShotsOnTarget() {
        return shotsOnTarget;
    }

    public int getSaves() {
        return saves;
    }

    public int getCorners() {
        return corners;
    }

    public int getFouls() {
        return fouls;
    }

    public int getFreeKicks() {
        return freeKicks;
    }

    public int getKickIns() {
        return kickIns;
    }

    public int getWoodwork() {
        return woodwork;
    }

    public int getPossessionSeconds() {
        return possessionSeconds;
    }

    public void setPossessionSeconds(int possessionSeconds) {
        this.possessionSeconds = Math.max(0, possessionSeconds);
    }
}
