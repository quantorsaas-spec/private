package com.quantor.infrastructure.db;


import com.quantor.domain.strategy.Strategy;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class StrategyRepository {

    public record StrategyRow(long id, String name, boolean enabled, String paramsJson) {}

    public StrategyRepository() {
        Database.initSchema();
    }

    public long create(String name, boolean enabled, String paramsJson) throws SQLException {
        validateName(name);
        if (paramsJson == null || paramsJson.isBlank()) {
            throw new IllegalArgumentException("params_json cannot be empty");
        }

        String now = Instant.now().toString();

        String sql = """
            INSERT INTO strategies(name, enabled, params_json, created_at, updated_at)
            VALUES(?,?,?,?,?)
        """;

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name.trim());
            ps.setInt(2, enabled ? 1 : 0);
            ps.setString(3, paramsJson);
            ps.setString(4, now);
            ps.setString(5, now);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("Failed to obtain id of the new strategy");
    }

    public List<StrategyRow> listAll(boolean includeDisabled) throws SQLException {
        String sql = includeDisabled
                ? "SELECT id,name,enabled,params_json FROM strategies ORDER BY id"
                : "SELECT id,name,enabled,params_json FROM strategies WHERE enabled=1 ORDER BY id";

        List<StrategyRow> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                out.add(new StrategyRow(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("enabled") == 1,
                        rs.getString("params_json")
                ));
            }
        }
        return out;
    }

    public StrategyRow getById(long id) throws SQLException {
        String sql = "SELECT id,name,enabled,params_json FROM strategies WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new StrategyRow(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("enabled") == 1,
                        rs.getString("params_json")
                );
            }
        }
    }

    public List<StrategyRow> findByNameLike(String q) throws SQLException {
        if (q == null) q = "";
        String sql = "SELECT id,name,enabled,params_json FROM strategies WHERE name LIKE ? ORDER BY id";
        List<StrategyRow> out = new ArrayList<>();

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, "%" + q.trim() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StrategyRow(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getInt("enabled") == 1,
                            rs.getString("params_json")
                    ));
                }
            }
        }
        return out;
    }

    public void updateParams(long id, String newParamsJson) throws SQLException {
        if (newParamsJson == null || newParamsJson.isBlank()) {
            throw new IllegalArgumentException("params_json cannot be empty");
        }

        String sql = "UPDATE strategies SET params_json=?, updated_at=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, newParamsJson);
            ps.setString(2, Instant.now().toString());
            ps.setLong(3, id);

            if (ps.executeUpdate() == 0) {
                throw new SQLException("Strategy not found: id=" + id);
            }
        }
    }

    public void setEnabled(long id, boolean enabled) throws SQLException {
        String sql = "UPDATE strategies SET enabled=?, updated_at=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, enabled ? 1 : 0);
            ps.setString(2, Instant.now().toString());
            ps.setLong(3, id);

            if (ps.executeUpdate() == 0) {
                throw new SQLException("Strategy not found: id=" + id);
            }
        }
    }

    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM strategies WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, id);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Strategy not found: id=" + id);
            }
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Strategy name is empty");
        if (name.length() > 50)
            throw new IllegalArgumentException("Strategy name is too long (max 50)");
    }
}