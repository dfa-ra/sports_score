package com.studentleague.teams.controller;

import com.studentleague.common.dto.PageResponse;
import com.studentleague.security.UserPrincipal;
import com.studentleague.teams.dto.AddTeamMemberRequest;
import com.studentleague.teams.dto.AssignCaptainRequest;
import com.studentleague.teams.dto.CreateTeamRequest;
import com.studentleague.teams.dto.TeamMemberResponse;
import com.studentleague.teams.dto.TeamResponse;
import com.studentleague.teams.dto.UpdateTeamRequest;
import com.studentleague.teams.service.TeamService;
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
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams")
@SecurityRequirement(name = "bearerAuth")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    @Operation(summary = "List teams")
    public PageResponse<TeamResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeDisbanded,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return PageResponse.from(teamService.listTeams(q, includeDisbanded, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a team (caller becomes captain)")
    public TeamResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTeamRequest request
    ) {
        return teamService.createTeam(principal, request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get team details")
    public TeamResponse get(@PathVariable UUID id) {
        return teamService.getTeam(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update team (captain or admin)")
    public TeamResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTeamRequest request
    ) {
        return teamService.updateTeam(principal, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Disband a team (admin only)")
    public void disband(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        teamService.disbandTeam(principal, id);
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "List active team members")
    public List<TeamMemberResponse> members(@PathVariable UUID id) {
        return teamService.listMembers(id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add player to team (captain or admin)")
    public TeamMemberResponse addMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddTeamMemberRequest request
    ) {
        return teamService.addMember(principal, id, request);
    }

    @DeleteMapping("/{id}/members/{playerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove player from team (captain or admin)")
    public void removeMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID playerId
    ) {
        teamService.removeMember(principal, id, playerId);
    }

    @PutMapping("/{id}/captain")
    @Operation(summary = "Assign team captain (current captain or admin)")
    public TeamResponse assignCaptain(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AssignCaptainRequest request
    ) {
        return teamService.assignCaptain(principal, id, request);
    }
}
