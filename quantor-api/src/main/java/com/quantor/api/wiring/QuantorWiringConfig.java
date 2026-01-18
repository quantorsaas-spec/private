package com.quantor.api.wiring;

import com.quantor.application.ports.ConfigPort;
import com.quantor.application.service.SessionService;
import com.quantor.infrastructure.config.FileConfigService;
import com.quantor.infrastructure.db.UserSecretsStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Composition root for Quantor.
 *
 * All production wiring must live in quantor-api.
 */
@Configuration
public class QuantorWiringConfig {

    @Bean
    public ConfigPort configPort() throws IOException {
        // Loads from ./config/config.properties and secrets.* using the existing infra adapter
        return FileConfigService.defaultFromWorkingDir();
    }

    @Bean
    public SessionService sessionService(ConfigPort config) {
        return Bootstrap.createSessionService(config);
    }

    @Bean
    public UserSecretsStore userSecretsStore() {
        return new UserSecretsStore();
    }
}
