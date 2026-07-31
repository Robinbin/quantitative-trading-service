package com.trading.strategy.engine;

import com.trading.strategy.domain.StrategyContext;
import com.trading.strategy.domain.StrategyResult;

public interface Strategy {

    /** Stable identifier used as the ?strategy= query parameter. */
    String name();

    /** One-sentence description for the strategy listing endpoint. */
    String description();

    /**
     * Evaluates the given context and returns a trading signal.
     * Implementations must never throw — return a HOLD result for any error condition.
     */
    StrategyResult evaluate(StrategyContext context);
}
