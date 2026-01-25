package com.quantor.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  /**
   * Public endpoints chain (NO resource-server):
   * - auth entrypoints: register/login/refresh (must be reachable without Bearer token)
   * - health + error
   * - billing webhooks (POST)
   */
  @Bean
  @Order(2)
  SecurityFilterChain publicApiChain(HttpSecurity http) throws Exception {
    return http
        .securityMatcher(
            "/api/v1/health",
            "/error",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/billing/lemonsqueezy/webhook",
            "/api/v1/billing/lemon/webhook"
        )
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/api/v1/health", "/error").permitAll()
            .requestMatchers(HttpMethod.POST,
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh"
            ).permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/billing/lemonsqueezy/webhook").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/billing/lemon/webhook").permitAll()
            .anyRequest().permitAll()
        )
        .build();
  }

  /**
   * Secured API chain (JWT required):
   * - everything else under /api/**
   */
  @Bean
  @Order(3)
  SecurityFilterChain securedApiChain(HttpSecurity http) throws Exception {
    return http
        .securityMatcher("/api/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth -> oauth
            .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtRoleConverter()))
        )
        .build();
  }
}
