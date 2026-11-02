package com.studentleague.gallery;

import com.studentleague.gallery.service.GalleryService;
import com.studentleague.security.UserPrincipal;
import com.studentleague.users.domain.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * A disabled gallery photo stays on disk for the admin screen, but an anonymous
 * GET of that /media path must not receive the bytes.
 */
@Component
public class DisabledGalleryMediaFilter extends OncePerRequestFilter {

    private final GalleryService galleryService;

    public DisabledGalleryMediaFilter(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/media/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isAdmin() || !galleryService.isHiddenFromAnonymous(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    private static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        return principal.hasRole(Role.ADMIN);
    }
}
