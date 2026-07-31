package com.trading.strategy.impl;

import com.trading.strategy.domain.Signal;
import com.trading.strategy.domain.StrategyContext;
import com.trading.strategy.domain.StrategyResult;
import com.trading.strategy.engine.Strategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * MA_CROSS: Golden/death cross of MA5 vs MA20.
 * MA5 > MA20 → BUY; MA5 < MA20 → SELL; equal → HOLD.
 * Confidence scales with percentage separation, reaching 1.0 at 5% gap.
 */
@Component
public class MovingAverageCrossStrategy implements Strategy {

    private static final String NAME = "MA_CROSS";
    private static final BigDecimal SEPARATION_THRESHOLD = new BigDecimal("0.05");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Golden/death cross of MA5 vs MA20; confidence scales with percentage separation";
    }

    @Override
    public StrategyResult evaluate(StrategyContext context) {
        var ind = context.indicators();
        BigDecimal ma5 = ind.ma5();
        BigDecimal ma20 = ind.ma20();

        if (ma5 == null || ma20 == null) {
            return hold(context.symbol(), "Insufficient data: MA5 or MA20 not available");
        }
        if (ma20.compareTo(BigDecimal.ZERO) == 0) {
            return hold(context.symbol(), "MA20 is zero");
        }

        BigDecimal separation = ma5.subtract(ma20)
                .divide(ma20, 6, RoundingMode.HALF_UP);
        int cmp = separation.compareTo(BigDecimal.ZERO);

        if (cmp == 0) {
            return hold(context.symbol(), "MA5 equals MA20");
        }

        BigDecimal confidence = separation.abs()
                .divide(SEPARATION_THRESHOLD, 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
        BigDecimal pct = separation.abs().multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);

        if (cmp > 0) {
            return result(context.symbol(), Signal.BUY, confidence,
                    String.format("MA5 (%s) above MA20 (%s), separation %s%%", ma5, ma20, pct));
        } else {
            return result(context.symbol(), Signal.SELL, confidence,
                    String.format("MA5 (%s) below MA20 (%s), separation %s%%", ma5, ma20, pct));
        }
    }

    private StrategyResult hold(String symbol, String reason) {
        return result(symbol, Signal.HOLD, BigDecimal.ZERO, reason);
    }

    private StrategyResult result(String symbol, Signal signal, BigDecimal confidence, String reason) {
        return new StrategyResult(NAME, symbol, signal, confidence, reason, Instant.now());
    }
}
