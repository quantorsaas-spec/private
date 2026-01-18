package com.quantor.domain.strategy;

import com.quantor.domain.risk.RiskManager;
import com.quantor.domain.strategy.impl.EmaCrossStrategy;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Random;

public class AutoTuner {

    private final Strategy emaStrategy;
    private final RiskManager rm;
    private final int tuneEvery;

    private int trades = 0;
    private int wins   = 0;
    private int losses = 0;

    private final Random rnd = new Random();

    public AutoTuner(EmaCrossStrategy ema, RiskManager rm, int tuneEvery) {
        this.emaStrategy = ema;
        this.rm = rm;
        this.tuneEvery = Math.max(1, tuneEvery);
    }

    public void onTrade(double pnl) {
        trades++;
        if (pnl > 0) wins++; else losses++;

        if (trades % tuneEvery != 0) return;

        tune();
        log();
    }

    private void tune() {
        double pos = rm.getPositionUSDT();

        if (losses >= wins) pos *= 0.9;
        else pos *= 1.05;

        if (pos < 10) pos = 10;
        if (pos > 5000) pos = 5000;
        rm.setPositionUSDT(pos);

        mutateEmaIfPossible();
    }

    private void mutateEmaIfPossible() {
        if (emaStrategy == null) return;

        try {
            Class<?> cls = emaStrategy.getClass();
            Field fastField = null;
            Field slowField = null;

            try { fastField = cls.getDeclaredField("fastPeriod"); } catch (NoSuchFieldException ignored) {}
            try { slowField = cls.getDeclaredField("slowPeriod"); } catch (NoSuchFieldException ignored) {}

            if (fastField == null) {
                try { fastField = cls.getDeclaredField("fast"); } catch (NoSuchFieldException ignored) {}
            }
            if (slowField == null) {
                try { slowField = cls.getDeclaredField("slow"); } catch (NoSuchFieldException ignored) {}
            }

            if (fastField == null || slowField == null) return;

            fastField.setAccessible(true);
            slowField.setAccessible(true);

            int fast = fastField.getInt(emaStrategy);
            int slow = slowField.getInt(emaStrategy);

            fast += rnd.nextInt(3) - 1;
            slow += rnd.nextInt(5) - 2;

            if (fast < 5) fast = 5;
            if (slow < fast + 3) slow = fast + 3;
            if (slow > 120) slow = 120;

            fastField.setInt(emaStrategy, fast);
            slowField.setInt(emaStrategy, slow);

        } catch (Exception ignored) {}
    }

    private int getEmaFastSafe() {
        if (emaStrategy == null) return -1;
        try {
            Class<?> cls = emaStrategy.getClass();
            Field f;
            try { f = cls.getDeclaredField("fastPeriod"); }
            catch (NoSuchFieldException e) { f = cls.getDeclaredField("fast"); }
            f.setAccessible(true);
            return f.getInt(emaStrategy);
        } catch (Exception e) { return -1; }
    }

    private int getEmaSlowSafe() {
        if (emaStrategy == null) return -1;
        try {
            Class<?> cls = emaStrategy.getClass();
            Field f;
            try { f = cls.getDeclaredField("slowPeriod"); }
            catch (NoSuchFieldException e) { f = cls.getDeclaredField("slow"); }
            f.setAccessible(true);
            return f.getInt(emaStrategy);
        } catch (Exception e) { return -1; }
    }

    private void log() {
        String msg = String.format(
                "🧠 AutoTuner %s | EMA=%d/%d | positionUSDT=%.2f | Wins=%d Losses=%d Trades=%d",
                LocalDateTime.now(),
                getEmaFastSafe(),
                getEmaSlowSafe(),
                rm.getPositionUSDT(),
                wins, losses, trades
        );
        System.out.println(msg);
    }

    public String getStatus() {
        return String.format(
                "🧠 AutoTuner STATUS\n" +
                        "Trades: %d\nWins: %d\nLosses: %d\n" +
                        "EMA fast: %d\nEMA slow: %d\npositionUSDT: %.2f",
                trades, wins, losses,
                getEmaFastSafe(), getEmaSlowSafe(),
                rm.getPositionUSDT()
        );
    }
}