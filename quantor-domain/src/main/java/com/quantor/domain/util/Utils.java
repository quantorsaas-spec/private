package com.quantor.domain.util;

import java.util.*;

/** Utility helper methods. */
public class Utils {

    /** Simple EMA for a list of values (starts from the first value). */
    public static List<Double> ema(List<Double> values, int period) {
        if (values.size() < period) return Collections.emptyList();

        List<Double> out = new ArrayList<>(values.size());
        double k = 2.0 / (period + 1);
        double ema = values.get(0);
        out.add(ema);

        for (int i = 1; i < values.size(); i++) {
            ema = values.get(i) * k + ema * (1 - k);
            out.add(ema);
        }
        return out;
    }
}