package com.studentleague.tournaments.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.tournaments.domain.CompetitionStage;
import com.studentleague.tournaments.domain.QualifyStage;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.dto.PlayoffQualifyResponse;
import com.studentleague.tournaments.dto.PlayoffRoundResponse;
import com.studentleague.tournaments.dto.PlayoffSlotResponse;
import com.studentleague.tournaments.dto.PlayoffViewResponse;
import com.studentleague.tournaments.dto.QualifyMarkResponse;
import com.studentleague.tournaments.dto.QualifyRuleRequest;
import com.studentleague.tournaments.dto.RebuildPlayoffRequest;
import com.studentleague.tournaments.dto.SavePlayoffQualifyRequest;
import com.studentleague.tournaments.dto.StandingRow;
import com.studentleague.tournaments.dto.StandingTableResponse;
import com.studentleague.tournaments.entity.PlayoffQualificationRule;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.entity.TournamentTable;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.pairing.PlayoffPairing;
import com.studentleague.tournaments.pairing.PlayoffPairing.Entrant;
import com.studentleague.tournaments.pairing.PlayoffPairing.PairingRejected;
import com.studentleague.tournaments.pairing.PlayoffPairing.SlotDraft;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.Failure;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.GroupSize;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.Report;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.RuleSpan;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.StageLine;
import com.studentleague.tournaments.repository.PlayoffQualificationRuleRepository;
import com.studentleague.tournaments.repository.PlayoffSlotRepository;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTableRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlayoffQualifyService {

    private final TournamentRepository tournamentRepository;
    private final TournamentTableRepository tournamentTableRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final MatchRepository matchRepository;
    private final PlayoffQualificationRuleRepository ruleRepository;
    private final PlayoffSlotRepository playoffSlotRepository;
    private final TournamentService tournamentService;
    private final PlayoffService playoffService;

    public PlayoffQualifyService(
            TournamentRepository tournamentRepository,
            TournamentTableRepository tournamentTableRepository,
            TournamentTeamRepository tournamentTeamRepository,
            MatchRepository matchRepository,
            PlayoffQualificationRuleRepository ruleRepository,
            PlayoffSlotRepository playoffSlotRepository,
            TournamentService tournamentService,
            PlayoffService playoffService
    ) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentTableRepository = tournamentTableRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.matchRepository = matchRepository;
        this.ruleRepository = ruleRepository;
        this.playoffSlotRepository = playoffSlotRepository;
        this.tournamentService = tournamentService;
        this.playoffService = playoffService;
    }

    @Transactional(readOnly = true)
    public PlayoffQualifyResponse get(UUID tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        return build(tournament, storedRules(tournamentId));
    }

    @Transactional
    public PlayoffQualifyResponse save(UUID tournamentId, SavePlayoffQualifyRequest request) {
        Tournament tournament = requireTournament(tournamentId);
        if (bracketBuilt(tournament)) {
            throw locked();
        }
        List<GroupView> groups = groupViews(tournamentId);
        List<RuleSpan> spans = spans(request.rules());
        Report report = PlayoffQualifyMath.evaluate(sizes(groups), spans);
        if (report.failure() != null) {
            throw failure(report.failure());
        }
        ruleRepository.deleteByTournamentId(tournamentId);
        ruleRepository.flush();
        for (RuleSpan span : PlayoffQualifyMath.collapse(spans)) {
            PlayoffQualificationRule row = new PlayoffQualificationRule();
            row.setTournamentId(tournamentId);
            row.setTableId(span.groupId());
            row.setPlaceFrom(span.placeFrom());
            row.setPlaceTo(span.placeTo());
            row.setStage(span.stage());
            ruleRepository.save(row);
        }
        return build(tournament, storedRules(tournamentId));
    }

    @Transactional
    public PlayoffQualifyResponse rebuild(UUID tournamentId, RebuildPlayoffRequest request) {
        Tournament tournament = requireTournament(tournamentId);
        if (request == null || !request.confirm()) {
            throw ApiException.badRequest("Подтвердите пересборку сетки");
        }
        if (!bracketBuilt(tournament)) {
            throw ApiException.badRequest("Сетки ещё нет");
        }
        playoffService.resetBracket(tournamentId);
        tournament = requireTournament(tournamentId);
        return build(tournament, storedRules(tournamentId));
    }

    /**
     * Public bracket and table. Place colours come from the saved rules.
     * A group that is still playing shows the seed («A1»), not a team name.
     */
    @Transactional(readOnly = true)
    public PlayoffViewResponse decorate(UUID tournamentId, PlayoffViewResponse view) {
        List<PlayoffQualificationRule> rules = storedRules(tournamentId);
        List<GroupView> groups = rules.isEmpty() ? List.of() : groupViews(tournamentId);
        List<QualifyMarkResponse> marks = marks(rules, groups);
        if (rules.isEmpty() || view.rounds() == null || view.rounds().isEmpty()) {
            return withMarks(view, marks);
        }
        Map<String, GroupView> byName = new HashMap<>();
        for (GroupView group : groups) {
            byName.put(group.name(), group);
        }
        Report report = PlayoffQualifyMath.evaluate(sizes(groups), spansOf(rules));
        List<StageLine> chain = report.stages();
        boolean retitle = !chain.isEmpty() && chain.size() == view.rounds().size();
        List<PlayoffRoundResponse> rounds = new ArrayList<>();
        for (int index = 0; index < view.rounds().size(); index++) {
            PlayoffRoundResponse round = view.rounds().get(index);
            String title = retitle ? roundHeading(chain.get(index).stage(), chain.get(index).total(), index == chain.size() - 1) : round.title();
            String previous = index == 0
                    ? null
                    : (retitle
                    ? roundHeading(chain.get(index - 1).stage(), chain.get(index - 1).total(), false)
                    : view.rounds().get(index - 1).title());
            Map<Integer, Integer> feeders = index == 0
                    ? Map.of()
                    : visibleMatchNumbers(view.rounds().get(index - 1).slots());
            List<PlayoffSlotResponse> slots = new ArrayList<>();
            for (PlayoffSlotResponse slot : round.slots()) {
                slots.add(paint(slot, title, previous, byName, retitle, feeders));
            }
            rounds.add(new PlayoffRoundResponse(round.round(), title, slots));
        }
        return new PlayoffViewResponse(
                view.competitionStage(),
                view.advancingPlaces(),
                view.qualified(),
                rounds,
                view.unassignedMatches(),
                marks
        );
    }

    private PlayoffQualifyResponse build(Tournament tournament, List<PlayoffQualificationRule> rules) {
        List<GroupView> groups = groupViews(tournament.getId());
        Report report = PlayoffQualifyMath.evaluate(sizes(groups), spansOf(rules));
        Map<UUID, Map<Integer, QualifyStage>> byPlace = report.byPlace();
        List<PlayoffQualifyResponse.GroupBlock> blocks = new ArrayList<>();
        for (GroupView group : groups) {
            Map<Integer, QualifyStage> stages = byPlace.getOrDefault(group.id(), Map.of());
            List<PlayoffQualifyResponse.PlaceRow> places = new ArrayList<>();
            for (int place = 1; place <= group.size(); place++) {
                QualifyStage stage = stages.get(place);
                StandingRow team = group.finished() ? group.row(place) : null;
                places.add(new PlayoffQualifyResponse.PlaceRow(
                        place,
                        team == null ? null : team.teamId(),
                        team == null ? null : team.teamName(),
                        stage == null ? null : stage.name()
                ));
            }
            blocks.add(new PlayoffQualifyResponse.GroupBlock(
                    group.id(),
                    group.name(),
                    group.size(),
                    group.finished(),
                    places
            ));
        }
        Map<String, PlayoffQualifyResponse.StageMath> perStage = new LinkedHashMap<>();
        for (StageLine line : report.stages()) {
            perStage.put(line.stage().name(), new PlayoffQualifyResponse.StageMath(
                    line.direct(), line.fromPrev(), line.total(), line.powerOfTwo()
            ));
        }
        List<PlayoffQualifyResponse.RuleRow> rows = new ArrayList<>();
        for (PlayoffQualificationRule rule : rules) {
            rows.add(new PlayoffQualifyResponse.RuleRow(
                    rule.getTableId(),
                    rule.getPlaceFrom(),
                    rule.getPlaceTo(),
                    rule.getStage().name()
            ));
        }
        boolean built = bracketBuilt(tournament);
        PlayoffQualifyResponse.RangeError error = report.failure() == null
                ? null
                : new PlayoffQualifyResponse.RangeError(report.failure().code(), report.failure().message());
        return new PlayoffQualifyResponse(
                blocks,
                rows,
                new PlayoffQualifyResponse.Derived(perStage),
                built,
                built,
                built ? List.of() : proposalOf(groups, report),
                error
        );
    }

    private List<PlayoffQualifyResponse.ProposedPair> proposalOf(List<GroupView> groups, Report report) {
        if (report.failure() != null || report.stages().isEmpty()) {
            return List.of();
        }
        List<Entrant> entrants = new ArrayList<>();
        int order = 0;
        for (GroupView group : groups) {
            Map<Integer, QualifyStage> stages = report.byPlace().getOrDefault(group.id(), Map.of());
            for (int place = 1; place <= group.size(); place++) {
                QualifyStage stage = stages.get(place);
                if (stage == null || !stage.playoffEntry()) {
                    continue;
                }
                StandingRow team = group.finished() ? group.row(place) : null;
                entrants.add(new Entrant(
                        group.id(),
                        group.name(),
                        place,
                        order,
                        stage,
                        team == null ? null : team.teamId()
                ));
            }
            order++;
        }
        List<SlotDraft> slots;
        try {
            slots = PlayoffPairing.arrange(report.stages(), entrants);
        } catch (PairingRejected ex) {
            return List.of();
        }
        QualifyStage closer = report.stages().getLast().total() == 2 ? report.stages().getLast().stage() : null;
        List<PlayoffQualifyResponse.ProposedPair> pairs = new ArrayList<>();
        for (SlotDraft slot : slots) {
            if (slot.bye() || (slot.home() == null && slot.away() == null)) {
                continue;
            }
            String label = slot.stage() == closer ? "Финал" : slot.stage().label();
            pairs.add(new PlayoffQualifyResponse.ProposedPair(
                    slot.stage().name(),
                    label,
                    slot.roundNumber(),
                    slot.pairIndex(),
                    proposedSide(groups, slot.home()),
                    proposedSide(groups, slot.away())
            ));
        }
        return pairs;
    }

    private static PlayoffQualifyResponse.ProposedSide proposedSide(List<GroupView> groups, PlayoffPairing.Side side) {
        if (side == null) {
            return null;
        }
        String teamName = null;
        for (GroupView group : groups) {
            if (group.id().equals(side.groupId()) && group.finished()) {
                StandingRow row = group.row(side.place());
                teamName = row == null ? null : row.teamName();
            }
        }
        return new PlayoffQualifyResponse.ProposedSide(
                side.groupId(),
                side.groupName(),
                side.place(),
                seedLabel(side.groupName(), side.place()),
                teamName
        );
    }

    /** 1-based index among the matches a viewer can see. Bye slots are not numbered. */
    static Map<Integer, Integer> visibleMatchNumbers(List<PlayoffSlotResponse> slots) {
        List<PlayoffSlotResponse> ordered = new ArrayList<>(slots);
        ordered.sort(java.util.Comparator.comparingInt(PlayoffSlotResponse::slotIndex));
        Map<Integer, Integer> numbers = new java.util.HashMap<>();
        int number = 1;
        for (PlayoffSlotResponse slot : ordered) {
            if (slot.bye() || "THIRD".equals(slot.kind())) {
                continue;
            }
            numbers.put(slot.slotIndex(), number++);
        }
        return numbers;
    }

    private PlayoffSlotResponse paint(
            PlayoffSlotResponse slot,
            String roundTitle,
            String previousTitle,
            Map<String, GroupView> groups,
            boolean retitle,
            Map<Integer, Integer> feeders
    ) {
        Side home = paintSide(slot, true, previousTitle, groups, retitle, feeders);
        Side away = paintSide(slot, false, previousTitle, groups, retitle, feeders);
        return new PlayoffSlotResponse(
                slot.id(),
                slot.round(),
                slot.slotIndex(),
                roundTitle,
                slot.matchId(),
                home.teamId(),
                home.teamName(),
                slot.homeGroupName(),
                slot.homePlace(),
                away.teamId(),
                away.teamName(),
                slot.awayGroupName(),
                slot.awayPlace(),
                home.waiting(),
                away.waiting(),
                slot.scheduledAt(),
                slot.venue(),
                slot.matchStatus(),
                slot.homeScore(),
                slot.awayScore(),
                slot.penaltyHome(),
                slot.penaltyAway(),
                slot.minute(),
                slot.startedAt(),
                slot.kind(),
                slot.bye()
        );
    }

    private Side paintSide(
            PlayoffSlotResponse slot,
            boolean home,
            String previousTitle,
            Map<String, GroupView> groups,
            boolean retitle,
            Map<Integer, Integer> feeders
    ) {
        String groupName = home ? slot.homeGroupName() : slot.awayGroupName();
        Integer place = home ? slot.homePlace() : slot.awayPlace();
        UUID teamId = home ? slot.homeTeamId() : slot.awayTeamId();
        String teamName = home ? slot.homeTeamName() : slot.awayTeamName();
        String waiting = home ? slot.homeWaitingLabel() : slot.awayWaitingLabel();
        boolean seeded = place != null && place > 0 && groupName != null && !groupName.isBlank();
        GroupView group = groupName == null ? null : groups.get(groupName);
        if (seeded && group != null && place > group.size()) {
            return new Side(null, null, null);
        }
        if (seeded && group != null && !group.finished()) {
            return new Side(null, null, seedLabel(groupName, place));
        }
        if (seeded && teamId == null && group != null && group.finished()) {
            StandingRow row = group.row(place);
            if (row != null) {
                return new Side(row.teamId(), row.teamName(), null);
            }
        }
        if (teamId == null && retitle && slot.round() > 1 && !"THIRD".equals(slot.kind()) && !seeded) {
            int feederIndex = slot.slotIndex() * 2 + (home ? 0 : 1);
            Integer number = feeders.get(feederIndex);
            if (number != null) {
                return new Side(null, null, PlayoffService.winnerSlotLabel(previousTitle, number));
            }
        }
        return new Side(teamId, teamName, waiting);
    }

    private static PlayoffViewResponse withMarks(PlayoffViewResponse view, List<QualifyMarkResponse> marks) {
        return new PlayoffViewResponse(
                view.competitionStage(),
                view.advancingPlaces(),
                view.qualified(),
                view.rounds(),
                view.unassignedMatches(),
                marks
        );
    }

    private List<QualifyMarkResponse> marks(List<PlayoffQualificationRule> rules, List<GroupView> groups) {
        Map<UUID, Integer> sizes = new HashMap<>();
        for (GroupView group : groups) {
            sizes.put(group.id(), group.size());
        }
        List<QualifyMarkResponse> marks = new ArrayList<>();
        for (PlayoffQualificationRule rule : rules) {
            if (rule.getPlaceTo() < rule.getPlaceFrom()) {
                continue;
            }
            int size = sizes.getOrDefault(rule.getTableId(), 0);
            int last = Math.min(rule.getPlaceTo(), size);
            for (int place = Math.max(1, rule.getPlaceFrom()); place <= last; place++) {
                marks.add(new QualifyMarkResponse(rule.getTableId(), place, rule.getStage().name()));
            }
        }
        return marks;
    }

    private List<PlayoffQualificationRule> storedRules(UUID tournamentId) {
        return ruleRepository.findByTournamentIdOrderByPlaceFromAsc(tournamentId);
    }

    private boolean bracketBuilt(Tournament tournament) {
        return tournament.getCompetitionStage() == CompetitionStage.PLAYOFF
                || playoffSlotRepository.countByTournamentId(tournament.getId()) > 0;
    }

    private List<GroupView> groupViews(UUID tournamentId) {
        List<TournamentTable> tables = tournamentTableRepository.findByTournamentIdOrderBySortOrderAscIdAsc(tournamentId);
        Map<UUID, Integer> sizes = new HashMap<>();
        Map<UUID, List<UUID>> members = new HashMap<>();
        for (TournamentTeam entry : tournamentTeamRepository.findByTournamentId(tournamentId)) {
            if (entry.getStatus() != TournamentTeamStatus.APPROVED || entry.getTableId() == null) {
                continue;
            }
            sizes.merge(entry.getTableId(), 1, Integer::sum);
            members.computeIfAbsent(entry.getTableId(), ignored -> new ArrayList<>()).add(entry.getTeamId());
        }
        Map<UUID, boolean[]> progress = new HashMap<>();
        for (TournamentTable table : tables) {
            progress.put(table.getId(), new boolean[2]);
        }
        Map<UUID, UUID> tableByTeam = new HashMap<>();
        for (Map.Entry<UUID, List<UUID>> entry : members.entrySet()) {
            for (UUID teamId : entry.getValue()) {
                tableByTeam.put(teamId, entry.getKey());
            }
        }
        for (Match match : matchRepository.findByTournamentId(tournamentId)) {
            if (match.getBracketRound() != null || match.getStatus() == MatchStatus.CANCELLED) {
                continue;
            }
            UUID tableId = match.getTableId();
            if (tableId == null) {
                UUID homeTable = tableByTeam.get(match.getHomeTeamId());
                UUID awayTable = tableByTeam.get(match.getAwayTeamId());
                if (homeTable != null && homeTable.equals(awayTable)) {
                    tableId = homeTable;
                }
            }
            boolean[] flags = tableId == null ? null : progress.get(tableId);
            if (flags == null) {
                continue;
            }
            if (match.getStatus() == MatchStatus.FINISHED) {
                flags[0] = true;
            } else {
                flags[1] = true;
            }
        }
        Map<UUID, StandingTableResponse> standings = new HashMap<>();
        for (StandingTableResponse table : tournamentService.standings(tournamentId).tables()) {
            if (table.id() != null) {
                standings.put(table.id(), table);
            }
        }
        List<GroupView> groups = new ArrayList<>();
        for (TournamentTable table : tables) {
            boolean[] flags = progress.getOrDefault(table.getId(), new boolean[2]);
            boolean finished = flags[0] && !flags[1];
            groups.add(new GroupView(
                    table.getId(),
                    table.getName(),
                    sizes.getOrDefault(table.getId(), 0),
                    finished,
                    standings.get(table.getId())
            ));
        }
        return groups;
    }

    private static List<GroupSize> sizes(List<GroupView> groups) {
        List<GroupSize> sizes = new ArrayList<>();
        for (GroupView group : groups) {
            sizes.add(new GroupSize(group.id(), group.name(), group.size()));
        }
        return sizes;
    }

    private List<RuleSpan> spans(List<QualifyRuleRequest> rules) {
        List<RuleSpan> spans = new ArrayList<>();
        for (QualifyRuleRequest rule : rules) {
            if (rule.stage() == QualifyStage.F) {
                throw new ApiException(
                        PlayoffQualifyMath.PLACE_OUT_OF_RANGE,
                        "В финал напрямую не ставят",
                        HttpStatus.BAD_REQUEST
                );
            }
            spans.add(new RuleSpan(rule.groupId(), rule.placeFrom(), rule.placeTo(), rule.stage()));
        }
        return spans;
    }

    private static List<RuleSpan> spansOf(List<PlayoffQualificationRule> rules) {
        List<RuleSpan> spans = new ArrayList<>();
        for (PlayoffQualificationRule rule : rules) {
            spans.add(new RuleSpan(rule.getTableId(), rule.getPlaceFrom(), rule.getPlaceTo(), rule.getStage()));
        }
        return spans;
    }

    private Tournament requireTournament(UUID tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> ApiException.notFound("Турнир не найден"));
    }

    private static ApiException locked() {
        return new ApiException(
                PlayoffQualifyMath.LOCKED,
                "Сетка уже сформирована",
                HttpStatus.CONFLICT
        );
    }

    private static ApiException failure(Failure failure) {
        return new ApiException(failure.code(), failure.message(), HttpStatus.BAD_REQUEST);
    }

    static String roundHeading(QualifyStage stage) {
        return roundHeading(stage, 0, false);
    }

    static String roundHeading(QualifyStage stage, int total, boolean closing) {
        if (stage == QualifyStage.F || (closing && total == 2)) {
            return "Финал";
        }
        return stage.label() + " финала";
    }

    /** Same token the bracket already prints: «Группа A» + 1 → «A1». */
    static String seedLabel(String groupName, int place) {
        String trimmed = groupName.trim();
        Character letter = null;
        for (int index = trimmed.length() - 1; index >= 0; index--) {
            char c = trimmed.charAt(index);
            if (Character.isWhitespace(c)) {
                break;
            }
            if (Character.isLetter(c)) {
                letter = Character.toUpperCase(c);
                break;
            }
        }
        String token = letter == null ? trimmed : String.valueOf(letter);
        return token + place;
    }

    private record GroupView(UUID id, String name, int size, boolean finished, StandingTableResponse table) {
        StandingRow row(int place) {
            if (table == null || place < 1 || place > table.rows().size()) {
                return null;
            }
            return table.rows().get(place - 1);
        }
    }

    private record Side(UUID teamId, String teamName, String waiting) {
    }
}
