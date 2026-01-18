package com.quantor.cli.tools;

import com.quantor.application.ports.ConfigPort;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigPrinter {

    private ConfigPrinter() {}

    public static void printMasked(ConfigPort cfg, String[] keys, String title) {
        System.out.println("\n" + title);
        Map<String, String> out = new LinkedHashMap<>();
        for (String k : keys) {
            String v;
            try {
                v = cfg.getSecret(k);
            } catch (Exception e) {
                v = "<error: " + e.getMessage() + ">";
            }
            out.put(k, mask(v));
        }
        for (Map.Entry<String, String> e : out.entrySet()) {
            System.out.println(" - " + e.getKey() + " = " + e.getValue());
        }
    }

    public static String maskValue(String v) {
    return mask(v);
  }

  private static String mask(String v) {
        if (v == null || v.isBlank()) return "<empty>";
        String t = v.trim();
        if (t.startsWith("ENC(") && t.endsWith(")")) return "<encrypted>";
        if (t.length() <= 6) return "***";
        return t.substring(0, 2) + "***" + t.substring(t.length() - 2);
    }
}
