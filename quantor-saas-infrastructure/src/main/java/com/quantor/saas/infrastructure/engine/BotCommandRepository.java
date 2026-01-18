package com.quantor.saas.infrastructure.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BotCommandRepository extends JpaRepository<BotCommandEntity, UUID> {

  java.util.Optional<BotCommandEntity> findTopByBotInstanceIdAndStatusOrderByCreatedAtDesc(UUID botInstanceId, String status);

  List<BotCommandEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

  @Query(
      value = """
          SELECT count(*)
          FROM bot_commands
          WHERE status = 'PENDING'
            AND (next_run_at IS NULL OR next_run_at <= now())
          """,
      nativeQuery = true)
  long countDuePending();

  @Query(
      value = """
          SELECT count(*)
          FROM bot_commands
          WHERE status = 'PROCESSING'
          """,
      nativeQuery = true)
  long countProcessing();

  /**
   * Atomically claims up to :limit PENDING commands using Postgres row-level locking.
   * Safe for running multiple worker instances in parallel.
   */
  @Transactional
  @Query(
      value = """
          WITH cte AS (
            SELECT id
            FROM bot_commands
            WHERE status = 'PENDING'
              AND (next_run_at IS NULL OR next_run_at <= now())
            ORDER BY created_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
          )
          UPDATE bot_commands bc
          SET status = 'PROCESSING',
              worker_id = :workerId,
              locked_at = now()
          FROM cte
          WHERE bc.id = cte.id
          RETURNING bc.*
          """,
      nativeQuery = true)
  List<BotCommandEntity> claimBatch(@Param("workerId") String workerId, @Param("limit") int limit);

  /** Re-queues stuck PROCESSING commands (e.g., worker crashed) back to PENDING. */
  @Modifying
  @Query(
      value = """
          UPDATE bot_commands
          SET status = 'PENDING',
              worker_id = NULL,
              locked_at = NULL
          WHERE status = 'PROCESSING'
            AND locked_at IS NOT NULL
            AND locked_at < :deadline
          """,
      nativeQuery = true)
  int requeueStuck(@Param("deadline") Instant deadline);

  @Modifying
  @Query(
      value = """
          UPDATE bot_commands
          SET status = 'DONE',
              processed_at = now(),
              error_message = NULL
          WHERE id = :id
          """,
      nativeQuery = true)
  int markDone(@Param("id") UUID id);

  /**
   * Marks command FAILED (final) and stores error.
   */
  @Modifying
  @Query(
      value = """
          UPDATE bot_commands
          SET status = 'FAILED',
              processed_at = now(),
              error_message = left(:error, 512)
          WHERE id = :id
          """,
      nativeQuery = true)
  int markFailed(@Param("id") UUID id, @Param("error") String error);

  /**
   * Schedules a retry: increments attempts, sets next_run_at, and moves back to PENDING.
   */
  @Modifying
  @Query(
      value = """
          UPDATE bot_commands
          SET attempts = attempts + 1,
              status = 'PENDING',
              next_run_at = :nextRunAt,
              worker_id = NULL,
              locked_at = NULL,
              error_message = left(:error, 512)
          WHERE id = :id
          """,
      nativeQuery = true)
  int scheduleRetry(@Param("id") UUID id, @Param("nextRunAt") Instant nextRunAt, @Param("error") String error);
}
