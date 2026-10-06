package com.studentleague.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Uploaded files keep a random id in the name, so a replaced photo is a new URL.
 * /media/ can stay in the browser for a year. JSON under /api/ is unchanged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MediaCacheControlFilter extends OncePerRequestFilter {

    public static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

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
        HttpServletResponseWrapper wrapped = new HttpServletResponseWrapper(response) {
            @Override
            public void setHeader(String name, String value) {
                super.setHeader(name, rewrite(name, value));
            }

            @Override
            public void addHeader(String name, String value) {
                if (HttpHeaders.CACHE_CONTROL.equalsIgnoreCase(name)) {
                    super.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL);
                    return;
                }
                super.addHeader(name, value);
            }
        };
        wrapped.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL);
        filterChain.doFilter(request, wrapped);
    }

    private static String rewrite(String name, String value) {
        if (HttpHeaders.CACHE_CONTROL.equalsIgnoreCase(name)) {
            return CACHE_CONTROL;
        }
        return value;
    }
}
