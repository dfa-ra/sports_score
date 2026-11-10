package com.studentleague.tournaments.service;

import com.studentleague.cache.StandingsBoardCache;
import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.clock.MatchClock;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.tournaments.domain.CompetitionStage;
import com.studentleague.tournaments.domain.QualifyStage;
import com.studentleague.tournaments.domain.TournamentStatus;
import com.studentleague.tournaments.dto.ApplyPairsRequest;
import com.studentleague.tournaments.dto.PlayoffQualifyResponse;
import com.studentleague.tournaments.dto.PlayoffQualifyResponse.StageMath;
import com.studentleague.tournaments.entity.PlayoffSlot;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.pairing.PlayoffPairing;
import com.studentleague.tournaments.pairing.PlayoffPairing.Edit;
import com.studentleague.tournaments.pairing.PlayoffPairing.Entrant;
import com.studentleague.tournaments.pairing.PlayoffPairing.PairingRejected;
import com.studentleague.tournaments.pairing.PlayoffPairing.Side;
import com.studentleague.tournaments.pairing.PlayoffPairing.SlotDraft;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath;
import com.studentleague.tournaments.qualify.PlayoffQualifyMath.StageLine;
import com.studentleague.tournaments.repository.PlayoffSlotRepository;
import com.studentleague.tournaments.repository.TournamentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Writes the bracket from {@link PlayoffPairing}. The qualification screen
 * does not call this. Swap the class if pairs become fully manual.
 */
@Service
public class PlayoffPairService {

    private final PlayoffQualifyService playoffQualifyService;
    private final TournamentRepository tournamentRepository;
    private final PlayoffSlotRepository playoffSlotRepository;
    private final MatchRepository matchRepository;
    private final StandingsBoardCache standingsBoardCache;

    public PlayoffPairService(
            PlayoffQualifyService playoffQualifyService,
            TournamentRepository tournamentRepository,
            PlayoffSlotRepository playoffSlotRepository,
            MatchRepository matchRepository,
            StandingsBoardCache standingsBoardCache
    ) {
        this.playoffQualifyService = playoffQualifyService;
        this.tournamentRepository = tournamentRepository;
        this.playoffSlotRepository = playoffSlotRepository;
        this.matchRepository = matchRepository;
        this.standingsBoardCache = standingsBoardCache;
    }

    @Transactional
    public PlayoffQualifyResponse apply(UUID tournamentId, ApplyPairsRequest request) {
        if (request == null || !request.confirm()) {
            throw ApiException.badRequest("Подтвердите сборку сетки");
        }
        PlayoffQualifyResponse current = playoffQualifyService.get(tournamentId);
        if (current.bracketBuilt()) {
            throw new ApiException(PlayoffQualifyMath.LOCKED, "Сетка уже сформирована", HttpStatus.CONFLICT);
        }
        if (current.rules() == null || current.rules().isEmpty()) {
            throw ApiException.badRequest("Сначала сохраните выход из групп");
        }
        if (current.error() != null
                && !PlayoffQualifyMath.NOT_POWER_OF_TWO.equals(current.error().code())) {
            throw new ApiException(current.error().code(), current.error().message(), HttpStatus.BAD_REQUEST);
        }
        List<StageLine> lines = linesOf(current);
        if (lines.isEmpty()) {
            throw new ApiException(
                    PlayoffQualifyMath.NOT_POWER_OF_TWO,
                    "В плей-офф не выходит ни одна команда",
                    HttpStatus.BAD_REQUEST
            );
        }
        StageLine previous = null;
        for (StageLine line : lines) {
            if (!line.powerOfTwo()) {
                throw new ApiException(
                        PlayoffQualifyMath.NOT_POWER_OF_TWO,
                        PlayoffQualifyMath.powerMessage(line, previous == null ? null : previous.stage()),
                        HttpStatus.BAD_REQUEST
                );
            }
            previous = line;
        }
        List<Entrant> entrants = entrantsOf(current);
        List<Edit> edits = editsOf(request);
        List<SlotDraft> slots;
        try {
            slots = PlayoffPairing.arrange(lines, entrants, edits);
        } catch (PairingRejected ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> ApiException.notFound("Турнир не найден"));
        for (SlotDraft draft : slots) {
            PlayoffSlot slot = new PlayoffSlot();
            slot.setTournamentId(tournamentId);
            slot.setRoundNumber(draft.roundNumber());
            slot.setSlotIndex(draft.slotIndex());
            slot.setBye(draft.bye());
            applySide(slot, true, draft.home());
            applySide(slot, false, draft.away());
            if (bothKnown(draft)) {
                Match match = newMatch(tournament, draft);
                slot.setMatchId(matchRepository.save(match).getId());
            }
            playoffSlotRepository.save(slot);
        }
        tournament.setCompetitionStage(CompetitionStage.PLAYOFF);
        if (tournament.getStatus() == TournamentStatus.REGISTRATION) {
            tournament.setStatus(TournamentStatus.ACTIVE);
        }
        tournamentRepository.save(tournament);
        standingsBoardCache.invalidateTournament(tournamentId);
        return playoffQualifyService.get(tournamentId);
    }

    private static boolean bothKnown(SlotDraft draft) {
        return !draft.bye()
                && draft.home() != null
                && draft.away() != null
                && draft.home().teamId() != null
                && draft.away().teamId() != null;
    }

    private static void applySide(PlayoffSlot slot, boolean home, Side side) {
        if (side == null) {
            return;
        }
        if (home) {
            slot.setHomeTeamId(side.teamId());
            slot.setHomeGroupName(side.groupName());
            slot.setHomePlace(side.place());
        } else {
            slot.setAwayTeamId(side.teamId());
            slot.setAwayGroupName(side.groupName());
            slot.setAwayPlace(side.place());
        }
    }

    private static Match newMatch(Tournament tournament, SlotDraft draft) {
        Match match = new Match();
        match.setTournamentId(tournament.getId());
        match.setSportId(tournament.getSportId());
        match.setHomeTeamId(draft.home().teamId());
        match.setAwayTeamId(draft.away().teamId());
        match.setStatus(MatchStatus.SCHEDULED);
        match.setHomeScore(0);
        match.setAwayScore(0);
        match.setPeriodCount(MatchClock.DEFAULT_PERIOD_COUNT);
        match.setPeriodLengthSeconds(MatchClock.DEFAULT_PERIOD_LENGTH_SECONDS);
        match.setBracketRound(draft.roundNumber());
        return match;
    }

    private static List<StageLine> linesOf(PlayoffQualifyResponse current) {
        List<StageLine> lines = new ArrayList<>();
        if (current.derived() == null || current.derived().perStage() == null) {
            return lines;
        }
        for (var entry : current.derived().perStage().entrySet()) {
            StageMath math = entry.getValue();
            lines.add(new StageLine(
                    QualifyStage.valueOf(entry.getKey()),
                    math.direct(),
                    math.fromPrev(),
                    math.total(),
                    math.powerOfTwo()
            ));
        }
        return lines;
    }

    private static List<Entrant> entrantsOf(PlayoffQualifyResponse current) {
        List<Entrant> entrants = new ArrayList<>();
        int order = 0;
        for (PlayoffQualifyResponse.GroupBlock group : current.groups()) {
            for (PlayoffQualifyResponse.PlaceRow place : group.places()) {
                QualifyStage stage = QualifyStage.direct(place.stage());
                if (stage == null || !stage.playoffEntry()) {
                    continue;
                }
                UUID teamId = group.finished() ? place.teamId() : null;
                entrants.add(new Entrant(group.id(), group.name(), place.place(), order, stage, teamId));
            }
            order++;
        }
        return entrants;
    }

    private static List<Edit> editsOf(ApplyPairsRequest request) {
        if (request.pairs() == null || request.pairs().isEmpty()) {
            return List.of();
        }
        List<Edit> edits = new ArrayList<>();
        for (ApplyPairsRequest.PairEdit pair : request.pairs()) {
            edits.add(new Edit(
                    pair.stage(),
                    pair.pairIndex(),
                    pair.homeGroupId(),
                    pair.homePlace(),
                    pair.awayGroupId(),
                    pair.awayPlace()
            ));
        }
        return edits;
    }
}
