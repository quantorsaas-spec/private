package com.quantor.worker.wiring;

import com.quantor.application.ports.ConfigPort;
import com.quantor.application.service.SessionService;
import com.quantor.infrastructure.config.FileConfigService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class WorkerWiringConfig {

  @Bean
  public ConfigPort configPort() throws IOException {
    return FileConfigService.defaultFromWorkingDir();
  }

  @Bean
  public SessionService sessionService(ConfigPort config) {
    return Bootstrap.createSessionService(config);
  }
}
