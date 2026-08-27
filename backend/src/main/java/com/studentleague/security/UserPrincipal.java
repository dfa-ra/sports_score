package com.studentleague.security;

import com.studentleague.users.domain.Role;
import com.studentleague.users.domain.RoleStatus;
import com.studentleague.users.entity.User;
import com.studentleague.users.entity.UserRoleAssignment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final Set<Role> approvedRoles;
    private final boolean enabled;

    public UserPrincipal(UUID id, String email, String passwordHash, Role role, boolean enabled) {
        this(id, email, passwordHash, role, role == null ? Set.of() : Set.of(role), enabled);
    }

    public UserPrincipal(
            UUID id,
            String email,
            String passwordHash,
            Role role,
            Set<Role> approvedRoles,
            boolean enabled
    ) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.approvedRoles = approvedRoles == null || approvedRoles.isEmpty()
                ? (role == null ? Set.of() : EnumSet.of(role))
                : EnumSet.copyOf(approvedRoles);
        this.enabled = enabled;
    }

    public static UserPrincipal from(User user) {
        return from(user, List.of());
    }

    public static UserPrincipal from(User user, Collection<UserRoleAssignment> assignments) {
        Set<Role> approved = assignments == null
                ? new LinkedHashSet<>()
                : assignments.stream()
                .filter(a -> a.getStatus() == RoleStatus.APPROVED)
                .map(UserRoleAssignment::getRole)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (user.getRole() != null) {
            approved.add(user.getRole());
        }
        Role primary = user.getRole() != null ? user.getRole() : Role.FAN;
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                primary,
                approved,
                user.isEnabled()
        );
    }

    public UUID getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public Set<Role> getApprovedRoles() {
        return approvedRoles;
    }

    public boolean hasRole(Role candidate) {
        return approvedRoles.contains(candidate) || role == candidate;
    }

    public boolean hasAnyRole(Role... candidates) {
        for (Role candidate : candidates) {
            if (hasRole(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return approvedRoles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
