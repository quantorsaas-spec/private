package com.quantor.worker.engine;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Updates worker liveness in Postgres.
 */
@Component
public class WorkerHeartbeat {

  private final JdbcTemplate jdbc;
  private final String workerId;

  public WorkerHeartbeat(JdbcTemplate jdbc, WorkerIdentity workerIdentity) {
    this.jdbc = jdbc;
    this.workerId = workerIdentity.id();

    // ensure row exists
    jdbc.update(
        "INSERT INTO quantor_workers(worker_id) VALUES (?) ON CONFLICT (worker_id) DO NOTHING",
        workerId
    );
  }

  @Scheduled(fixedDelayString = "${quantor.worker.heartbeatMs:5000}")
  public void heartbeat() {
    jdbc.update(
        "UPDATE quantor_workers SET last_heartbeat_at = now() WHERE worker_id = ?",
        workerId
    );
  }
}
