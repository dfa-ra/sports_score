package com.studentleague.matches.controller;

import com.studentleague.matches.dto.AdminCreateMatchEventRequest;
import com.studentleague.matches.dto.AdminUpdateMatchEventRequest;
import com.studentleague.matches.dto.MatchEventResponse;
import com.studentleague.matches.service.AdminMatchProtocolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/matches")
@Tag(name = "Admin Matches")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMatchController {

    private final AdminMatchProtocolService protocolService;

    public AdminMatchController(AdminMatchProtocolService protocolService) {
        this.protocolService = protocolService;
    }

    @PostMapping("/{id}/events")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a goal or card to the match protocol (ADMIN)")
    public MatchEventResponse addEvent(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCreateMatchEventRequest request
    ) {
        return protocolService.add(id, request);
    }

    @PatchMapping("/{id}/events/{eventId}")
    @Operation(summary = "Change the minute of a protocol event (ADMIN)")
    public MatchEventResponse updateMinute(
            @PathVariable UUID id,
            @PathVariable UUID eventId,
            @Valid @RequestBody AdminUpdateMatchEventRequest request
    ) {
        return protocolService.updateMinute(id, eventId, request);
    }

    @PostMapping("/{id}/events/{eventId}/void")
    @Operation(summary = "Void a protocol event so it drops out of the public protocol (ADMIN)")
    public MatchEventResponse voidEvent(@PathVariable UUID id, @PathVariable UUID eventId) {
        return protocolService.voidEvent(id, eventId);
    }
}
