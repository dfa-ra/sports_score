package com.studentleague.tournaments.pairing;

import com.studentleague.tournaments.domain.QualifyStage;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.StageLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turns saved place rules into bracket slots.
 * Replace this class if pairs must be drawn entirely by hand.
 * The rules screen does not depend on it.
 */
public final class PlayoffPairing {

    private PlayoffPairing() {
    }

    public record Entrant(
            UUID groupId,
            String groupName,
            int place,
            int groupOrder,
            QualifyStage stage,
            UUID teamId
    ) {
    }

    public record Side(UUID groupId, String groupName, int place, UUID teamId) {
        static Side of(Entrant entrant) {
            return new Side(entrant.groupId(), entrant.groupName(), entrant.place(), entrant.teamId());
        }
    }

    /** One stored slot. {@code pairIndex} counts real matches of that stage, starting at 0. Byes use -1. */
    public record SlotDraft(
            QualifyStage stage,
            int roundNumber,
            int slotIndex,
            int pairIndex,
            boolean bye,
            Side home,
            Side away
    ) {
        SlotDraft withSides(Side home, Side away) {
            return new SlotDraft(stage, roundNumber, slotIndex, pairIndex, bye, home, away);
        }

        SlotDraft at(int roundNumber, int slotIndex, int pairIndex) {
            return new SlotDraft(stage, roundNumber, slotIndex, pairIndex, bye, home, away);
        }
    }

    public record Edit(QualifyStage stage, int pairIndex, UUID homeGroupId, Integer homePlace, UUID awayGroupId, Integer awayPlace) {
    }

    public static List<SlotDraft> arrange(List<StageLine> stages, List<Entrant> entrants) {
        return arrange(stages, entrants, List.of());
    }

    public static List<SlotDraft> arrange(List<StageLine> stages, List<Entrant> entrants, List<Edit> edits) {
        List<StageLine> play = new ArrayList<>();
        for (StageLine line : stages) {
            if (line.powerOfTwo() && line.total() > 0) {
                play.add(line);
            }
        }
        if (play.isEmpty()) {
            return List.of();
        }
        List<List<SlotDraft>> rounds = new ArrayList<>();
        for (int index = 0; index < play.size(); index++) {
            StageLine stage = play.get(index);
            StageLine next = index + 1 < play.size() ? play.get(index + 1) : null;
            int matches = Math.max(1, stage.total() / 2);
            List<Entrant> mine = ofStage(entrants, stage.stage());
            List<Entrant> nextDirects = next == null || next.stage() == QualifyStage.F
                    ? List.of()
                    : ofStage(entrants, next.stage());
            List<SlotDraft> real = stage.fromPrev() == 0
                    ? pairEntrants(mine, stage.stage())
                    : blanks(matches, stage.stage());
            List<SlotDraft> byes = new ArrayList<>();
            for (Entrant entrant : seeded(nextDirects)) {
                byes.add(new SlotDraft(next.stage(), 0, 0, -1, true, Side.of(entrant), null));
            }
            rounds.add(index(interleave(byes, real), index + 1));
        }
        placeByes(rounds);
        List<SlotDraft> flat = new ArrayList<>();
        for (List<SlotDraft> round : rounds) {
            flat.addAll(round);
        }
        if (edits != null && !edits.isEmpty()) {
            flat = applyEdits(flat, edits, entrants);
        }
        return List.copyOf(flat);
    }

    static List<SlotDraft> applyEdits(List<SlotDraft> slots, List<Edit> edits, List<Entrant> entrants) {
        List<SlotDraft> next = new ArrayList<>(slots);
        for (Edit edit : edits) {
            int at = realSlot(next, edit.stage(), edit.pairIndex());
            if (at < 0) {
                throw new PairingRejected("Пара " + edit.stage().label() + " №" + (edit.pairIndex() + 1) + " не найдена");
            }
            SlotDraft slot = next.get(at);
            Side home = resolve(edit.homeGroupId(), edit.homePlace(), entrants, slot.home());
            Side away = resolve(edit.awayGroupId(), edit.awayPlace(), entrants, slot.away());
            if (home == null && away == null) {
                throw new PairingRejected("В паре должна быть хотя бы одна команда");
            }
            next.set(at, slot.withSides(home, away));
        }
        resyncByes(next);
        Set<String> seen = new HashSet<>();
        for (SlotDraft slot : next) {
            if (slot.bye()) {
                note(seen, slot.home());
                continue;
            }
            if (slot.home() != null && !coveredByBye(next, slot.home())) {
                note(seen, slot.home());
            }
            if (slot.away() != null && !coveredByBye(next, slot.away())) {
                note(seen, slot.away());
            }
        }
        if (seen.size() != entrants.size()) {
            throw new PairingRejected("Каждая вышедшая команда ставится в сетку один раз");
        }
        return next;
    }

    private static void note(Set<String> seen, Side side) {
        if (side == null || !seen.add(side.groupId() + ":" + side.place())) {
            throw new PairingRejected("Каждая вышедшая команда ставится в сетку один раз");
        }
    }

    private static boolean coveredByBye(List<SlotDraft> slots, Side side) {
        for (SlotDraft slot : slots) {
            if (slot.bye() && same(slot.home(), side)) {
                return true;
            }
        }
        return false;
    }

    /** A bye carries the same team the next round already shows on that side. */
    private static void resyncByes(List<SlotDraft> slots) {
        for (int index = 0; index < slots.size(); index++) {
            SlotDraft bye = slots.get(index);
            if (!bye.bye()) {
                continue;
            }
            SlotDraft target = null;
            for (SlotDraft slot : slots) {
                if (slot.roundNumber() == bye.roundNumber() + 1 && slot.slotIndex() == bye.slotIndex() / 2) {
                    target = slot;
                    break;
                }
            }
            if (target == null) {
                continue;
            }
            Side placed = bye.slotIndex() % 2 == 0 ? target.home() : target.away();
            slots.set(index, bye.withSides(placed, null));
        }
    }

    private static Side resolve(UUID groupId, Integer place, List<Entrant> entrants, Side keep) {
        if (groupId == null || place == null) {
            return null;
        }
        for (Entrant entrant : entrants) {
            if (entrant.groupId().equals(groupId) && entrant.place() == place) {
                return Side.of(entrant);
            }
        }
        if (keep != null && keep.groupId().equals(groupId) && keep.place() == place) {
            return keep;
        }
        throw new PairingRejected("В сетке только команды с выбранных мест");
    }

    private static int realSlot(List<SlotDraft> slots, QualifyStage stage, int pairIndex) {
        for (int index = 0; index < slots.size(); index++) {
            SlotDraft slot = slots.get(index);
            if (!slot.bye() && slot.stage() == stage && slot.pairIndex() == pairIndex) {
                return index;
            }
        }
        return -1;
    }

    private static void placeByes(List<List<SlotDraft>> rounds) {
        for (int round = 0; round < rounds.size() - 1; round++) {
            List<SlotDraft> feeders = rounds.get(round);
            List<SlotDraft> next = rounds.get(round + 1);
            for (SlotDraft slot : feeders) {
                if (!slot.bye() || slot.home() == null) {
                    continue;
                }
                int dest = slot.slotIndex() / 2;
                if (dest < 0 || dest >= next.size()) {
                    throw new PairingRejected("Не удалось поставить прямой выход в следующую стадию");
                }
                SlotDraft target = next.get(dest);
                boolean homeSide = slot.slotIndex() % 2 == 0;
                next.set(dest, homeSide ? target.withSides(slot.home(), target.away()) : target.withSides(target.home(), slot.home()));
            }
        }
    }

    private static List<SlotDraft> index(List<SlotDraft> slots, int roundNumber) {
        List<SlotDraft> numbered = new ArrayList<>();
        int pair = 0;
        for (int index = 0; index < slots.size(); index++) {
            SlotDraft slot = slots.get(index);
            int pairIndex = slot.bye() ? -1 : pair++;
            numbered.add(slot.at(roundNumber, index, pairIndex));
        }
        return numbered;
    }

    static List<SlotDraft> interleave(List<SlotDraft> byes, List<SlotDraft> real) {
        List<SlotDraft> out = new ArrayList<>();
        int bye = 0;
        int match = 0;
        while (bye < byes.size() || match < real.size()) {
            if (bye < byes.size() && match < real.size()) {
                out.add(byes.get(bye++));
                out.add(real.get(match++));
            } else if (byes.size() - bye >= 2) {
                out.add(byes.get(bye++));
                out.add(byes.get(bye++));
            } else if (real.size() - match >= 2) {
                out.add(real.get(match++));
                out.add(real.get(match++));
            } else if (bye < byes.size()) {
                out.add(byes.get(bye++));
            } else if (match < real.size()) {
                out.add(real.get(match++));
            } else {
                break;
            }
        }
        return out;
    }

    private static List<SlotDraft> blanks(int count, QualifyStage stage) {
        List<SlotDraft> slots = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            slots.add(new SlotDraft(stage, 0, 0, index, false, null, null));
        }
        return slots;
    }

    static List<SlotDraft> pairEntrants(List<Entrant> entrants, QualifyStage stage) {
        List<Entrant> ordered = seeded(entrants);
        if (ordered.size() < 2 || ordered.size() % 2 != 0) {
            throw new PairingRejected("Для посева нужно чётное число команд");
        }
        List<Integer> seeds = seedOrder(ordered.size());
        List<SlotDraft> matches = new ArrayList<>();
        for (int index = 0; index < seeds.size(); index += 2) {
            Entrant home = ordered.get(seeds.get(index) - 1);
            Entrant away = ordered.get(seeds.get(index + 1) - 1);
            matches.add(new SlotDraft(stage, 0, 0, matches.size(), false, Side.of(home), Side.of(away)));
        }
        separateGroups(matches);
        return matches;
    }

    /** Classic fold: 1 plays n, 4 plays 5, 2 plays 7, 3 plays 6. */
    static List<Integer> seedOrder(int count) {
        List<Integer> seeds = new ArrayList<>();
        seeds.add(1);
        int size = 1;
        while (size < count) {
            List<Integer> next = new ArrayList<>();
            for (int seed : seeds) {
                next.add(seed);
                next.add(size * 2 + 1 - seed);
            }
            seeds = next;
            size *= 2;
        }
        return seeds;
    }

    private static void separateGroups(List<SlotDraft> matches) {
        for (int index = 0; index < matches.size(); index++) {
            SlotDraft slot = matches.get(index);
            if (!sameGroup(slot.home(), slot.away())) {
                continue;
            }
            for (int other = 0; other < matches.size(); other++) {
                if (other == index) {
                    continue;
                }
                SlotDraft candidate = matches.get(other);
                if (sameGroup(slot.home(), candidate.away()) || sameGroup(candidate.home(), slot.away())) {
                    continue;
                }
                matches.set(index, slot.withSides(slot.home(), candidate.away()));
                matches.set(other, candidate.withSides(candidate.home(), slot.away()));
                break;
            }
        }
    }

    private static boolean sameGroup(Side left, Side right) {
        return left != null && right != null && left.groupId().equals(right.groupId());
    }

    private static boolean same(Side left, Side right) {
        return left != null && right != null && left.groupId().equals(right.groupId()) && left.place() == right.place();
    }

    private static List<Entrant> ofStage(List<Entrant> entrants, QualifyStage stage) {
        List<Entrant> mine = new ArrayList<>();
        for (Entrant entrant : entrants) {
            if (entrant.stage() == stage) {
                mine.add(entrant);
            }
        }
        return mine;
    }

    static List<Entrant> seeded(List<Entrant> entrants) {
        List<Entrant> copy = new ArrayList<>(entrants);
        copy.sort(Comparator.comparingInt(Entrant::place)
                .thenComparingInt(Entrant::groupOrder)
                .thenComparing(entrant -> entrant.groupName() == null ? "" : entrant.groupName()));
        return copy;
    }

    public static class PairingRejected extends RuntimeException {
        public PairingRejected(String message) {
            super(message);
        }
    }
}
