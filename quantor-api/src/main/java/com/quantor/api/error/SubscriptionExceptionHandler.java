package com.quantor.api.error;

import com.quantor.application.guard.SubscriptionRequiredException;
import com.quantor.domain.trading.StopReason;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class SubscriptionExceptionHandler {

    @ExceptionHandler(SubscriptionRequiredException.class)
    public ResponseEntity<?> handleSubscriptionRequired(SubscriptionRequiredException ex) {
        StopReason reason = ex.stopReason();

        Map<String, Object> body = Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "error", "SUBSCRIPTION_REQUIRED",
                "message", ex.getMessage(),
                "stopReason", reason == null ? null : Map.of(
                        "code", reason.code().name(),
                        "message", reason.message()
                )
        );
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(body); // 402
    }
}
