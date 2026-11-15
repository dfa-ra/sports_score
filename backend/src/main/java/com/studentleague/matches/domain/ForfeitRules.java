package com.studentleague.matches.domain;

import com.studentleague.matches.entity.Match;

public final class ForfeitRules {

    public static final int SCORE = 5;

    private ForfeitRules() {
    }

    public static boolean active(Match match) {
        return match != null && match.getForfeit() != null && match.getForfeit() != ForfeitSide.NONE;
    }

    public static boolean mutual(Match match) {
        return match != null && match.getForfeit() == ForfeitSide.BOTH;
    }

    public static boolean playoff(Match match) {
        return match != null && match.getBracketRound() != null;
    }

    /** A loss that carries the «ТП» mark: the side that was forfeited. */
    public static boolean technicalLoss(Match match, boolean homeSide) {
        if (match == null || match.getForfeit() == null) {
            return false;
        }
        return switch (match.getForfeit()) {
            case BOTH -> true;
            case HOME -> homeSide;
            case AWAY -> !homeSide;
            case NONE -> false;
        };
    }
}
