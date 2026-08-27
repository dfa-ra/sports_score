package com.studentleague.tournaments.controller;

import com.studentleague.common.dto.PageResponse;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.service.MatchService;
import com.studentleague.security.UserPrincipal;
import com.studentleague.tournaments.domain.TournamentStatus;
import com.studentleague.tournaments.dto.CalendarImportResponse;
import com.studentleague.tournaments.dto.CreateTournamentRequest;
import com.studentleague.tournaments.dto.RegisterTeamRequest;
import com.studentleague.tournaments.dto.StandingRow;
import com.studentleague.tournaments.dto.TournamentFormatResponse;
import com.studentleague.tournaments.dto.TournamentResponse;
import com.studentleague.tournaments.dto.TournamentTeamResponse;
import com.studentleague.tournaments.dto.UpdateTournamentRequest;
import com.studentleague.tournaments.service.CalendarImportService;
import com.studentleague.tournaments.service.TournamentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tournaments")
@Tag(name = "Tournaments")
@SecurityRequirement(name = "bearerAuth")
public class TournamentController {

    private final TournamentService tournamentService;
    private final MatchService matchService;
    private final CalendarImportService calendarImportService;

    public TournamentController(
            TournamentService tournamentService,
            MatchService matchService,
            CalendarImportService calendarImportService
    ) {
        this.tournamentService = tournamentService;
        this.matchService = matchService;
        this.calendarImportService = calendarImportService;
    }

    @GetMapping
    @Operation(summary = "List tournaments")
    public PageResponse<TournamentResponse> list(
            @RequestParam(required = false) TournamentStatus status,
            @RequestParam(required = false) UUID sportId,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PageResponse.from(tournamentService.list(status, sportId, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create tournament (ADMIN)")
    public TournamentResponse create(@Valid @RequestBody CreateTournamentRequest request) {
        return tournamentService.create(request);
    }

    @GetMapping("/current")
    @Operation(summary = "Current (active or latest) tournament")
    public ResponseEntity<TournamentResponse> current() {
        TournamentResponse current = tournamentService.current();
        return current == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(current);
    }

    @GetMapping("/formats")
    @Operation(summary = "Supported tournament formats")
    public List<TournamentFormatResponse> formats() {
        return tournamentService.formats();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tournament")
    public TournamentResponse get(@PathVariable UUID id) {
        return tournamentService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update tournament (ADMIN)")
    public TournamentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTournamentRequest request) {
        return tournamentService.update(id, request);
    }

    @PostMapping("/{id}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a team (captain of that team or ADMIN)")
    public TournamentTeamResponse register(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RegisterTeamRequest request
    ) {
        return tournamentService.registerTeam(principal, id, request);
    }

    @PostMapping("/{id}/teams/{teamId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve team registration (ADMIN)")
    public TournamentTeamResponse approve(@PathVariable UUID id, @PathVariable UUID teamId) {
        return tournamentService.approveTeam(id, teamId);
    }

    @DeleteMapping("/{id}/teams/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exclude team from tournament (ADMIN)")
    public void exclude(@PathVariable UUID id, @PathVariable UUID teamId) {
        tournamentService.excludeTeam(id, teamId);
    }

    @GetMapping("/{id}/teams")
    @Operation(summary = "List tournament participants")
    public List<TournamentTeamResponse> teams(@PathVariable UUID id) {
        return tournamentService.listTeams(id);
    }

    @GetMapping("/{id}/standings")
    @Operation(summary = "Tournament standings from finished matches")
    public List<StandingRow> standings(@PathVariable UUID id) {
        return tournamentService.standings(id);
    }

    @GetMapping("/{id}/matches")
    @Operation(summary = "List matches in tournament")
    public PageResponse<MatchResponse> matches(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return PageResponse.from(matchService.list(id, null, pageable));
    }

    @PostMapping(value = "/{id}/calendar/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Import calendar from CSV or Excel (ADMIN). Columns: date, time, home, away")
    public CalendarImportResponse importCalendar(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file
    ) {
        return calendarImportService.importCalendar(id, file);
    }
}
