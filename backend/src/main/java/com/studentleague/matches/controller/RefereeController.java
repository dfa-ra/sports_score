package com.studentleague.matches.controller;

import com.studentleague.matches.dto.AnalystAssignmentResponse;
import com.studentleague.matches.dto.AnalystPersonResponse;
import com.studentleague.matches.dto.AssignAnalystsRequest;
import com.studentleague.matches.dto.CreateMatchEventRequest;
import com.studentleague.matches.dto.MatchEventResponse;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.service.AnalystAssignmentService;
import com.studentleague.matches.service.RefereeMatchService;
import com.studentleague.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/referee")
@Tag(name = "Referee")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('REFEREE','ADMIN')")
public class RefereeController {

    private final RefereeMatchService refereeMatchService;
    private final AnalystAssignmentService analystAssignmentService;

    public RefereeController(
            RefereeMatchService refereeMatchService,
            AnalystAssignmentService analystAssignmentService
    ) {
        this.refereeMatchService = refereeMatchService;
        this.analystAssignmentService = analystAssignmentService;
    }

    @GetMapping("/analysts")
    @Operation(summary = "List users who already have the analyst role")
    public List<AnalystPersonResponse> analysts(@RequestParam(defaultValue = "") String q) {
        return analystAssignmentService.search(q);
    }

    @PutMapping("/matches/{id}/analysts")
    @Operation(summary = "Assign analysts to this match (match referee or admin)")
    public AnalystAssignmentResponse assignAnalysts(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AssignAnalystsRequest request
    ) {
        return analystAssignmentService.assign(principal, id, request);
    }

    @DeleteMapping("/matches/{id}/analysts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear analyst assignment (match referee or admin)")
    public void clearAnalysts(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        analystAssignmentService.clear(principal, id);
    }

    @GetMapping("/matches")
    @Operation(summary = "List matches assigned to current referee")
    public List<MatchResponse> assigned(@AuthenticationPrincipal UserPrincipal principal) {
        return refereeMatchService.assignedMatches(principal);
    }

    @PostMapping("/matches/{id}/start")
    @Operation(summary = "Start assigned match")
    public MatchResponse start(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return refereeMatchService.start(principal, id);
    }

    @PostMapping("/matches/{id}/pause")
    @Operation(summary = "Pause assigned match")
    public MatchResponse pause(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return refereeMatchService.pause(principal, id);
    }

    @PostMapping("/matches/{id}/resume")
    @Operation(summary = "Resume assigned match")
    public MatchResponse resume(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return refereeMatchService.resume(principal, id);
    }

    @PostMapping("/matches/{id}/finish")
    @Operation(summary = "Finish assigned match")
    public MatchResponse finish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return refereeMatchService.finish(principal, id);
    }

    @PostMapping("/matches/{id}/next-period")
    @Operation(summary = "Start the next period")
    public MatchResponse nextPeriod(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return refereeMatchService.nextPeriod(principal, id);
    }

    @PostMapping("/matches/{id}/events")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add match event")
    public MatchEventResponse addEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateMatchEventRequest request
    ) {
        return refereeMatchService.addEvent(principal, id, request);
    }

    @PostMapping("/matches/{id}/events/{eventId}/void")
    @Operation(summary = "Void erroneous match event")
    public MatchEventResponse voidEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID eventId
    ) {
        return refereeMatchService.voidEvent(principal, id, eventId);
    }
}
