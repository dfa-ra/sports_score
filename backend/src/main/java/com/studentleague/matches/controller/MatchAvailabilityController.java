package com.studentleague.matches.controller;

import com.studentleague.matches.dto.MatchAvailabilityListResponse;
import com.studentleague.matches.dto.MatchAvailabilityResponse;
import com.studentleague.matches.dto.SetMatchAvailabilityRequest;
import com.studentleague.matches.service.MatchAvailabilityService;
import com.studentleague.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matches/{matchId}/availability")
@Tag(name = "Matches")
@SecurityRequirement(name = "bearerAuth")
public class MatchAvailabilityController {

    private final MatchAvailabilityService availabilityService;

    public MatchAvailabilityController(MatchAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    @Operation(summary = "Who is going to the match (players of either team)")
    public MatchAvailabilityListResponse list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID matchId
    ) {
        return availabilityService.list(principal, matchId);
    }

    @PostMapping
    @Operation(summary = "Set own going / not going status for a scheduled match")
    public MatchAvailabilityResponse setOwn(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID matchId,
            @Valid @RequestBody SetMatchAvailabilityRequest request
    ) {
        return availabilityService.setOwn(principal, matchId, request);
    }
}
