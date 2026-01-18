package com.quantor.api.saas;

import com.quantor.saas.domain.model.PlanCode;
import com.quantor.saas.domain.model.PlanLimits;

/**
 * Central place for plan -> limits mapping for MVP.
 * Later move to DB/config.
 */
public final class PlanCatalog {

  private PlanCatalog() {}

  public static PlanLimits limits(PlanCode plan) {
    return switch (plan) {
      case FREE -> new PlanLimits(
          1,
          1,
          1,
          1,
          200,
          30,
          2.0,
          false,
          false,
          true,
          false
      );
      case PRO -> new PlanLimits(
          3,
          5,
          3,
          3,
          500,
          200,
          5.0,
          true,
          true,
          true,
          true
      );
      case PRO_PLUS -> new PlanLimits(
          10,
          20,
          10,
          10,
          2000,
          1000,
          10.0,
          true,
          true,
          true,
          true
      );
      case ENTERPRISE -> new PlanLimits(
          100,
          500,
          100,
          100,
          5000,
          10000,
          20.0,
          true,
          true,
          true,
          true
      );
    };
  }
}
