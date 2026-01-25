package com.quantor.db;

import com.quantor.application.ports.TradingSessionRepository;
import com.quantor.domain.trading.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;

/**
 * SQLite-backed TradingSessionRepository (P0).
 *
 * Storage is simple and explicit to keep risk low.
 */
public final class TradingSessionSqlRepository implements TradingSessionRepository {

    @Override
    public Optional<TradingSession> findById(SessionId id) {
        if (id == null) return Optional.empty();
        Database.ensureDataDir();
        Database.initSchema();
        String sql = "SELECT * FROM trading_sessions WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id.value());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("findById failed: " + id.value(), e);
        }
    }

    @Override
    public Optional<TradingSession> findActiveByUser(UserId userId) {
        if (userId == null) return Optional.empty();
        Database.ensureDataDir();
        Database.initSchema();
        String sql = "SELECT * FROM trading_sessions WHERE user_id = ? AND status = ? ORDER BY started_at DESC LIMIT 1";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId.value());
            ps.setString(2, TradingStatus.RUNNING.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("findActiveByUser failed: " + userId.value(), e);
        }
    }

    @Override
    public void save(TradingSession session) {
        if (session == null) return;

        Database.ensureDataDir();
        Database.initSchema();

        String now = Instant.now().toString();

        String sql = """
            INSERT INTO trading_sessions(
              id, user_id, exchange_id, account_id, strategy_id, status,
              started_at, stopped_at, stop_code, stop_message,
              pnl_realized, pnl_unrealized, pnl_updated_at,
              created_at, updated_at
            ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              user_id=excluded.user_id,
              exchange_id=excluded.exchange_id,
              account_id=excluded.account_id,
              strategy_id=excluded.strategy_id,
              status=excluded.status,
              started_at=excluded.started_at,
              stopped_at=excluded.stopped_at,
              stop_code=excluded.stop_code,
              stop_message=excluded.stop_message,
              pnl_realized=excluded.pnl_realized,
              pnl_unrealized=excluded.pnl_unrealized,
              pnl_updated_at=excluded.pnl_updated_at,
              updated_at=excluded.updated_at
            """;

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, session.id().value());
            ps.setString(2, session.userId().value());
            ps.setString(3, session.exchangeAccountRef().exchangeId());
            ps.setString(4, session.exchangeAccountRef().accountId());
            ps.setString(5, session.strategyId().name());
            ps.setString(6, session.status().name());

            ps.setString(7, session.startedAt() == null ? null : session.startedAt().toString());
            ps.setString(8, session.stoppedAt() == null ? null : session.stoppedAt().toString());

            StopReason sr = session.stopReason();
            ps.setString(9, sr == null ? null : sr.code().name());
            ps.setString(10, sr == null ? null : sr.message());

            ps.setDouble(11, session.pnl().realized().doubleValue());
            ps.setDouble(12, session.pnl().unrealized().doubleValue());
            ps.setString(13, session.pnl().updatedAt() == null ? null : session.pnl().updatedAt().toString());

            ps.setString(14, now); // created_at (kept on insert; ignored on update)
            ps.setString(15, now);

            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("save failed: " + session.id().value(), e);
        }
    }

    private static TradingSession map(ResultSet rs) throws Exception {
        SessionId id = new SessionId(rs.getString("id"));
        UserId userId = new UserId(rs.getString("user_id"));

        ExchangeAccountRef ref = new ExchangeAccountRef(
                rs.getString("exchange_id"),
                rs.getString("account_id")
        );

        StrategyId strategyId = StrategyId.valueOf(rs.getString("strategy_id"));
        TradingStatus status = TradingStatus.valueOf(rs.getString("status"));

        Instant startedAt = parseInstant(rs.getString("started_at"));
        Instant stoppedAt = parseInstant(rs.getString("stopped_at"));

        StopReason stopReason = null;
        String stopCode = rs.getString("stop_code");
        String stopMsg = rs.getString("stop_message");
        if (stopCode != null && !stopCode.isBlank()) {
            stopReason = StopReason.of(StopReasonCode.valueOf(stopCode), stopMsg);
        }

        Instant pnlUpdated = parseInstant(rs.getString("pnl_updated_at"));
        if (pnlUpdated == null) pnlUpdated = Instant.now();

        PnLSnapshot pnl = new PnLSnapshot(
                java.math.BigDecimal.valueOf(rs.getDouble("pnl_realized")),
                java.math.BigDecimal.valueOf(rs.getDouble("pnl_unrealized")),
                pnlUpdated
        );

        return TradingSession.restore(id, userId, ref, strategyId, status, startedAt, stoppedAt, stopReason, pnl);
    }

    private static Instant parseInstant(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Instant.parse(v); } catch (Exception e) { return null; }
    }
}
