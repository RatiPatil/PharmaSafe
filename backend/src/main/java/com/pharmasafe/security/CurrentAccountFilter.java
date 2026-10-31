package com.pharmasafe.security;

import com.pharmasafe.repository.UserRepository;
import com.pharmasafe.service.AuthenticationService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Invalidates application-issued sessions after role/password/status changes. */
public class CurrentAccountFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public CurrentAccountFilter(UserRepository users) { this.users = users; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().equals("/api/auth/login")) { chain.doFilter(request,response); return; }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        var session = request.getSession(false);
        // Our login service issues a String principal. Other authentication providers retain their own lifecycle.
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && session != null && (auth.getPrincipal() instanceof String
                    || session.getAttribute("PHARMASAFE_AUTH_VERSION") != null)) {
            var current = users.findByUsername(auth.getName());
            Object version = session.getAttribute("PHARMASAFE_AUTH_VERSION");
            boolean valid = current.isPresent() && current.get().isEnabled()
                    && (!AuthenticationService.ACCOUNT_LOCKOUT_ENABLED || !current.get().isLocked())
                    && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(current.get().getRole()))
                    && version instanceof Number && ((Number) version).longValue() == current.get().getAuthVersion();
            if (!valid) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Your session has expired or your account access changed. Sign in again.\",\"code\":\"SESSION_EXPIRED\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
