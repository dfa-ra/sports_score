package com.studentleague.notifications.controller;

import com.studentleague.notifications.dto.RegisterDeviceTokenRequest;
import com.studentleague.notifications.service.DeviceTokenService;
import com.studentleague.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
@SecurityRequirement(name = "bearerAuth")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    @PostMapping("/device-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Register a device token for push notifications")
    public void register(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RegisterDeviceTokenRequest request
    ) {
        deviceTokenService.register(principal.getId(), request);
    }

    @DeleteMapping("/device-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unregister a device token")
    public void unregister(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String token
    ) {
        deviceTokenService.unregister(principal.getId(), token);
    }
}
