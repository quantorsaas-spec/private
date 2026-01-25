package com.quantor.saas.infrastructure.engine;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class BotInstanceRepositoryImpl implements BotInstanceRepositoryCustom {

  @PersistenceContext
  private EntityManager em;

  @Override
  @Transactional
  public List<UUID> claimDueLeases(String workerId, Duration leaseTtl, int limit) {
    // Select candidates and lock them so concurrent workers can skip.
    List<UUID> ids = em.createNativeQuery(
        "SELECT id FROM bot_instances " +
            "WHERE status IN ('RUNNING','PAUSED') " +
            "AND (lease_until IS NULL OR lease_until < now()) " +
            "ORDER BY updated_at ASC " +
            "LIMIT :limit " +
            "FOR UPDATE SKIP LOCKED"
    )
        .setParameter("limit", limit)
        .getResultList();

    if (ids.isEmpty()) return ids;

    Instant until = Instant.now().plus(leaseTtl);
    // Update row-by-row to avoid JDBC array binding differences across drivers.
    for (UUID id : ids) {
      em.createNativeQuery(
          "UPDATE bot_instances SET lease_owner = :workerId, lease_until = :until WHERE id = :id"
      )
          .setParameter("workerId", workerId)
          .setParameter("until", until)
          .setParameter("id", id)
          .executeUpdate();
    }

    return ids;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> findActiveLeasesByOwner(String workerId) {
    return em.createNativeQuery(
        "SELECT id FROM bot_instances " +
            "WHERE status IN ('RUNNING','PAUSED') " +
            "AND lease_owner = :workerId " +
            "AND lease_until IS NOT NULL " +
            "AND lease_until >= now()"
    )
        .setParameter("workerId", workerId)
        .getResultList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countActiveLeasesByOwner(String workerId) {
    Object result = em.createNativeQuery(
        "SELECT count(*) FROM bot_instances " +
            "WHERE status IN ('RUNNING','PAUSED') " +
            "AND lease_owner = :workerId " +
            "AND lease_until IS NOT NULL " +
            "AND lease_until >= now()"
    )
        .setParameter("workerId", workerId)
        .getSingleResult();

    if (result instanceof Number n) return n.longValue();
    return Long.parseLong(result.toString());
  }

  @Override
  @Transactional
  public int renewLeases(String workerId, Duration leaseTtl) {
    Instant until = Instant.now().plus(leaseTtl);
    return em.createNativeQuery(
        "UPDATE bot_instances SET lease_until = :until " +
            "WHERE status IN ('RUNNING','PAUSED') " +
            "AND lease_owner = :workerId " +
            "AND (lease_until IS NULL OR lease_until >= now() - interval '10 seconds')"
    )
        .setParameter("until", until)
        .setParameter("workerId", workerId)
        .executeUpdate();
  }

  @Override
  @Transactional
  public int releaseLease(UUID botInstanceId, String workerId) {
    return em.createNativeQuery(
        "UPDATE bot_instances SET lease_owner = NULL, lease_until = NULL " +
            "WHERE id = :id AND (lease_owner IS NULL OR lease_owner = :workerId)"
    )
        .setParameter("id", botInstanceId)
        .setParameter("workerId", workerId)
        .executeUpdate();
  }
}
