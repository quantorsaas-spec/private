package com.quantor.api.events;

import com.quantor.saas.infrastructure.events.EventLogEntity;
import com.quantor.saas.infrastructure.events.EventLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Simple idempotency layer backed by DB unique constraint (source,eventId).
 * If the event already exists, returns empty and caller should NOT process it again.
 */
@Service
public class IdempotentEventService {

  private final EventLogRepository events;

  public IdempotentEventService(EventLogRepository events) {
    this.events = events;
  }

  public Optional<EventLogEntity> tryStart(String source, String eventId, String eventType, UUID userId, String payloadJson) {
    try {
      EventLogEntity e = new EventLogEntity(UUID.randomUUID(), source, eventId, eventType, userId, payloadJson);
      return Optional.of(events.save(e));
    } catch (DataIntegrityViolationException dup) {
      return Optional.empty();
    } catch (RuntimeException ex) {
      // Some drivers wrap constraint violations differently; fall back to lookup.
      if (events.findBySourceAndEventId(source, eventId).isPresent()) {
        return Optional.empty();
      }
      throw ex;
    }
  }

  public void markProcessed(UUID rowId) {
    EventLogEntity e = events.findById(rowId).orElse(null);
    if (e == null) return;
    e.setStatus("PROCESSED");
    e.setProcessedAt(Instant.now());
    events.save(e);
  }

  public void markFailed(UUID rowId, String error) {
    EventLogEntity e = events.findById(rowId).orElse(null);
    if (e == null) return;
    e.setStatus("FAILED");
    e.setError(error);
    e.setProcessedAt(Instant.now());
    events.save(e);
  }
}
