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
 * MACD: Signal based on MACD histogram sign.
 * Histogram > 0 → BUY; < 0 → SELL; = 0 → HOLD.
 * Confidence = min(1.0, |histogram| / 0.50).
 *
 * NOTE: The normalization anchor of 0.50 is price-scale-dependent.
 * For large-cap stocks (e.g. $150+) this yields conservative confidence values,
 * which is intentional to prevent over-confidence on high-priced securities.
 */
@Component
public class MACDStrategy implements Strategy {

    private static final String NAME = "MACD";
    private static final BigDecimal NORMALIZATION = new BigDecimal("0.50");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "MACD histogram direction: positive signals BUY, negative signals SELL";
    }

    @Override
    public StrategyResult evaluate(StrategyContext context) {
        BigDecimal histogram = context.indicators().macdHistogram();

        if (histogram == null) {
            return hold(context.symbol(), "MACD histogram not available");
        }

        int cmp = histogram.compareTo(BigDecimal.ZERO);
        if (cmp == 0) {
            return hold(context.symbol(), "MACD histogram at zero");
        }

        BigDecimal confidence = histogram.abs()
                .divide(NORMALIZATION, 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);

        if (cmp > 0) {
            return result(context.symbol(), Signal.BUY, confidence,
                    String.format("MACD histogram positive (%s)", histogram));
        } else {
            return result(context.symbol(), Signal.SELL, confidence,
                    String.format("MACD histogram negative (%s)", histogram));
        }
    }

    private StrategyResult hold(String symbol, String reason) {
        return result(symbol, Signal.HOLD, BigDecimal.ZERO, reason);
    }

    private StrategyResult result(String symbol, Signal signal, BigDecimal confidence, String reason) {
        return new StrategyResult(NAME, symbol, signal, confidence, reason, Instant.now());
    }
}
