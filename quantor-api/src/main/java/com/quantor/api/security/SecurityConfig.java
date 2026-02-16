package com.quantor.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Quantor API.
 *
 * Design principles:
 * - Stateless (JWT only, no sessions)
 * - Fail-closed for secured APIs
 * - Explicit public endpoints (auth, health, billing webhooks)
 */
@Configuration
public class SecurityConfig {

    /**
     * Public endpoints (NO JWT, NO resource server):
     * - Auth: register / login / refresh
     * - Health & error
     * - Billing webhooks (Lemon Squeezy)
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
                "/api/v1/billing/lemonsqueezy/webhook"
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/health", "/error").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/billing/lemonsqueezy/webhook"
                ).permitAll()
                .anyRequest().denyAll() // fail-closed
            )
            .build();
    }

    /**
     * Secured API (JWT required):
     * - All remaining /api/** endpoints
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
