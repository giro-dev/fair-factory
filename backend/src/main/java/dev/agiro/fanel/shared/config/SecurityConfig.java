package dev.agiro.fanel.shared.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import dev.agiro.fanel.shared.security.HouseholdScopeFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, OncePerRequestFilter apiTokenFilter,
                                            HouseholdScopeFilter householdScopeFilter,
                                            AuthenticationManager authenticationManager,
                                            ObjectProvider<ClientRegistrationRepository> registrations,
                                            ObjectProvider<OidcUserService> memberOidcUserService) throws Exception {
        boolean oidcEnabled = registrations.getIfAvailable() != null;
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(
                        oidcEnabled ? SessionCreationPolicy.IF_REQUIRED : SessionCreationPolicy.STATELESS))
                .authenticationManager(authenticationManager)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/assets/**", "/manifest.webmanifest", "/sw.js",
                                "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers("/api/v1/setup", "/api/v1/auth/providers").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**", "/login").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/actuator/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(apiTokenFilter, BasicAuthenticationFilter.class)
                .addFilterAfter(householdScopeFilter, BasicAuthenticationFilter.class);
        if (oidcEnabled) {
            http.oauth2Login(oauth -> oauth
                            .loginPage("/login")
                            .defaultSuccessUrl("/", true)
                            .failureUrl("/?sso=failed")
                            .userInfoEndpoint(info -> info.oidcUserService(memberOidcUserService.getObject())))
                    .logout(logout -> logout
                            .logoutUrl("/api/v1/auth/logout")
                            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                    .exceptionHandling(handling -> handling.defaultAuthenticationEntryPointFor(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                            PathPatternRequestMatcher.withDefaults().matcher("/api/**")));
        }
        return http.build();
    }

    @Bean
    HouseholdScopeFilter householdScopeFilter() {
        return new HouseholdScopeFilter();
    }

    @Bean
    OncePerRequestFilter apiTokenFilter(@Value("${fanel.api.token:}") String apiToken) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String header = request.getHeader("Authorization");
                if (!apiToken.isBlank() && header != null && header.equals("Bearer " + apiToken)) {
                    var auth = new UsernamePasswordAuthenticationToken("api-client", null,
                            List.of(new SimpleGrantedAuthority("ROLE_API")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
                chain.doFilter(request, response);
            }
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    InMemoryUserDetailsManager adminUserDetailsService(@Value("${fanel.admin.user:admin}") String username,
                                                       @Value("${fanel.admin.password:admin}") String password) {
        return new InMemoryUserDetailsManager(User.withUsername(username).password("{noop}" + password).roles("ADMIN").build());
    }

    /**
     * Combines the single global admin account with the per-household adult accounts
     * ({@code MemberUserDetailsService}, wired here only by its {@link UserDetailsService} type
     * so this module doesn't depend on `household`'s internals).
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService adminUserDetailsService,
                                                UserDetailsService memberUserDetailsService,
                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider adminProvider = new DaoAuthenticationProvider(adminUserDetailsService);
        adminProvider.setPasswordEncoder(passwordEncoder);
        DaoAuthenticationProvider memberProvider = new DaoAuthenticationProvider(memberUserDetailsService);
        memberProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(adminProvider, memberProvider);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${fanel.cors.origins:http://localhost:5173}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
