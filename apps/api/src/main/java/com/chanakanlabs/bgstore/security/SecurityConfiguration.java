package com.chanakanlabs.bgstore.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

  @Bean
  @Order(1)
  SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrf.setCookieName("bgstore-csrf");
    csrf.setHeaderName("X-BGStore-CSRF");

    return http.securityMatcher("/api/**")
        .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .requestCache(requestCache -> requestCache.disable())
        .csrf(csrfConfiguration -> csrfConfiguration.csrfTokenRepository(csrf))
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
        .oauth2Login(withDefaults())
        .logout(logout -> logout.logoutSuccessUrl("/"))
        .build();
  }
}
