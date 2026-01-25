package com.quantor.api.rest;

import com.quantor.api.rest.dto.*;
import com.quantor.api.security.SecurityActor;
import com.quantor.application.guard.SubscriptionRequiredException;
import com.quantor.application.ports.TradingSessionRepository;
import com.quantor.application.service.CoreTradingOrchestrator;
import com.quantor.domain.trading.SessionId;
import com.quantor.domain.trading.StopReason;
import com.quantor.domain.trading.StopReasonCode;
import com.quantor.domain.trading.TradingSession;
import com.quantor.domain.trading.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/trading")
public class TradingController {

    private final CoreTradingOrchestrator orchestrator;
    private final TradingSessionRepository sessions;

    public TradingController(CoreTradingOrchestrator orchestrator,
                             TradingSessionRepository sessions) {
        this.orchestrator = orchestrator;
        this.sessions = sessions;
    }

    /**
     * Starts a live trading session for the authenticated user.
     */
    @PostMapping("/start")
    public TradingSessionResponse start(@Valid @RequestBody StartTradingRequest req) {
        String userId = currentUserIdOrThrow();
        TradingSession s = orchestrator.startLive(userId, req.accountId());
        return map(s);
    }

    /**
     * Stops the authenticated user's active session (if any).
     */
    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        String userId = currentUserIdOrThrow();
        orchestrator.stopLive(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Returns the authenticated user's active session.
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        String userId = currentUserIdOrThrow();
        return sessions.findActiveByUser(new UserId(userId))
                .<ResponseEntity<?>>map(s -> ResponseEntity.ok(map(s)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("NOT_FOUND", "No active session")));
    }

    /**
     * Returns a session by ID.
     *
     * Authorization:
     * - USER: can only see their own sessions
     * - ADMIN/support: can see any session
     */
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<?> status(@PathVariable("sessionId") String sessionId) {
        var actor = SecurityActor.current();
        String userId = currentUserIdOrThrow();

        Optional<TradingSession> s = sessions.findById(new SessionId(sessionId));
        if (s.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("NOT_FOUND", "Session not found"));
        }

        TradingSession ts = s.get();
        boolean admin = actor != null && ("ADMIN".equalsIgnoreCase(actor.actorType()) || actor.support());
        if (!admin && !userId.equals(ts.userId().value())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("FORBIDDEN", "Not allowed"));
        }

        return ResponseEntity.ok(map(ts));
    }

    // --- error handling ---

    @ExceptionHandler(SubscriptionRequiredException.class)
    public ResponseEntity<ErrorResponse> handleSub(SubscriptionRequiredException e) {
        StopReason r = e.stopReason();
        String code = r == null ? StopReasonCode.SUBSCRIPTION_REQUIRED.name() : r.code().name();
        String msg = r == null ? e.getMessage() : r.message();
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(new ErrorResponse(code, msg));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }

    private static TradingSessionResponse map(TradingSession s) {
        StopReason sr = s.stopReason();
        return new TradingSessionResponse(
                s.id().value(),
                s.userId().value(),
                s.exchangeAccountRef().exchangeId(),
                s.exchangeAccountRef().accountId(),
                s.strategyId().name(),
                s.status(),
                s.startedAt() == null ? null : s.startedAt().toString(),
                s.stoppedAt() == null ? null : s.stoppedAt().toString(),
                sr == null ? null : sr.code().name(),
                sr == null ? null : sr.message(),
                s.pnl().realized().doubleValue(),
                s.pnl().unrealized().doubleValue()
        );
    }

    private static String currentUserIdOrThrow() {
        var actor = SecurityActor.current();
        if (actor == null || actor.effectiveUserId() == null) {
            throw new IllegalStateException("No authenticated user");
        }
        return actor.effectiveUserId().toString();
    }
}
