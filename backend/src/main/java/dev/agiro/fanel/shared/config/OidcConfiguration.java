package dev.agiro.fanel.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.StringUtils;

/**
 * Registers the OIDC client only when {@code fanel.oidc.issuer} is set. Note that
 * {@link ClientRegistrations#fromIssuerLocation} performs an HTTP discovery call, so this bean
 * must not exist in deployments/tests without a real issuer.
 */
@Configuration
@EnableConfigurationProperties(OidcProperties.class)
public class OidcConfiguration {

    @Bean
    @Conditional(OidcConfiguration.OidcConfigured.class)
    ClientRegistrationRepository clientRegistrationRepository(OidcProperties props) {
        ClientRegistration registration = ClientRegistrations.fromIssuerLocation(props.issuer())
                .registrationId("sso")
                .clientId(props.clientId())
                .clientSecret(props.clientSecret())
                .scope(props.scopes() != null ? props.scopes().split(",") : new String[]{"openid"})
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    /** Enabled when {@code fanel.oidc.issuer} has text (an empty placeholder counts as disabled). */
    static class OidcConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("fanel.oidc.issuer"));
        }
    }
}
