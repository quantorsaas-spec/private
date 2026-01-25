package com.quantor.api.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds a stable correlation id for every HTTP request.
 *
 * - Reads request id from X-Request-Id (if provided)
 * - Otherwise generates a UUID
 * - Stores it in MDC + RequestContext for downstream code
 * - Echoes back in response header X-Request-Id
 *
 * Also captures W3C traceparent header (if present) for best-effort propagation
 * to async processing (worker).
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HDR_REQUEST_ID = "X-Request-Id";
  public static final String HDR_TRACEPARENT = "traceparent";
  public static final String MDC_REQUEST_ID = "requestId";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String reqId = firstNonBlank(request.getHeader(HDR_REQUEST_ID), request.getHeader("X-Correlation-Id"));
    if (reqId == null) reqId = UUID.randomUUID().toString();

    String traceparent = firstNonBlank(request.getHeader(HDR_TRACEPARENT), request.getHeader("X-Traceparent"));

    // MDC + ThreadLocal context
    MDC.put(MDC_REQUEST_ID, reqId);
    RequestContext.set(reqId, traceparent);

    // echo for client
    response.setHeader(HDR_REQUEST_ID, reqId);
    if (traceparent != null) {
      response.setHeader(HDR_TRACEPARENT, traceparent);
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      RequestContext.clear();
      MDC.remove(MDC_REQUEST_ID);
    }
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a.trim();
    if (b != null && !b.isBlank()) return b.trim();
    return null;
  }
}
