package com.quantor.worker.engine;

import java.net.InetAddress;
import java.util.UUID;

final class WorkerIds {
  private WorkerIds() {}

  static String randomWorkerId() {
    String host = "unknown";
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception ignored) {}
    return "worker-" + host + "-" + UUID.randomUUID();
  }
}
