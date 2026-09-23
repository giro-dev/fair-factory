package dev.agiro.fanel.household.domain;

import dev.agiro.fanel.household.infra.MemberRepository;
import dev.agiro.fanel.shared.config.OidcProperties;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Maps an OIDC identity to a household member: first by the pinned {@code oidc_subject}, then by
 * the configured username claim (falling back to {@code email}); a successful username match pins
 * the subject so later logins survive provider-side username changes. Unknown users are rejected
 * with {@code unknown_member}.
 *
 * <p>Exposed as {@code OidcUserService} so {@code shared} can wire it into Spring Security by type
 * without importing this module's internals.
 */
@Service
public class MemberOidcUserService extends OidcUserService {
    private final MemberRepository members;
    private final OidcProperties oidc;

    public MemberOidcUserService(MemberRepository members, OidcProperties oidc) {
        this.members = members;
        this.oidc = oidc;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        return map(super.loadUser(userRequest));
    }

    /** Maps a resolved OIDC identity to a member; exposed for tests that skip the token exchange. */
    @Transactional
    public OidcUser map(OidcUser user) {
        Member member = members.findByOidcSubject(user.getSubject())
                .orElseGet(() -> matchByUsername(user));
        if (member.getOidcSubject() == null) {
            member.setOidcSubject(user.getSubject());
            members.save(member);
        } else if (!member.getOidcSubject().equals(user.getSubject())) {
            // The username matched a member already bound to a different identity.
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unknown_member", "Member is bound to another identity", null));
        }
        return new MemberOidcUser(user, member);
    }

    private Member matchByUsername(OidcUser user) {
        String username = user.getClaimAsString(oidc.usernameClaim());
        if (!StringUtils.hasText(username)) username = user.getEmail();
        if (!StringUtils.hasText(username)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_username_claim", "No username claim in the OIDC profile", null));
        }
        String resolved = username;
        return members.findByUsername(resolved)
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        new OAuth2Error("unknown_member", "No member with username " + resolved, null)));
    }
}
