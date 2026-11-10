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
        List<QualifyMarkResponse> marks = marks(rules);
        if (rules.isEmpty() || view.rounds() == null || view.rounds().isEmpty()) {
            return withMarks(view, marks);
        }
        List<GroupView> groups = groupViews(tournamentId);
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
            String title = retitle ? roundHeading(chain.get(index).stage()) : round.title();
            String previous = index == 0
                    ? null
                    : (retitle ? roundHeading(chain.get(index - 1).stage()) : view.rounds().get(index - 1).title());
            List<PlayoffSlotResponse> slots = new ArrayList<>();
            for (PlayoffSlotResponse slot : round.slots()) {
                slots.add(paint(slot, title, previous, byName, retitle));
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
        return new PlayoffQualifyResponse(
                blocks,
                rows,
                new PlayoffQualifyResponse.Derived(perStage),
                built,
                built
        );
    }

    private PlayoffSlotResponse paint(
            PlayoffSlotResponse slot,
            String roundTitle,
            String previousTitle,
            Map<String, GroupView> groups,
            boolean retitle
    ) {
        Side home = paintSide(slot, true, previousTitle, groups, retitle);
        Side away = paintSide(slot, false, previousTitle, groups, retitle);
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
            boolean retitle
    ) {
        String groupName = home ? slot.homeGroupName() : slot.awayGroupName();
        Integer place = home ? slot.homePlace() : slot.awayPlace();
        UUID teamId = home ? slot.homeTeamId() : slot.awayTeamId();
        String teamName = home ? slot.homeTeamName() : slot.awayTeamName();
        String waiting = home ? slot.homeWaitingLabel() : slot.awayWaitingLabel();
        boolean seeded = place != null && place > 0 && groupName != null && !groupName.isBlank();
        GroupView group = groupName == null ? null : groups.get(groupName);
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
            int feeder = slot.slotIndex() * 2 + (home ? 1 : 2);
            return new Side(null, null, PlayoffService.winnerSlotLabel(previousTitle, feeder));
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

    private List<QualifyMarkResponse> marks(List<PlayoffQualificationRule> rules) {
        List<QualifyMarkResponse> marks = new ArrayList<>();
        for (PlayoffQualificationRule rule : rules) {
            if (rule.getStage() == QualifyStage.OUT) {
                continue;
            }
            for (int place = rule.getPlaceFrom(); place <= rule.getPlaceTo(); place++) {
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
        if (stage == QualifyStage.F) {
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
