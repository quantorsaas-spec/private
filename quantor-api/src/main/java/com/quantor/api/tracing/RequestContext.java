package com.quantor.api.tracing;

/**
 * Lightweight per-request context stored in ThreadLocal.
 *
 * We intentionally keep this minimal (requestId + traceparent) so we can propagate
 * correlation across: API -> DB (bot_commands) -> worker logs.
 */
public final class RequestContext {

  private static final ThreadLocal<Ctx> TL = new ThreadLocal<>();

  private RequestContext() {}

  public static void set(String requestId, String traceparent) {
    TL.set(new Ctx(requestId, traceparent));
  }

  public static void clear() {
    TL.remove();
  }

  public static String requestId() {
    Ctx c = TL.get();
    return c == null ? null : c.requestId;
  }

  public static String traceparent() {
    Ctx c = TL.get();
    return c == null ? null : c.traceparent;
  }

  private record Ctx(String requestId, String traceparent) {}
}
