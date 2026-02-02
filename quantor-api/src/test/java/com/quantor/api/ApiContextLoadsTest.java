package com.quantor.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Minimal smoke test.
 * If this fails, the service is not starting in a controlled test environment.
 */
@SpringBootTest(properties = {"spring.profiles.active=test"})
class ApiContextLoadsTest {

  @Test
  void contextLoads() {
    // no-op
  }
}
