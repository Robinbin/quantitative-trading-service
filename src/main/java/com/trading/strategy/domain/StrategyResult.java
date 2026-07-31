package com.trading.strategy.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record StrategyResult(
        String     strategyName,
        String     symbol,
        Signal     signal,
        BigDecimal confidence,   // [0.00, 1.00]; HOLD always carries 0.00
        String     reason,
        Instant    evaluatedAt
) {}
