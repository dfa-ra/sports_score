package com.studentleague.users.controller;

import com.studentleague.auth.dto.UserResponse;
import com.studentleague.common.dto.PageResponse;
import com.studentleague.users.domain.Role;
import com.studentleague.users.dto.RequestRoleRequest;
import com.studentleague.users.dto.RoleAssignmentResponse;
import com.studentleague.users.dto.UpdateUserRequest;
import com.studentleague.users.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Users")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/users")
    @Operation(summary = "List users")
    public PageResponse<UserResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PageResponse.from(userAdminService.listUsers(pageable));
    }

    @GetMapping("/role-requests")
    @Operation(summary = "Pending role requests")
    public List<RoleAssignmentResponse> pendingRoles() {
        return userAdminService.pendingRoles();
    }

    @PatchMapping("/users/{id}")
    @Operation(summary = "Update user primary role or enabled flag")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userAdminService.updateUser(id, request);
    }

    @PostMapping("/users/{id}/roles")
    @Operation(summary = "Approve an additional role or change a wrong one")
    public UserResponse addRole(@PathVariable UUID id, @Valid @RequestBody RequestRoleRequest request) {
        return userAdminService.addOrChangeRole(id, request);
    }

    @PostMapping("/users/{id}/roles/{role}/approve")
    @Operation(summary = "Approve a requested role")
    public UserResponse approve(@PathVariable UUID id, @PathVariable Role role) {
        return userAdminService.approveRole(id, role);
    }

    @PostMapping("/users/{id}/roles/{role}/reject")
    @Operation(summary = "Reject a requested role")
    public UserResponse reject(
            @PathVariable UUID id,
            @PathVariable Role role,
            @RequestParam(required = false) String note
    ) {
        return userAdminService.rejectRole(id, role, note);
    }
}
