package com.studentleague.common.web;

import com.studentleague.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class PageableBoundsInterceptor implements HandlerInterceptor {

    public static final int MAX_PAGE_SIZE = 500;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        rejectNegative(request.getParameter("page"), "Номер страницы не может быть отрицательным");
        rejectSize(request.getParameter("size"));
        return true;
    }

    private static void rejectNegative(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            if (Integer.parseInt(raw.trim()) < 0) {
                throw ApiException.badRequest(message);
            }
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("Некорректный номер страницы");
        }
    }

    private static void rejectSize(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        int size;
        try {
            size = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("Некорректный размер страницы");
        }
        if (size < 1) {
            throw ApiException.badRequest("Размер страницы должен быть больше нуля");
        }
        if (size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("Размер страницы не больше " + MAX_PAGE_SIZE);
        }
    }
}
