package com.studentleague.tournaments.controller;

import com.studentleague.tournaments.dto.ApplyPairsRequest;
import com.studentleague.tournaments.dto.PlayoffQualifyResponse;
import com.studentleague.tournaments.dto.RebuildPlayoffRequest;
import com.studentleague.tournaments.dto.SavePlayoffQualifyRequest;
import com.studentleague.tournaments.service.PlayoffPairService;
import com.studentleague.tournaments.service.PlayoffQualifyService;
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
@RequestMapping("/api/v1/tournaments/{id}/playoff-qualify")
@Tag(name = "Playoff qualification")
@SecurityRequirement(name = "bearerAuth")
public class PlayoffQualifyController {

    private final PlayoffQualifyService playoffQualifyService;
    private final PlayoffPairService playoffPairService;

    public PlayoffQualifyController(
            PlayoffQualifyService playoffQualifyService,
            PlayoffPairService playoffPairService
    ) {
        this.playoffQualifyService = playoffQualifyService;
        this.playoffPairService = playoffPairService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Group-by-group playoff places (ADMIN)")
    public PlayoffQualifyResponse get(@PathVariable UUID id) {
        return playoffQualifyService.get(id);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Save which place from each group enters which stage (ADMIN)")
    public PlayoffQualifyResponse save(
            @PathVariable UUID id,
            @Valid @RequestBody SavePlayoffQualifyRequest request
    ) {
        return playoffQualifyService.save(id, request);
    }

    @PostMapping("/pairs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Seed the bracket from the saved places, with optional pair edits (ADMIN)")
    public PlayoffQualifyResponse pairs(
            @PathVariable UUID id,
            @Valid @RequestBody ApplyPairsRequest request
    ) {
        return playoffPairService.apply(id, request);
    }

    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Drop the bracket and playoff results after confirmation (ADMIN)")
    public PlayoffQualifyResponse rebuild(
            @PathVariable UUID id,
            @Valid @RequestBody RebuildPlayoffRequest request
    ) {
        return playoffQualifyService.rebuild(id, request);
    }
}
