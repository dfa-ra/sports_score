package com.studentleague.storage.controller;

import com.studentleague.common.exception.ApiException;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.security.UserPrincipal;
import com.studentleague.storage.StorageService;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.users.domain.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uploads")
@Tag(name = "Uploads")
@SecurityRequirement(name = "bearerAuth")
public class UploadController {

    private final StorageService storageService;
    private final PlayerProfileRepository playerProfileRepository;
    private final TeamRepository teamRepository;

    public UploadController(
            StorageService storageService,
            PlayerProfileRepository playerProfileRepository,
            TeamRepository teamRepository
    ) {
        this.storageService = storageService;
        this.playerProfileRepository = playerProfileRepository;
        this.teamRepository = teamRepository;
    }

    @PostMapping(value = "/players/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload current player's avatar")
    public Map<String, String> uploadAvatar(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file
    ) {
        validateImage(file);
        PlayerProfile profile = playerProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> ApiException.badRequest("Create a player profile first"));
        String url = storageService.store("avatars", file);
        profile.setAvatarUrl(url);
        playerProfileRepository.save(profile);
        return Map.of("url", url);
    }

    @PostMapping(value = "/teams/{teamId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload team logo (captain or admin)")
    public Map<String, String> uploadTeamLogo(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID teamId,
            @RequestPart("file") MultipartFile file
    ) {
        validateImage(file);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Team not found"));
        if (!principal.hasRole(Role.ADMIN)) {
            PlayerProfile profile = playerProfileRepository.findByUserId(principal.getId())
                    .orElseThrow(() -> ApiException.forbidden("Only the team captain can upload a logo"));
            if (!profile.getId().equals(team.getCaptainId())) {
                throw ApiException.forbidden("Only the team captain can upload a logo");
            }
        }
        String url = storageService.store("logos", file);
        team.setLogoUrl(url);
        teamRepository.save(team);
        return Map.of("url", url);
    }

    @PostMapping(value = "/admin/gallery", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Upload a home/gallery image (admin)")
    public Map<String, String> uploadGallery(@RequestPart("file") MultipartFile file) {
        validateImage(file);
        return Map.of("url", storageService.store("gallery", file));
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ApiException.badRequest("Only image uploads are supported");
        }
    }
}
