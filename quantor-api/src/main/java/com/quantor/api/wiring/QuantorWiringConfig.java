// File: quantor-api/src/main/java/com/quantor/api/wiring/QuantorWiringConfig.java
package com.quantor.api.wiring;

import com.quantor.api.billing.ForcePaidSubscriptionPort;
import com.quantor.application.guard.TradePermissionGuard;
import com.quantor.application.ports.ConfigPort;
import com.quantor.application.ports.SubscriptionPort;
import com.quantor.application.ports.TradingSessionRepository;
import com.quantor.application.service.CoreTradingOrchestrator;
import com.quantor.application.service.SessionService;
import com.quantor.db.TradingSessionSqlRepository;
import com.quantor.infrastructure.config.FileConfigService;
import com.quantor.infrastructure.db.UserSecretsStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Locale;

@Configuration
public class QuantorWiringConfig {

    @Bean
    public ConfigPort configPort() throws IOException {
        return FileConfigService.defaultFromWorkingDir();
    }

    /**
     * Billing/subscription boundary.
     * P0 rule: FAIL-CLOSED in prod; DEV switch via billing.forcePaid=true.
     */
    @Bean
public SubscriptionPort subscriptionPort(DataSource dataSource) {
    return new com.quantor.db.SubscriptionSqlPort(dataSource);
}



    @Bean
    public SessionService sessionService(ConfigPort config, SubscriptionPort subscriptionPort) {
        return Bootstrap.createSessionService(config, subscriptionPort);
    }

    @Bean
    public UserSecretsStore userSecretsStore() {
        return new UserSecretsStore();
    }

    @Bean
    public TradingSessionRepository tradingSessionRepository() {
        return new TradingSessionSqlRepository();
    }

    @Bean
    public TradePermissionGuard tradePermissionGuard(SubscriptionPort subscriptionPort) {
        return new TradePermissionGuard(subscriptionPort);
    }

    @Bean
    public CoreTradingOrchestrator coreTradingOrchestrator(
            TradePermissionGuard guard,
            TradingSessionRepository repo,
            SessionService sessionService
    ) {
        return CoreTradingOrchestrator.defaults(guard, repo, sessionService);
    }

    private static boolean isTrue(String v) {
        if (v == null) return false;
        String s = v.trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y");
    }
}
