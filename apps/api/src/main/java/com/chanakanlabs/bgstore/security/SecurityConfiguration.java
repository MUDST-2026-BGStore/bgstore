package com.chanakanlabs.bgstore.security;

import com.chanakanlabs.bgstore.clients.ClientOnboardingFilter;
import com.chanakanlabs.bgstore.clients.CurrentUserService;
import com.chanakanlabs.bgstore.identity.CurrentIdentityProvider;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

  @Bean
  @Order(1)
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      CurrentIdentityProvider currentIdentityProvider,
      CurrentUserService currentUsers)
      throws Exception {
    return http.securityMatcher("/api/**")
        .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .requestCache(requestCache -> requestCache.disable())
        .csrf(csrfConfiguration -> csrfConfiguration.spa())
        .addFilterAfter(
            new ClientOnboardingFilter(currentIdentityProvider, currentUsers),
            AuthorizationFilter.class)
        .build();
  }

  @Bean
  SecurityFilterChain browserSecurity(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health/**", "/actuator/info", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new ReturnToRequestFilter(), OAuth2AuthorizationRequestRedirectFilter.class)
        .oauth2Login(
            oauth2 ->
                oauth2.successHandler(
                    (request, response, authentication) -> {
                      HttpSession session = request.getSession(false);
                      String returnTo =
                          session == null
                              ? null
                              : (String)
                                  session.getAttribute(ReturnToRequestFilter.SESSION_ATTRIBUTE);
                      if (session != null) {
                        session.removeAttribute(ReturnToRequestFilter.SESSION_ATTRIBUTE);
                      }
                      response.sendRedirect(
                          returnTo != null && ReturnToRequestFilter.isSafeRelativePath(returnTo)
                              ? returnTo
                              : "/");
                    }))
        .logout(logout -> logout.logoutSuccessUrl("/"))
        .build();
  }
}
