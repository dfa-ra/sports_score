package com.studentleague.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleHierarchyTest {

    @Test
    void captainImpliesPlayerAndFan() {
        RoleHierarchy hierarchy = RoleHierarchyImpl.fromHierarchy("""
                ROLE_CAPTAIN > ROLE_PLAYER
                ROLE_PLAYER > ROLE_FAN
                """);

        var reachable = hierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_CAPTAIN"))
        );

        assertThat(reachable).extracting(Object::toString)
                .contains("ROLE_CAPTAIN", "ROLE_PLAYER", "ROLE_FAN");
    }
}
