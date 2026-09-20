package dev.agiro.fanel.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces household scoping on every {@code /api/v1/households/{id}/**} request: an authenticated
 * household member may only touch its own household. The global admin and API-token clients are
 * not member-bound and pass through. Non-UUID path segments (e.g. {@code import}) are skipped and
 * left to the controllers.
 */
public class HouseholdScopeFilter extends OncePerRequestFilter {
    private static final Pattern HOUSEHOLD_PATH = Pattern.compile("^/api/v1/households/([^/]+)(?:/.*)?$");
    private static final String FORBIDDEN_BODY =
            "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                    + "\"detail\":\"Not allowed to access this household\"}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Matcher matcher = HOUSEHOLD_PATH.matcher(request.getRequestURI());
        if (matcher.matches() && isMemberOutOfScope(matcher.group(1))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(FORBIDDEN_BODY);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isMemberOutOfScope(String segment) {
        UUID requested;
        try {
            requested = UUID.fromString(segment);
        } catch (IllegalArgumentException e) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof MemberPrincipal member)) {
            return false;
        }
        return !member.householdId().equals(requested);
    }
}
