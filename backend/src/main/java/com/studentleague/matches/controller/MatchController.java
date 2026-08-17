package com.studentleague.matches.controller;

import com.studentleague.common.dto.PageResponse;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.AssignRefereeRequest;
import com.studentleague.matches.dto.CreateMatchRequest;
import com.studentleague.matches.dto.MatchRefereeResponse;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.service.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
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
}
