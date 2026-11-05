package com.studentleague.security;

import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.users.domain.Role;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captain of this team, or an admin. Runs in the security filter, before body validation.
 */
@Component
public class TeamManageAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Pattern TEAM_ID = Pattern.compile(
            "/api/v1/(?:uploads/)?teams/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
    );

    private final TeamRepository teamRepository;
    private final PlayerProfileRepository playerProfileRepository;

    public TeamManageAuthorizationManager(
            TeamRepository teamRepository,
            PlayerProfileRepository playerProfileRepository
    ) {
        this.teamRepository = teamRepository;
        this.playerProfileRepository = playerProfileRepository;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return new AuthorizationDecision(false);
        }
        if (principal.hasRole(Role.ADMIN)) {
            return new AuthorizationDecision(true);
        }
        Matcher matcher = TEAM_ID.matcher(path(context));
        if (!matcher.find()) {
            return new AuthorizationDecision(false);
        }
        UUID teamId = UUID.fromString(matcher.group(1));
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null || !principal.hasRole(Role.CAPTAIN)) {
            return new AuthorizationDecision(false);
        }
        PlayerProfile profile = playerProfileRepository.findByUserId(principal.getId()).orElse(null);
        return new AuthorizationDecision(profile != null && profile.getId().equals(team.getCaptainId()));
    }

    private static String path(RequestAuthorizationContext context) {
        String uri = context.getRequest().getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = context.getRequest().getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
