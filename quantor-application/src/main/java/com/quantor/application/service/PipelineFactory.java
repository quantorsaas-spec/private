package com.quantor.application.service;

import com.quantor.application.execution.ExecutionJob;
import com.quantor.application.usecase.TradingPipeline;

/**
 * Factory that builds a TradingPipeline for a given job.
 * Implementations live in CLI/bootstrap or infrastructure wiring.
 */
public interface PipelineFactory {
    TradingPipeline create(ExecutionJob job);
}
