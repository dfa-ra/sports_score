package com.studentleague.tournaments.qualify;

import com.studentleague.tournaments.domain.QualifyStage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure check for variant B: one place, one stage, and each playoff round
 * is direct entrants plus winners of the previous round, a power of two
 * in {2, 4, 8, 16}.
 */
public final class PlayoffQualifyMath {

    public static final String PLACE_OUT_OF_RANGE = "PLACE_OUT_OF_RANGE";
    public static final String NOT_POWER_OF_TWO = "NOT_POWER_OF_TWO";
    public static final String LOCKED = "LOCKED";

    private static final QualifyStage[] PIPE = {QualifyStage.R16, QualifyStage.R8, QualifyStage.QF, QualifyStage.SF};
    private static final int[] ALLOWED = {2, 4, 8, 16};

    private PlayoffQualifyMath() {
    }

    public record GroupSize(UUID id, String name, int size) {
    }

    public record RuleSpan(UUID groupId, int placeFrom, int placeTo, QualifyStage stage) {
    }

    public record StageLine(QualifyStage stage, int direct, int fromPrev, int total, boolean powerOfTwo) {
    }

    public record Failure(String code, String message) {
    }

    public record Report(List<StageLine> stages, Failure failure, Map<UUID, Map<Integer, QualifyStage>> byPlace) {
    }

    public static Report evaluate(List<GroupSize> groups, List<RuleSpan> rules) {
        Map<UUID, GroupSize> byId = new LinkedHashMap<>();
        for (GroupSize group : groups) {
            byId.put(group.id(), group);
        }
        Map<UUID, Map<Integer, QualifyStage>> assigned = new LinkedHashMap<>();
        for (RuleSpan rule : rules) {
            if (rule.stage() == null || rule.stage() == QualifyStage.F) {
                return fail(PLACE_OUT_OF_RANGE, "Стадия места указана неверно", assigned);
            }
            GroupSize group = byId.get(rule.groupId());
            if (group == null) {
                return fail(PLACE_OUT_OF_RANGE, "Группа не найдена в этом турнире", assigned);
            }
            if (rule.placeFrom() < 1 || rule.placeTo() < rule.placeFrom() || rule.placeTo() > group.size()) {
                return fail(
                        PLACE_OUT_OF_RANGE,
                        "Место " + Math.max(rule.placeTo(), rule.placeFrom())
                                + " в группе «" + group.name() + "» больше числа команд (" + group.size() + ")"
                , assigned);
            }
            Map<Integer, QualifyStage> places = assigned.computeIfAbsent(group.id(), ignored -> new LinkedHashMap<>());
            for (int place = rule.placeFrom(); place <= rule.placeTo(); place++) {
                if (places.containsKey(place)) {
                    return fail(
                            PLACE_OUT_OF_RANGE,
                            "Место " + place + " в группе «" + group.name() + "» указано дважды",
                            assigned
                    );
                }
                places.put(place, rule.stage());
            }
        }
        EnumMap<QualifyStage, Integer> direct = new EnumMap<>(QualifyStage.class);
        for (Map<Integer, QualifyStage> places : assigned.values()) {
            for (QualifyStage stage : places.values()) {
                if (stage.playoffEntry()) {
                    direct.merge(stage, 1, Integer::sum);
                }
            }
        }
        List<StageLine> lines = lines(direct);
        if (lines.isEmpty()) {
            return new Report(lines, new Failure(NOT_POWER_OF_TWO, "В плей-офф не выходит ни одна команда"), assigned);
        }
        Failure failure = null;
        for (int index = 0; index < lines.size(); index++) {
            StageLine line = lines.get(index);
            if (!line.powerOfTwo()) {
                QualifyStage previous = index == 0 ? null : lines.get(index - 1).stage();
                failure = new Failure(NOT_POWER_OF_TWO, powerMessage(line, previous));
                break;
            }
        }
        return new Report(lines, failure, assigned);
    }

    public static List<RuleSpan> collapse(List<RuleSpan> rules) {
        Map<UUID, List<RuleSpan>> byGroup = new LinkedHashMap<>();
        for (RuleSpan rule : rules) {
            byGroup.computeIfAbsent(rule.groupId(), ignored -> new ArrayList<>()).add(rule);
        }
        List<RuleSpan> collapsed = new ArrayList<>();
        for (List<RuleSpan> spans : byGroup.values()) {
            List<int[]> places = new ArrayList<>();
            Map<Integer, QualifyStage> stageOf = new LinkedHashMap<>();
            UUID groupId = spans.getFirst().groupId();
            for (RuleSpan span : spans) {
                for (int place = span.placeFrom(); place <= span.placeTo(); place++) {
                    places.add(new int[] {place});
                    stageOf.put(place, span.stage());
                }
            }
            places.sort(Comparator.comparingInt(row -> row[0]));
            Integer from = null;
            Integer to = null;
            QualifyStage stage = null;
            for (int[] row : places) {
                int place = row[0];
                QualifyStage next = stageOf.get(place);
                if (from == null) {
                    from = place;
                    to = place;
                    stage = next;
                    continue;
                }
                if (next == stage && place == to + 1) {
                    to = place;
                    continue;
                }
                collapsed.add(new RuleSpan(groupId, from, to, stage));
                from = place;
                to = place;
                stage = next;
            }
            if (from != null) {
                collapsed.add(new RuleSpan(groupId, from, to, stage));
            }
        }
        return collapsed;
    }

    static List<StageLine> lines(Map<QualifyStage, Integer> directCounts) {
        List<StageLine> rows = new ArrayList<>();
        int incoming = 0;
        boolean started = false;
        boolean chainOk = true;
        boolean closed = false;
        for (QualifyStage stage : PIPE) {
            int direct = directCounts.getOrDefault(stage, 0);
            if (!started && direct == 0 && incoming == 0) {
                continue;
            }
            started = true;
            int total = direct + incoming;
            boolean sizeOk = allowed(total);
            boolean ok = chainOk && sizeOk;
            rows.add(new StageLine(stage, direct, incoming, total, ok));
            if (!ok) {
                chainOk = false;
            }
            // Two teams is the final match. A further round would have one team.
            if (ok && total == 2) {
                closed = true;
                break;
            }
            incoming = total / 2;
        }
        if (started && !closed) {
            int total = incoming;
            boolean ok = chainOk && allowed(total);
            rows.add(new StageLine(QualifyStage.F, 0, incoming, total, ok));
        }
        return rows;
    }

    public static boolean allowed(int total) {
        for (int size : ALLOWED) {
            if (size == total) {
                return true;
            }
        }
        return false;
    }

    public static int suggested(int total) {
        for (int size : ALLOWED) {
            if (size >= total) {
                return size;
            }
        }
        return ALLOWED[ALLOWED.length - 1];
    }

    public static String powerMessage(StageLine line, QualifyStage previous) {
        int expect = suggested(line.total());
        String prev = previous == null ? "прошлого круга" : previous.label();
        return "В " + line.stage().label() + " должно быть " + expect + " " + teamsWord(expect)
                + ": " + line.direct() + " напрямую + " + line.fromPrev() + " " + winnersWord(line.fromPrev())
                + " " + prev;
    }

    public static String teamsWord(int count) {
        int tail = Math.abs(count) % 100;
        int digit = tail % 10;
        if (tail > 10 && tail < 20) {
            return "команд";
        }
        if (digit == 1) {
            return "команда";
        }
        if (digit >= 2 && digit <= 4) {
            return "команды";
        }
        return "команд";
    }

    public static String winnersWord(int count) {
        int tail = Math.abs(count) % 100;
        int digit = tail % 10;
        if (tail > 10 && tail < 20) {
            return "победителей";
        }
        if (digit == 1) {
            return "победитель";
        }
        if (digit >= 2 && digit <= 4) {
            return "победителя";
        }
        return "победителей";
    }

    private static Report fail(String code, String message, Map<UUID, Map<Integer, QualifyStage>> assigned) {
        return new Report(List.of(), new Failure(code, message), assigned);
    }
}
