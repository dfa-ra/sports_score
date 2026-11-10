package com.studentleague.tournaments.controller;

import com.studentleague.tournaments.dto.AssignGroupMatchRequest;
import com.studentleague.tournaments.dto.OpenPlayoffRequest;
import com.studentleague.tournaments.dto.PlayoffSlotResponse;
import com.studentleague.tournaments.dto.PlayoffViewResponse;
import com.studentleague.tournaments.dto.SetAdvancingPlacesRequest;
import com.studentleague.tournaments.dto.UpdatePlayoffSlotRequest;
import com.studentleague.tournaments.service.PlayoffQualifyService;
import com.studentleague.tournaments.service.PlayoffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tournaments/{id}")
@Tag(name = "Playoff")
@SecurityRequirement(name = "bearerAuth")
public class PlayoffController {

    private final PlayoffService playoffService;
    private final PlayoffQualifyService playoffQualifyService;

    public PlayoffController(PlayoffService playoffService, PlayoffQualifyService playoffQualifyService) {
        this.playoffService = playoffService;
        this.playoffQualifyService = playoffQualifyService;
    }

    @GetMapping("/playoff")
    @Operation(summary = "Playoff bracket, qualifying places and teams that advance")
    public PlayoffViewResponse view(@PathVariable UUID id) {
        return playoffQualifyService.decorate(id, playoffService.view(id));
    }

    @PutMapping("/playoff/places")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set which finishing places advance from each group (ADMIN)")
    public PlayoffViewResponse places(@PathVariable UUID id, @Valid @RequestBody SetAdvancingPlacesRequest request) {
        return playoffService.setPlaces(id, request);
    }

    @PutMapping("/matches/{matchId}/group")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Attach an existing match to a group without changing its score or teams (ADMIN)")
    public PlayoffViewResponse assignMatch(
            @PathVariable UUID id,
            @PathVariable UUID matchId,
            @Valid @RequestBody AssignGroupMatchRequest request
    ) {
        return playoffService.assignMatch(id, matchId, request);
    }

    @PostMapping("/playoff")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Open the playoff and pair qualified teams by hand (ADMIN)")
    public PlayoffViewResponse open(@PathVariable UUID id, @Valid @RequestBody OpenPlayoffRequest request) {
        return playoffService.open(id, request);
    }

    @PutMapping("/playoff/slots/{slotId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change kickoff and venue of a playoff pair. Teams stay locked (ADMIN)")
    public PlayoffSlotResponse updateSlot(
            @PathVariable UUID id,
            @PathVariable UUID slotId,
            @Valid @RequestBody UpdatePlayoffSlotRequest request
    ) {
        return playoffService.updateSlot(id, slotId, request);
    }
}
