package com.chanakanlabs.theparlor.identity.internal.security;

import jakarta.servlet.DispatcherType;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Browser authentication lives on the server; anonymous API requests receive 401, never login HTML.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  ClientRegistrationRepository clientRegistrations(
      @Value("${theparlor.auth.issuer}") String issuer,
      @Value("${theparlor.auth.backchannel-issuer}") String backchannel,
      @Value("${theparlor.auth.client-secret}") String secret,
      @Value("${theparlor.auth.public-origin}") String origin) {
    if (secret.isBlank()) throw new IllegalArgumentException("Set THEPARLOR_OIDC_CLIENT_SECRET");
    URI publicOrigin = URI.create(origin);
    if (publicOrigin.getHost() == null
        || !(publicOrigin.getScheme().equals("http") || publicOrigin.getScheme().equals("https"))
        || publicOrigin.getRawQuery() != null
        || publicOrigin.getRawFragment() != null
        || publicOrigin.getUserInfo() != null
        || !(publicOrigin.getPath().isEmpty() || publicOrigin.getPath().equals("/")))
      throw new IllegalArgumentException("THEPARLOR_PUBLIC_ORIGIN must be an HTTP(S) origin");
    // Explicit endpoints avoid browser-facing discovery/DNS during startup in Compose/Kubernetes.
    var registration =
        ClientRegistration.withRegistrationId("keycloak")
            .clientId("theparlor-bff")
            .clientSecret(secret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(origin.replaceAll("/+$", "") + "/api/auth/callback/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri(issuer + "/protocol/openid-connect/auth")
            .tokenUri(backchannel + "/protocol/openid-connect/token")
            .jwkSetUri(backchannel + "/protocol/openid-connect/certs")
            .userInfoUri(backchannel + "/protocol/openid-connect/userinfo")
            .userNameAttributeName("sub")
            .issuerUri(issuer)
            .providerConfigurationMetadata(
                Map.of("end_session_endpoint", issuer + "/protocol/openid-connect/logout"))
            .clientName("The Parlor")
            .build();
    return new InMemoryClientRegistrationRepository(registration);
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ClientRegistrationRepository registrations,
      @Value("${theparlor.auth.public-origin}") String origin)
      throws Exception {
    String publicOrigin = origin.replaceAll("/+$", "");
    var authorization =
        new DefaultOAuth2AuthorizationRequestResolver(registrations, "/api/auth/login");
    authorization.setAuthorizationRequestCustomizer(
        OAuth2AuthorizationRequestCustomizers.withPkce());
    var logout = new OidcClientInitiatedLogoutSuccessHandler(registrations);
    logout.setPostLogoutRedirectUri(publicOrigin + "/");
    logout.setDefaultTargetUrl(publicOrigin + "/");

    http
        // HttpSession CSRF repository + masked tokens from /api/auth/csrf; no readable auth cookie.
        .csrf(csrf -> {})
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Allow Spring Boot to handle internal error dispatches.
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/hello",
                        "/api/auth/session",
                        "/api/auth/csrf",
                        "/api/auth/login/keycloak",
                        "/api/auth/callback/keycloak")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/logout")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/health",
                        "/info")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/games",
                        "/api/v1/games/{gameId}",
                        "/api/v1/games/{gameId}/editions",
                        "/api/v1/games/{gameId}/editions/{editionId}",
                        "/api/v1/categories",
                        "/api/v1/categories/{categoryId}",
                        "/api/v1/mechanics",
                        "/api/v1/mechanics/{mechanicId}",
                        "/api/v1/branches",
                        "/api/v1/branches/{branchId}",
                        "/api/v1/branches/{branchId}/availability",
                        "/api/v1/branches/{branchId}/tables",
                        "/api/v1/branches/{branchId}/tables/{tableId}",
                        "/api/v1/branches/{branchId}/games",
                        "/api/v1/branches/{branchId}/games/{gameId}")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .oauth2Login(
            oauth ->
                oauth
                    .authorizationEndpoint(
                        endpoint -> endpoint.authorizationRequestResolver(authorization))
                    .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/auth/callback/*"))
                    .authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
                    .userInfoEndpoint(
                        endpoint -> endpoint.userAuthoritiesMapper(realmAuthorities()))
                    .defaultSuccessUrl(publicOrigin + "/login", true)
                    .failureUrl(publicOrigin + "/?login_error=1"))
        .logout(
            config ->
                config
                    .logoutUrl("/api/auth/logout")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("THEPARLOR_SESSION")
                    .logoutSuccessHandler(logout));
    return http.build();
  }

  /** Realm roles must be present in the signed ID token, never supplied by the browser. */
  static GrantedAuthoritiesMapper realmAuthorities() {
    return authorities -> {
      Collection<GrantedAuthority> mapped = new HashSet<>(authorities);
      for (GrantedAuthority authority : authorities) {
        if (!(authority instanceof OidcUserAuthority oidc)) continue;
        Object claim = oidc.getIdToken().getClaims().get("realm_access");
        if (!(claim instanceof Map<?, ?> realm) || !(realm.get("roles") instanceof List<?> roles))
          continue;
        roles.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
            .forEach(mapped::add);
      }
      return mapped;
    };
  }
}
