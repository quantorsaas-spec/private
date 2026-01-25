package com.quantor.worker.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkerIdentity {
  private final String workerId;

  public WorkerIdentity(@Value("${quantor.worker.id:}") String configuredWorkerId) {
    this.workerId = (configuredWorkerId == null || configuredWorkerId.isBlank())
        ? WorkerIds.randomWorkerId()
        : configuredWorkerId;
  }

  public String id() { return workerId; }
}
