package com.quantor.api.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Maps JWT claim "role" (e.g. "USER", "ADMIN") to Spring Security authorities ("ROLE_USER", "ROLE_ADMIN").
 */
public final class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
    String role = jwt.getClaimAsString("role");
    if (role != null && !role.isBlank()) {
      authorities.add(new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()));
    } else {
      // fallback
      authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
    }
    return new JwtAuthenticationToken(jwt, authorities);
  }
}
