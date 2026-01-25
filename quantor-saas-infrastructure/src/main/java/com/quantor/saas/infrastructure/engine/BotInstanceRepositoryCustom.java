package com.quantor.saas.infrastructure.engine;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Lease primitives to support multi-worker execution.
 *
 * Lease semantics:
 * - A bot instance in desired status RUNNING/PAUSED should have a lease_owner and lease_until.
 * - Workers claim expired/unowned leases using SELECT ... FOR UPDATE SKIP LOCKED.
 * - Workers periodically renew leases they own.
 */
public interface BotInstanceRepositoryCustom {

  /**
   * Claims up to {@code limit} bot instances whose desired state requires execution and whose lease is missing/expired.
   */
  List<UUID> claimDueLeases(String workerId, Duration leaseTtl, int limit);

  /** Returns bot instance ids currently leased by this worker and not expired. */
  List<UUID> findActiveLeasesByOwner(String workerId);

  /** Counts bot instances currently leased by this worker and not expired (for metrics). */
  long countActiveLeasesByOwner(String workerId);

  /** Renews leases for all currently leased instances owned by this worker. */
  int renewLeases(String workerId, Duration leaseTtl);

  /** Clears lease ownership for an instance (best-effort). */
  int releaseLease(UUID botInstanceId, String workerId);
}
