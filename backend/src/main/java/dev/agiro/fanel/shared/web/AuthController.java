package dev.agiro.fanel.shared.web;

import dev.agiro.fanel.shared.config.OidcProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Lists the external login providers available on this instance (empty when OIDC is off). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final OidcProperties oidc;
    private final ObjectProvider<ClientRegistrationRepository> registrations;

    public AuthController(OidcProperties oidc, ObjectProvider<ClientRegistrationRepository> registrations) {
        this.oidc = oidc;
        this.registrations = registrations;
    }

    @GetMapping("/providers")
    public List<Map<String, String>> providers() {
        if (registrations.getIfAvailable() == null) return List.of();
        return List.of(Map.of(
                "id", "sso",
                "name", oidc.name() != null ? oidc.name() : "SSO",
                "loginUrl", "/oauth2/authorization/sso"));
    }
}
