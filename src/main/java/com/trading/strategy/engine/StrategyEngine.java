package com.trading.strategy.engine;

import com.trading.strategy.domain.StrategyContext;
import com.trading.strategy.domain.StrategyDefinition;
import com.trading.strategy.domain.StrategyResult;

import java.util.List;

public interface StrategyEngine {

    /** Returns metadata for all registered strategies. */
    List<StrategyDefinition> listStrategies();

    /**
     * Finds the strategy with the given name (case-insensitive) and evaluates it.
     *
     * @throws IllegalArgumentException if no strategy with that name is registered
     */
    StrategyResult evaluate(String strategyName, StrategyContext context);

    /** Runs every registered strategy and returns all results. */
    List<StrategyResult> evaluateAll(StrategyContext context);
}
