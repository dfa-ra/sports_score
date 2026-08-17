package com.studentleague.matches.controller;

import com.studentleague.common.dto.PageResponse;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.AssignRefereeRequest;
import com.studentleague.matches.dto.CreateMatchRequest;
import com.studentleague.matches.dto.MatchEventResponse;
import com.studentleague.matches.dto.MatchLineupsResponse;
import com.studentleague.matches.dto.MatchRefereeResponse;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.dto.SetMatchLineupRequest;
import com.studentleague.matches.service.MatchLineupService;
import com.studentleague.matches.service.MatchService;
import com.studentleague.matches.service.RefereeMatchService;
import com.studentleague.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matches")
@Tag(name = "Matches")
@SecurityRequirement(name = "bearerAuth")
public class MatchController {

    private final MatchService matchService;
    private final RefereeMatchService refereeMatchService;
    private final MatchLineupService matchLineupService;

    public MatchController(
            MatchService matchService,
            RefereeMatchService refereeMatchService,
            MatchLineupService matchLineupService
    ) {
        this.matchService = matchService;
        this.refereeMatchService = refereeMatchService;
        this.matchLineupService = matchLineupService;
    }

    @GetMapping
    @Operation(summary = "List matches with optional filters")
    public PageResponse<MatchResponse> list(
            @RequestParam(required = false) UUID tournamentId,
            @RequestParam(required = false) MatchStatus status,
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return PageResponse.from(matchService.list(tournamentId, status, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Schedule a match (ADMIN)")
    public MatchResponse create(@Valid @RequestBody CreateMatchRequest request) {
        return matchService.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get match details")
    public MatchResponse get(@PathVariable UUID id) {
        return matchService.get(id);
    }

    @PostMapping("/{id}/referees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign referee to match (ADMIN)")
    public MatchRefereeResponse assignReferee(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRefereeRequest request
    ) {
        return matchService.assignReferee(id, request);
    }

    @GetMapping("/{id}/referees")
    @Operation(summary = "List referees assigned to match")
    public List<MatchRefereeResponse> listReferees(@PathVariable UUID id) {
        return matchService.listReferees(id);
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "List match events")
    public List<MatchEventResponse> listEvents(@PathVariable UUID id) {
        return refereeMatchService.listEvents(id);
    }

    @GetMapping("/{id}/lineups")
    @Operation(summary = "Match lineups or roster fallback")
    public MatchLineupsResponse lineups(@PathVariable UUID id) {
        return matchLineupService.getLineups(id);
    }

    @PutMapping("/{id}/lineups")
    @Operation(summary = "Set starting lineup (captain, assigned referee or admin)")
    public MatchLineupsResponse setLineup(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SetMatchLineupRequest request
    ) {
        return matchLineupService.setLineup(principal, id, request);
    }
}
