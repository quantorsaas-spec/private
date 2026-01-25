package com.quantor.application.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConfigValidationResult {
    private final List<String> errors = new ArrayList<>();

    public void addError(String error) {
        if (error != null && !error.isBlank()) errors.add(error);
    }
  public boolean ok() {
    return isValid();
  }

  public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }
}
