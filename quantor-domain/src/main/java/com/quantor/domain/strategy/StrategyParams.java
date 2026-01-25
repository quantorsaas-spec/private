package com.quantor.domain.strategy;

import com.quantor.domain.strategy.impl.DebugStrategy;
import com.quantor.domain.strategy.impl.EmaCrossStrategy;
import com.quantor.domain.strategy.online.OnlineStrategy;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Strategy description helper (ASCII-only).
 */
public final class StrategyParams {

    private StrategyParams() {}

    public static String describe(Strategy strategy) {
        if (strategy == null) return "(null strategy)";
        String name = strategy.getClass().getSimpleName();

        if (strategy instanceof OnlineStrategy os) {
            String summary = safe(os.getParamsSummary());
            return "OnlineStrategy\n" + summary;
        }

        if (strategy instanceof EmaCrossStrategy ema) {
            StringBuilder sb = new StringBuilder();
            sb.append("EMA Cross\n");
            sb.append("fast=").append(ema.getFastPeriod()).append("\n");
            sb.append("slow=").append(ema.getSlowPeriod());
            Object lookback = tryInvoke(ema, "getLookback");
            if (lookback != null) sb.append("\nlookback=").append(lookback);
            Object debug = tryInvoke(ema, "isDebug");
            if (debug != null) sb.append("\ndebug=").append(debug);
            return sb.toString();
        }

        if (strategy instanceof DebugStrategy) {
            return "DebugStrategy (testing only)";
        }

        return "Strategy: " + name + "\n" + reflectFields(strategy);
    }

    private static Object tryInvoke(Object obj, String method) {
        try {
            var m = obj.getClass().getMethod(method);
            return m.invoke(obj);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String reflectFields(Object obj) {
        try {
            Field[] fields = obj.getClass().getDeclaredFields();
            Arrays.sort(fields, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            StringBuilder sb = new StringBuilder();
            for (Field f : fields) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    sb.append(f.getName()).append("=").append(String.valueOf(v)).append("\n");
                } catch (Exception ignore) {}
            }
            String out = sb.toString().trim();
            return out.isEmpty() ? "(no params)" : out;
        } catch (Exception e) {
            return "(params unavailable)";
        }
    }
}
