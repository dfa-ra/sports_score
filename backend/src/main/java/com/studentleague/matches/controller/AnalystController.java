package com.studentleague.matches.controller;

import com.studentleague.matches.dto.AdjustAnalystStatRequest;
import com.studentleague.matches.dto.AnalystStatsResponse;
import com.studentleague.matches.dto.SetPossessionRequest;
import com.studentleague.matches.service.AnalystStatsService;
import com.studentleague.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analyst")
@Tag(name = "Analyst")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
public class AnalystController {

    private final AnalystStatsService analystStatsService;

    public AnalystController(AnalystStatsService analystStatsService) {
        this.analystStatsService = analystStatsService;
    }

    @PostMapping("/matches/{id}/stats")
    @Operation(summary = "Add one team stat (analyst or admin)")
    public AnalystStatsResponse add(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AdjustAnalystStatRequest request
    ) {
        return analystStatsService.adjust(principal, id, request.teamId(), request.stat(), 1);
    }

    @PostMapping("/matches/{id}/stats/undo")
    @Operation(summary = "Undo one team stat of this type (analyst or admin)")
    public AnalystStatsResponse undo(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AdjustAnalystStatRequest request
    ) {
        return analystStatsService.adjust(principal, id, request.teamId(), request.stat(), -1);
    }

    @PostMapping("/matches/{id}/possession")
    @Operation(summary = "Give the ball to a team or pause possession (analyst or admin)")
    public AnalystStatsResponse possession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SetPossessionRequest request
    ) {
        return analystStatsService.setPossession(principal, id, request.side());
    }
}
