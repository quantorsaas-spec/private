package com.quantor.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
@Profile("dev")
public class DevActuatorSecurityConfig {

  /**
   * Dev-only: allow actuator endpoints without auth to ease local development.
   * This chain matches only /actuator/**. Non-actuator security is configured elsewhere.
   */
  @Bean
  @Order(0)
  SecurityFilterChain actuatorDevSecurity(HttpSecurity http) throws Exception {
    return http
        .securityMatcher("/actuator/**")
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .build();
  }
}
