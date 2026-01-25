package com.quantor.api.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility to materialize the current trace context into a W3C traceparent header value.
 *
 * This lets us persist trace context into DB commands so the worker can continue the trace.
 */
public final class TraceparentUtil {

  private static final TextMapPropagator PROPAGATOR =
      GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

  private static final TextMapSetter<Map<String, String>> SETTER =
      (carrier, key, value) -> {
        if (carrier != null && key != null && value != null) carrier.put(key, value);
      };

  private TraceparentUtil() {}

  /**
   * @return traceparent header value for the current context, or null if not present.
   */
  public static String currentTraceparentOrNull() {
    try {
      Map<String, String> headers = new HashMap<>();
      PROPAGATOR.inject(Context.current(), headers, SETTER);
      String tp = headers.get("traceparent");
      if (tp == null || tp.isBlank()) return null;
      return tp;
    } catch (Exception ignored) {
      return null;
    }
  }
}
