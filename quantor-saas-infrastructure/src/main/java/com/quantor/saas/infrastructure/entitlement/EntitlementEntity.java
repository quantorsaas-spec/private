package com.quantor.saas.infrastructure.entitlement;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "entitlements",
    indexes = {
        @Index(name = "ix_entitlements_user", columnList = "user_id")
    }
)
public class EntitlementEntity {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "max_bots", nullable = false)
  private int maxBots;

  @Column(name = "max_trades_per_day", nullable = false)
  private int maxTradesPerDay;

  @Column(name = "daily_loss_limit_pct", nullable = false)
  private double dailyLossLimitPct;

  @Column(name = "telegram_control", nullable = false)
  private boolean telegramControl;

  @Column(name = "advanced_strategies", nullable = false)
  private boolean advancedStrategies;

  @Column(name = "paper_trading_allowed", nullable = false)
  private boolean paperTradingAllowed;

  @Column(name = "live_trading_allowed", nullable = false)
  private boolean liveTradingAllowed;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected EntitlementEntity() {}

  public EntitlementEntity(UUID userId) {
    this.userId = userId;
    this.updatedAt = Instant.now();
  }

  public UUID getUserId() { return userId; }

  public int getMaxBots() { return maxBots; }
  public void setMaxBots(int maxBots) { this.maxBots = maxBots; }

  public int getMaxTradesPerDay() { return maxTradesPerDay; }
  public void setMaxTradesPerDay(int maxTradesPerDay) { this.maxTradesPerDay = maxTradesPerDay; }

  public double getDailyLossLimitPct() { return dailyLossLimitPct; }
  public void setDailyLossLimitPct(double dailyLossLimitPct) { this.dailyLossLimitPct = dailyLossLimitPct; }

  public boolean isTelegramControl() { return telegramControl; }
  public void setTelegramControl(boolean telegramControl) { this.telegramControl = telegramControl; }

  public boolean isAdvancedStrategies() { return advancedStrategies; }
  public void setAdvancedStrategies(boolean advancedStrategies) { this.advancedStrategies = advancedStrategies; }

  public boolean isPaperTradingAllowed() { return paperTradingAllowed; }
  public void setPaperTradingAllowed(boolean paperTradingAllowed) { this.paperTradingAllowed = paperTradingAllowed; }

  public boolean isLiveTradingAllowed() { return liveTradingAllowed; }
  public void setLiveTradingAllowed(boolean liveTradingAllowed) { this.liveTradingAllowed = liveTradingAllowed; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
