package com.studentleague.home;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.tournaments.domain.CompetitionStage;
import com.studentleague.tournaments.domain.TournamentFormat;
import com.studentleague.tournaments.domain.TournamentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * Short labels and round numbers for the home overview.
 * A round is one Europe/Moscow calendar day of non-playoff matches.
 * Playoff rounds follow the bracket number already stored on the match.
 */
public final class HomeLabels {

    public static final int SHORT_NAME_MAX = 16;
    public static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private HomeLabels() {
    }

    public static String shortName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String trimmed = name.trim().replaceAll("\\s+", " ");
        String withoutYear = trimmed.replaceFirst("\\s+\\d{4}(?:/\\d{2})?$", "");
        String withoutTail = withoutYear.replaceFirst("(?i)\\s+по\\s+.+$", "");
        String base = withoutTail.isBlank() ? withoutYear : withoutTail;
        if (base.length() <= SHORT_NAME_MAX) {
            return base;
        }
        String[] words = base.split(" ");
        if (words.length >= 2) {
            String two = words[0] + " " + words[1];
            if (two.length() <= SHORT_NAME_MAX) {
                return two;
            }
        }
        String first = words[0];
        return first.length() <= SHORT_NAME_MAX ? first : first.substring(0, SHORT_NAME_MAX);
    }

    public static String formatTitle(String code) {
        return TournamentFormat.fromCode(code).map(TournamentFormat::title).orElse(code == null ? "" : code);
    }

    public static String seasonLabel(Integer seasonYear) {
        return seasonYear == null ? null : "Сезон " + seasonYear;
    }

    public static LocalDate moscowDay(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(MOSCOW).toLocalDate();
    }

    public static String roundTitle(int round, int rounds) {
        int fromFinal = Math.max(0, rounds) - round;
        return switch (fromFinal) {
            case 0 -> "Финал";
            case 1 -> "1/2 финала";
            case 2 -> "1/4 финала";
            case 3 -> "1/8 финала";
            case 4 -> "1/16 финала";
            default -> "Раунд " + round;
        };
    }

    public static RoundPlan plan(List<Match> matches, CompetitionStage stage) {
        if (stage == CompetitionStage.PLAYOFF) {
            return playoffPlan(matches);
        }
        return groupPlan(matches);
    }

    public static String tournamentStageLabel(TournamentStatus status, String format, RoundPlan plan) {
        if (status == TournamentStatus.REGISTRATION) {
            return "Регистрация";
        }
        if (plan != null && plan.playoff()) {
            if (plan.roundTitle() == null) {
                return "Плей-офф";
            }
            return "Плей-офф · " + plan.roundTitle();
        }
        boolean groups = TournamentFormat.fromCode(format).orElse(null) == TournamentFormat.GROUPS_PLAYOFF;
        if (plan == null || plan.currentRound() == null || plan.roundsTotal() == null) {
            return groups ? "Групповой этап" : formatTitle(format);
        }
        String round = "тур " + plan.currentRound() + " из " + plan.roundsTotal();
        if (groups) {
            return "Групповой этап · " + round;
        }
        return "Тур " + plan.currentRound() + " из " + plan.roundsTotal();
    }

    public static String matchStageLabel(Match match, List<Match> tournamentMatches) {
        if (match == null) {
            return null;
        }
        if (match.getBracketRound() != null) {
            int total = tournamentMatches.stream()
                    .filter(row -> row.getBracketRound() != null)
                    .mapToInt(Match::getBracketRound)
                    .max()
                    .orElse(match.getBracketRound());
            return roundTitle(match.getBracketRound(), total);
        }
        Integer round = groupRoundOf(match, tournamentMatches);
        return round == null ? null : "Тур " + round;
    }

    public static Integer groupRoundOf(Match match, List<Match> tournamentMatches) {
        if (match.getScheduledAt() == null) {
            return null;
        }
        LocalDate day = moscowDay(match.getScheduledAt());
        List<LocalDate> days = groupDays(tournamentMatches);
        int index = days.indexOf(day);
        return index < 0 ? null : index + 1;
    }

    public static Double progress(List<Match> matches, TournamentStatus status, boolean playoff) {
        if (status == TournamentStatus.REGISTRATION) {
            return null;
        }
        List<Match> counted = matches.stream()
                .filter(match -> match.getStatus() != MatchStatus.CANCELLED)
                .filter(match -> !playoff || match.getBracketRound() != null)
                .toList();
        if (playoff && counted.isEmpty()) {
            counted = matches.stream().filter(match -> match.getStatus() != MatchStatus.CANCELLED).toList();
        }
        if (counted.isEmpty()) {
            return null;
        }
        long finished = counted.stream().filter(match -> match.getStatus() == MatchStatus.FINISHED).count();
        return (double) finished / counted.size();
    }

    private static RoundPlan groupPlan(List<Match> matches) {
        List<LocalDate> days = groupDays(matches);
        if (days.isEmpty()) {
            return RoundPlan.group(null, null);
        }
        int current = days.size();
        for (int i = 0; i < days.size(); i++) {
            LocalDate day = days.get(i);
            boolean open = matches.stream()
                    .filter(match -> match.getBracketRound() == null)
                    .filter(match -> match.getStatus() != MatchStatus.CANCELLED)
                    .filter(match -> day.equals(moscowDay(match.getScheduledAt())))
                    .anyMatch(match -> match.getStatus() != MatchStatus.FINISHED);
            if (open) {
                current = i + 1;
                break;
            }
        }
        return RoundPlan.group(current, days.size());
    }

    private static RoundPlan playoffPlan(List<Match> matches) {
        List<Match> bracket = matches.stream()
                .filter(match -> match.getBracketRound() != null && match.getStatus() != MatchStatus.CANCELLED)
                .toList();
        if (bracket.isEmpty()) {
            return RoundPlan.playoff(null, null, null, null, null);
        }
        int total = bracket.stream().mapToInt(Match::getBracketRound).max().orElse(1);
        int current = bracket.stream()
                .filter(match -> match.getStatus() != MatchStatus.FINISHED)
                .mapToInt(Match::getBracketRound)
                .min()
                .orElse(total);
        int played = (int) bracket.stream()
                .filter(match -> match.getBracketRound() == current && match.getStatus() == MatchStatus.FINISHED)
                .count();
        int pairs = (int) bracket.stream().filter(match -> match.getBracketRound() == current).count();
        return RoundPlan.playoff(current, total, roundTitle(current, total), played, pairs);
    }

    private static List<LocalDate> groupDays(List<Match> matches) {
        TreeMap<LocalDate, Boolean> days = new TreeMap<>();
        for (Match match : matches) {
            if (match.getBracketRound() != null || match.getStatus() == MatchStatus.CANCELLED || match.getScheduledAt() == null) {
                continue;
            }
            days.put(moscowDay(match.getScheduledAt()), Boolean.TRUE);
        }
        return new ArrayList<>(days.keySet());
    }

    public static Instant recency(Match match) {
        if (match.getFinishedAt() != null) {
            return match.getFinishedAt();
        }
        if (match.getStartedAt() != null) {
            return match.getStartedAt();
        }
        return match.getScheduledAt();
    }

    /**
     * Latest finished match of each tournament first, then the next latest, and so on.
     * A league with one tournament still gets up to {@code perTournament} of its own results.
     */
    public static List<Match> diversifiedRecent(List<Match> finishedNewestFirst, int perTournament, int limit) {
        java.util.LinkedHashMap<java.util.UUID, List<Match>> byTournament = new java.util.LinkedHashMap<>();
        for (Match match : finishedNewestFirst) {
            if (match.getTournamentId() == null) {
                continue;
            }
            List<Match> bucket = byTournament.computeIfAbsent(match.getTournamentId(), id -> new ArrayList<>());
            if (bucket.size() < perTournament) {
                bucket.add(match);
            }
        }
        List<java.util.UUID> order = new ArrayList<>(byTournament.keySet());
        order.sort(Comparator.comparing((java.util.UUID id) -> recency(byTournament.get(id).getFirst())).reversed());
        List<Match> out = new ArrayList<>();
        for (int wave = 0; wave < perTournament && out.size() < limit; wave++) {
            List<Match> slice = new ArrayList<>();
            for (java.util.UUID id : order) {
                List<Match> bucket = byTournament.get(id);
                if (bucket.size() > wave) {
                    slice.add(bucket.get(wave));
                }
            }
            slice.sort(Comparator.comparing(HomeLabels::recency).reversed());
            for (Match match : slice) {
                if (out.size() >= limit) {
                    break;
                }
                out.add(match);
            }
        }
        return out;
    }

    public record RoundPlan(
            Integer currentRound,
            Integer roundsTotal,
            boolean playoff,
            String roundTitle,
            Integer playedPairs,
            Integer totalPairs
    ) {
        static RoundPlan group(Integer currentRound, Integer roundsTotal) {
            return new RoundPlan(currentRound, roundsTotal, false, null, null, null);
        }

        static RoundPlan playoff(
                Integer currentRound,
                Integer roundsTotal,
                String roundTitle,
                Integer playedPairs,
                Integer totalPairs
        ) {
            return new RoundPlan(currentRound, roundsTotal, true, roundTitle, playedPairs, totalPairs);
        }
    }
}
