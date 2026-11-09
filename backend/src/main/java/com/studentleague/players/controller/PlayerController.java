package com.studentleague.players.controller;

import com.studentleague.common.dto.PageResponse;
import com.studentleague.players.dto.PlayerCardResponse;
import com.studentleague.players.dto.PlayerProfileRequest;
import com.studentleague.players.dto.PlayerProfileResponse;
import com.studentleague.players.service.PlayerService;
import com.studentleague.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/players")
@Tag(name = "Players")
@SecurityRequirement(name = "bearerAuth")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    @Operation(summary = "Search players by registration name. Each word must occur in the first or last name; case and extra spaces are ignored. Shirt nickname is not matched. Empty without a query of at least 2 characters, unless teamId is set. Optional tournamentId keeps active players of pending or approved teams. Page size is capped at 12.")
    public PageResponse<PlayerProfileResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID tournamentId,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return PageResponse.from(playerService.list(q, teamId, tournamentId, pageable));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user's player profile")
    public PlayerProfileResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return playerService.getMyProfile(principal.getId());
    }

    @PutMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Create or update current user's player profile (promotes FAN to PLAYER)")
    public PlayerProfileResponse upsertMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PlayerProfileRequest request
    ) {
        return playerService.createOrUpdateMyProfile(principal.getId(), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get player profile by id")
    public PlayerProfileResponse get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return playerService.getById(id, principal);
    }

    @GetMapping("/{id}/card")
    @Operation(summary = "Public player card")
    public PlayerCardResponse card(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return playerService.getPublicCard(id, principal);
    }
}
