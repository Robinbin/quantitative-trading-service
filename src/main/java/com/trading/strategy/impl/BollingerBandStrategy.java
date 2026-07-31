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
 * BOLLINGER: Price vs Bollinger Bands(20,2).
 * Price <= lower band → BUY; price >= upper band → SELL; otherwise HOLD.
 * Confidence = min(1.0, penetration / halfBandWidth).
 */
@Component
public class BollingerBandStrategy implements Strategy {

    private static final String NAME = "BOLLINGER";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Price vs Bollinger Bands(20,2): touch/breach of lower band = BUY, upper band = SELL";
    }

    @Override
    public StrategyResult evaluate(StrategyContext context) {
        var tick = context.tick();
        if (tick == null || tick.price() == null) {
            return hold(context.symbol(), "Current price not available");
        }

        var ind = context.indicators();
        BigDecimal upper  = ind.bollingerUpper();
        BigDecimal middle = ind.bollingerMiddle();
        BigDecimal lower  = ind.bollingerLower();

        if (upper == null || middle == null || lower == null) {
            return hold(context.symbol(), "Bollinger Bands not available");
        }
        if (upper.compareTo(lower) <= 0) {
            return hold(context.symbol(), "Bollinger Band width is zero or inverted");
        }

        BigDecimal price    = tick.price();
        BigDecimal halfBand = upper.subtract(lower)
                .divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);

        if (price.compareTo(lower) <= 0) {
            BigDecimal penetration = lower.subtract(price);
            BigDecimal confidence  = penetration.divide(halfBand, 4, RoundingMode.HALF_UP)
                    .min(BigDecimal.ONE);
            return result(context.symbol(), Signal.BUY, confidence,
                    String.format("Price (%s) at or below lower band (%s)", price, lower));
        }

        if (price.compareTo(upper) >= 0) {
            BigDecimal penetration = price.subtract(upper);
            BigDecimal confidence  = penetration.divide(halfBand, 4, RoundingMode.HALF_UP)
                    .min(BigDecimal.ONE);
            return result(context.symbol(), Signal.SELL, confidence,
                    String.format("Price (%s) at or above upper band (%s)", price, upper));
        }

        return hold(context.symbol(),
                String.format("Price (%s) within Bollinger Bands [%s, %s]", price, lower, upper));
    }

    private StrategyResult hold(String symbol, String reason) {
        return result(symbol, Signal.HOLD, BigDecimal.ZERO, reason);
    }

    private StrategyResult result(String symbol, Signal signal, BigDecimal confidence, String reason) {
        return new StrategyResult(NAME, symbol, signal, confidence, reason, Instant.now());
    }
}
