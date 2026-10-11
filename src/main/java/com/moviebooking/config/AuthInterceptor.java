package com.moviebooking.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Role-based access control for the REST API.
 * - /api/admin/**    -> session must belong to an ADMIN
 * - /api/customer/** -> session must belong to a CUSTOMER
 * Login endpoints live under /api/auth/** and are not intercepted.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = request.getRequestURI();
        String requiredRole = path.startsWith("/api/admin/") ? "ADMIN" : "CUSTOMER";

        HttpSession session = request.getSession(false);
        String currentRole = session == null ? null : (String) session.getAttribute(SessionKeys.ROLE);

        if (requiredRole.equals(currentRole)) {
            return true;
        }
        boolean loggedIn = currentRole != null;
        response.setStatus(loggedIn ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(loggedIn
                ? "{\"message\":\"You do not have access to this area\"}"
                : "{\"message\":\"Please log in first\"}");
        return false;
    }
}
