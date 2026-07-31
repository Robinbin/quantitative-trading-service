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
 * RSI: Mean-reversion strategy based on RSI(14).
 * RSI < 30 → oversold BUY; RSI > 70 → overbought SELL; otherwise HOLD.
 * Confidence scales linearly from threshold to extreme (0 or 100).
 */
@Component
public class RSIStrategy implements Strategy {

    private static final String NAME = "RSI";
    private static final BigDecimal RSI_OVERSOLD   = new BigDecimal("30");
    private static final BigDecimal RSI_OVERBOUGHT = new BigDecimal("70");
    private static final BigDecimal RSI_MIN        = BigDecimal.ZERO;
    private static final BigDecimal RSI_MAX        = new BigDecimal("100");
    private static final BigDecimal RANGE          = new BigDecimal("30"); // distance from threshold to extreme

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "RSI(14) mean-reversion: below 30 = oversold BUY, above 70 = overbought SELL";
    }

    @Override
    public StrategyResult evaluate(StrategyContext context) {
        BigDecimal rsi = context.indicators().rsi14();

        if (rsi == null) {
            return hold(context.symbol(), "RSI not available");
        }
        if (rsi.compareTo(RSI_MIN) < 0 || rsi.compareTo(RSI_MAX) > 0) {
            return hold(context.symbol(), "RSI value out of expected range: " + rsi);
        }

        if (rsi.compareTo(RSI_OVERSOLD) < 0) {
            BigDecimal confidence = RSI_OVERSOLD.subtract(rsi)
                    .divide(RANGE, 4, RoundingMode.HALF_UP);
            return result(context.symbol(), Signal.BUY, confidence,
                    String.format("RSI oversold at %s", rsi));
        }

        if (rsi.compareTo(RSI_OVERBOUGHT) > 0) {
            BigDecimal confidence = rsi.subtract(RSI_OVERBOUGHT)
                    .divide(RANGE, 4, RoundingMode.HALF_UP);
            return result(context.symbol(), Signal.SELL, confidence,
                    String.format("RSI overbought at %s", rsi));
        }

        return hold(context.symbol(), String.format("RSI neutral at %s", rsi));
    }

    private StrategyResult hold(String symbol, String reason) {
        return result(symbol, Signal.HOLD, BigDecimal.ZERO, reason);
    }

    private StrategyResult result(String symbol, Signal signal, BigDecimal confidence, String reason) {
        return new StrategyResult(NAME, symbol, signal, confidence, reason, Instant.now());
    }
}
