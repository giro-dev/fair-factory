package dev.agiro.fanel.household.domain;

import dev.agiro.fanel.shared.security.AccessRole;
import dev.agiro.fanel.shared.security.MemberPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An {@link OidcUser} that is also a {@link MemberPrincipal}, so the existing household scoping
 * ({@code HouseholdScopeFilter}, {@code CurrentAccess}) applies to SSO logins unchanged.
 */
class MemberOidcUser implements OidcUser, MemberPrincipal {
    private final OidcUser user;
    private final UUID memberId;
    private final UUID householdId;
    private final AccessRole role;

    MemberOidcUser(OidcUser user, Member member) {
        this.user = user;
        this.memberId = member.getId();
        this.householdId = member.getHousehold().getId();
        this.role = AccessRole.valueOf(member.getRole().name());
    }

    @Override
    public UUID memberId() { return memberId; }
    @Override
    public UUID householdId() { return householdId; }
    @Override
    public AccessRole role() { return role; }

    @Override
    public Map<String, Object> getClaims() { return user.getClaims(); }
    @Override
    public OidcUserInfo getUserInfo() { return user.getUserInfo(); }
    @Override
    public OidcIdToken getIdToken() { return user.getIdToken(); }
    @Override
    public Map<String, Object> getAttributes() { return user.getAttributes(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        return authorities;
    }

    @Override
    public String getName() { return user.getName(); }
}
