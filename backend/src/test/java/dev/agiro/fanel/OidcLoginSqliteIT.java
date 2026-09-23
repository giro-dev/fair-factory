package dev.agiro.fanel;

import com.jayway.jsonpath.JsonPath;
import dev.agiro.fanel.household.domain.MemberOidcUserService;
import dev.agiro.fanel.shared.security.MemberPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OIDC login against a hand-built client registration (no discovery): the security chain picks up
 * the {@link ClientRegistrationRepository} bean and {@code MemberOidcUserService} maps identities
 * to household members.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sqlite")
@Import(OidcLoginSqliteIT.OidcTestConfig.class)
class OidcLoginSqliteIT {
    @Autowired MockMvc mvc;
    @Autowired MemberOidcUserService memberOidcUserService;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        registry.add("FANEL_SQLITE_PATH", () -> tempDir.resolve("fanel.db").toString());
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("fanel.db"));
        registry.add("fanel.oidc.name", () -> "TestSSO");
    }

    @TestConfiguration
    static class OidcTestConfig {
        @Bean
        @Primary
        ClientRegistrationRepository testClientRegistrations() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("sso")
                    .clientId("test-client")
                    .clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri("http://sso.invalid/auth")
                    .tokenUri("http://sso.invalid/token")
                    .jwkSetUri("http://sso.invalid/jwks")
                    .issuerUri("http://sso.invalid")
                    .scope("openid")
                    .build());
        }
    }

    private OidcUser fakeUser(String subject, String preferredUsername) {
        OidcIdToken token = OidcIdToken.withTokenValue("t")
                .claim("sub", subject)
                .claim("preferred_username", preferredUsername)
                .claim("iss", "http://sso.invalid")
                .claim("aud", List.of("test-client"))
                .build();
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_OIDC_USER")), token);
    }

    private String createMemberWithUsername(String username) throws Exception {
        String householdJson = mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Llar\",\"locale\":\"ca\",\"timezone\":\"Europe/Madrid\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String householdId = JsonPath.read(householdJson, "$.id");
        String memberJson = mvc.perform(post("/api/v1/households/" + householdId + "/members")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anna\",\"role\":\"ADULT\",\"color\":\"#123456\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String memberId = JsonPath.read(memberJson, "$.id");
        mvc.perform(put("/api/v1/households/" + householdId + "/members/" + memberId + "/credentials")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());
        return memberId;
    }

    @Test
    void listsTheConfiguredProvider() throws Exception {
        mvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sso"))
                .andExpect(jsonPath("$[0].name").value("TestSSO"))
                .andExpect(jsonPath("$[0].loginUrl").value("/oauth2/authorization/sso"));
    }

    @Test
    void authorizationEndpointRedirectsToTheProvider() throws Exception {
        mvc.perform(get("/oauth2/authorization/sso"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("http://sso.invalid/auth")));
    }

    @Test
    void oidcLoginResolvesTheMemberAndScopesRequests() throws Exception {
        String memberId = createMemberWithUsername("anna");
        OidcUser principal = memberOidcUserService.map(fakeUser("sub-1", "anna"));
        assertThat(((MemberPrincipal) principal).memberId().toString()).isEqualTo(memberId);

        mvc.perform(get("/api/v1/me").with(oidcLogin().oidcUser(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("MEMBER"))
                .andExpect(jsonPath("$.member.username").value("anna"))
                .andExpect(jsonPath("$.member.id").value(memberId));
    }

    @Test
    void unknownUsernameIsRejected() {
        assertThatThrownBy(() -> memberOidcUserService.map(fakeUser("sub-x", "ghost")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo("unknown_member"));
    }

    @Test
    void subjectIsPinnedAfterFirstLoginAndUsedForLaterMatches() throws Exception {
        createMemberWithUsername("anna2");
        OidcUser first = memberOidcUserService.map(fakeUser("sub-pinned", "anna2"));
        String memberId = ((MemberPrincipal) first).memberId().toString();

        // A later login with a different username claim but the same subject still resolves.
        OidcUser second = memberOidcUserService.map(fakeUser("sub-pinned", "renamed"));
        assertThat(((MemberPrincipal) second).memberId().toString()).isEqualTo(memberId);
    }

    @Test
    void basicAuthStillCreatesNoSessionCookie() throws Exception {
        mvc.perform(get("/api/v1/me").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void unauthenticatedApiCallsStay401NotRedirect() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }
}
