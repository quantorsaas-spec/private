package com.quantor.api.security;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.JWSAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtBeans {

  private final Environment env;

  public JwtBeans(Environment env) {
    this.env = env;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  @ConditionalOnMissingBean(name = "jwtEncoder")
  public JwtEncoder jwtEncoder(@Value("${quantor.auth.jwtSecret:}") String secret) {
    byte[] keyBytes = normalizeSecret(secret);
    var jwk = new OctetSequenceKey.Builder(keyBytes)
        .algorithm(JWSAlgorithm.HS256)
        .keyID("quantor-hs256")
        .build();

    JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
    return new NimbusJwtEncoder(jwkSource);
  }

  @Bean
  @ConditionalOnMissingBean(name = "jwtDecoder")
  public JwtDecoder jwtDecoder(@Value("${quantor.auth.jwtSecret:}") String secret) {
    var key = new SecretKeySpec(normalizeSecret(secret), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  }

  /**
   * We accept any configured secret string and derive a fixed 32-byte key (HS256 friendly).
   *
   * Why: Spring's "${ENV:default}" does NOT fall back when ENV is set but blank. In local/dev
   * setups it's easy to end up with an empty/short secret -> runtime 500s on /login and /register.
   */
  private byte[] normalizeSecret(String secret) {
    String s = (secret == null) ? "" : secret.trim();
    if (s.isEmpty()) {
      // Dev convenience: work out of the box.
      if (env.acceptsProfiles(org.springframework.core.env.Profiles.of("dev"))) {
        s = "dev-secret-change-me";
      } else {
        throw new IllegalStateException("quantor.auth.jwtSecret is empty. Set QUANTOR_JWT_SECRET.");
      }
    }

    // Derive 32 bytes deterministically.
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return md.digest(s.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      // Should never happen on a normal JRE.
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
