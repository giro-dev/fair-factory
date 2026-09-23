package dev.agiro.fanel.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional external SSO provider configuration. Empty {@code issuer} means OIDC is disabled and
 * the application keeps its session-less Basic/Bearer behaviour.
 */
@ConfigurationProperties(prefix = "fanel.oidc")
public record OidcProperties(String issuer, String clientId, String clientSecret, String name,
                             String usernameClaim, String scopes) {
}
