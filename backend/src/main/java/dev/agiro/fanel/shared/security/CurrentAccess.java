package dev.agiro.fanel.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Convenience facade over the current {@link Authentication} for authorization checks: is the
 * caller the global admin or a household member with role ADMIN (full access), or a scoped
 * ADULT/CHILD member (see {@link MemberPrincipal}).
 */
@Component
public class CurrentAccess {
    public boolean isAdmin() {
        return hasAuthority("ROLE_ADMIN");
    }

    /** Instance-wide API token client ({@code Bearer fanel.api.token}); not bound to a household. */
    public boolean isApiClient() {
        return hasAuthority("ROLE_API");
    }

    /**
     * The global bootstrap admin (env-configured), as opposed to a household member whose role is
     * ADMIN: members are always bound to a single household.
     */
    public boolean isGlobalAdmin() {
        return isAdmin() && householdId().isEmpty();
    }

    /** Callers allowed to operate across households: global admin, household-admin members, API token. */
    public boolean hasFullAccess() {
        return isAdmin() || isApiClient();
    }

    /** Callers allowed to create households and see them all: the global admin or the API token. */
    public boolean hasGlobalAccess() {
        return isGlobalAdmin() || isApiClient();
    }

    public boolean isAdult() {
        return principal().map(MemberPrincipal::role).map(AccessRole.ADULT::equals).orElse(false);
    }

    public boolean isChild() {
        return principal().map(MemberPrincipal::role).map(AccessRole.CHILD::equals).orElse(false);
    }

    public Optional<UUID> memberId() {
        return principal().map(MemberPrincipal::memberId);
    }

    public Optional<UUID> householdId() {
        return principal().map(MemberPrincipal::householdId);
    }

    private Optional<MemberPrincipal> principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof MemberPrincipal member)) {
            return Optional.empty();
        }
        return Optional.of(member);
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals(authority));
    }
}
