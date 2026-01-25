package com.quantor.api.saas;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory registry of running jobs per user.
 * Good enough for single-instance MVP; for multi-instance move to DB/Redis.
 */
@Component
public class EngineJobRegistry {

  private final Map<String, Set<String>> jobsByUser = new ConcurrentHashMap<>();

  public int runningCount(String userId) {
    return jobsByUser.getOrDefault(userId, Collections.emptySet()).size();
  }

  public void register(String userId, String jobKey) {
    jobsByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(jobKey);
  }

  public void unregister(String userId, String jobKey) {
    Set<String> set = jobsByUser.get(userId);
    if (set != null) {
      set.remove(jobKey);
      if (set.isEmpty()) jobsByUser.remove(userId);
    }
  }
}
