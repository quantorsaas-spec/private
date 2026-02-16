// File: quantor-worker/src/main/java/com/quantor/worker/config/SessionServiceConfig.java
package com.quantor.worker.config;

import com.quantor.application.execution.JobScheduler;
import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.NotifierPort;
import com.quantor.application.service.PipelineFactory;
import com.quantor.application.service.SessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("debug-session") // ВКЛЮЧАЕТСЯ ТОЛЬКО ЯВНО
public class SessionServiceConfig {

    /**
     * Debug-only SessionService with ConfigPort injected.
     * Enables quantor.runtime.debugStopTrace=true
     *
     * To enable:
     *   SPRING_PROFILES_ACTIVE=debug-session
     */
    @Bean
    @Primary
    public SessionService sessionService(
            PipelineFactory pipelineFactory,
            JobScheduler scheduler,
            NotifierPort notifier,
            ConfigPort config
    ) {
        return new SessionService(pipelineFactory, scheduler, notifier, config);
    }
}
