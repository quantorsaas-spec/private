package com.quantor.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SmokeHealthTest {
  @LocalServerPort int port;
  @Autowired TestRestTemplate rest;

  @Test void healthIsUp() {
    var r = rest.getForEntity("http://localhost:" + port + "/actuator/health", String.class);
    assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
  }
}
