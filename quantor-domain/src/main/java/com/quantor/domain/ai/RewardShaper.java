package com.quantor.domain.ai;

public class RewardShaper {

    public static class Reward {
        public final double retClose;   // gross close return (without fees)
        public final Double ret3;       // optional
        public final Double ret5;       // optional
        public final double target01;   // 0..1

        public Reward(double retClose, Double ret3, Double ret5, double target01) {
            this.retClose = retClose;
            this.ret3 = ret3;
            this.ret5 = ret5;
            this.target01 = target01;
        }
    }

    public static Reward build(double entryPrice,
                               double exitPrice,
                               Double priceAfter3,
                               Double priceAfter5,
                               double k) {
        return build(entryPrice, exitPrice, priceAfter3, priceAfter5, k, 0.0, 0.0);
    }

    /**
     * feeOpenPct / feeClosePct — fees as fractions (e.g. 0.001 = 0.1%)
     */
    public static Reward build(double entryPrice,
                               double exitPrice,
                               Double priceAfter3,
                               Double priceAfter5,
                               double k,
                               double feeOpenPct,
                               double feeClosePct) {

        if (entryPrice <= 0 || exitPrice <= 0) {
            return new Reward(0.0, null, null, 0.5);
        }

        // gross return
        double retClose = (exitPrice - entryPrice) / entryPrice;

        // net return (including fees)
        double retNetClose = retClose - feeOpenPct - feeClosePct;

        Double ret3 = null;
        Double ret5 = null;

        if (priceAfter3 != null && priceAfter3 > 0) {
            ret3 = (priceAfter3 - exitPrice) / exitPrice;
        }
        if (priceAfter5 != null && priceAfter5 > 0) {
            ret5 = (priceAfter5 - exitPrice) / exitPrice;
        }

        // shaping weights
        double wClose = 0.85;
        double w3 = 0.10;
        double w5 = 0.05;

        double shaped = wClose * retNetClose
                + (ret3 == null ? 0.0 : w3 * ret3)
                + (ret5 == null ? 0.0 : w5 * ret5);

        /*
          ✅ Key fix:
          Instead of a linear 0.5 + k * shaped, use tanh
          so the target moves noticeably away from 0.5 even for small returns,
          but does not saturate to 0/1 too early.
        */
        double gain = 6.0; // response amplification (can be 4..10)
        double z = k * shaped * gain;

        double target = 0.5 + 0.5 * Math.tanh(z);

        // ✅ soft clamp to prevent the model from sticking at extremes
        if (target < 0.05) target = 0.05;
        if (target > 0.95) target = 0.95;

        return new Reward(retClose, ret3, ret5, target);
    }
}