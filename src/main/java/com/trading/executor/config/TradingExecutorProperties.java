package com.trading.executor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paper-trading engine configuration, bound from {@code trading.executor.*}.
 *
 * @param initialCash         starting cash balance in USD (default 100 000)
 * @param maxOrderValue       maximum USD value per individual order (default 10 000)
 * @param minStrategiesAgree  number of strategies that must agree for auto-execute to fire (default 3)
 * @param minConfidence       minimum average confidence across agreeing strategies (default 0.60)
 */
@ConfigurationProperties(prefix = "trading.executor")
public record TradingExecutorProperties(
        double initialCash,
        double maxOrderValue,
        int    minStrategiesAgree,
        double minConfidence
) {
    public TradingExecutorProperties {
        if (initialCash        <= 0) initialCash        = 100_000.0;
        if (maxOrderValue      <= 0) maxOrderValue      = 10_000.0;
        if (minStrategiesAgree <= 0) minStrategiesAgree = 3;
        if (minConfidence      <= 0) minConfidence      = 0.60;
    }
}
