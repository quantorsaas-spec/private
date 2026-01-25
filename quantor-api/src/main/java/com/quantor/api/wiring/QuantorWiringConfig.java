package com.quantor.api.wiring;

import com.quantor.application.ports.ConfigPort;
import com.quantor.application.service.SessionService;
import com.quantor.application.guard.TradePermissionGuard;
import com.quantor.application.ports.SubscriptionPort;
import com.quantor.application.ports.TradingSessionRepository;
import com.quantor.application.service.CoreTradingOrchestrator;
import com.quantor.db.TradingSessionSqlRepository;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
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


    // --- P0 SaaS Core wiring ---

    @Bean
    public TradingSessionRepository tradingSessionRepository() {
        return new TradingSessionSqlRepository();
    }

    @Bean
    public SubscriptionPort subscriptionPort(DataSource dataSource) {
        return new PostgresSubscriptionPort(new JdbcTemplate(dataSource));
    }
@Bean
    public TradePermissionGuard tradePermissionGuard(SubscriptionPort subscriptionPort) {
        return new TradePermissionGuard(subscriptionPort);
    }

    @Bean
    public CoreTradingOrchestrator coreTradingOrchestrator(TradePermissionGuard guard,
                                                          TradingSessionRepository repo,
                                                          SessionService sessionService) {
        // P0 defaults: BINANCE BTC/USDT M1. Move to config later.
        return CoreTradingOrchestrator.defaults(guard, repo, sessionService);
    }

}
