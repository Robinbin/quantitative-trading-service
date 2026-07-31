package com.trading.strategy.impl;

import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.TechnicalIndicators;
import com.trading.strategy.domain.Signal;
import com.trading.strategy.domain.StrategyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RSIStrategyTest {

    private RSIStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RSIStrategy();
    }

    @Test
    void evaluate_returnsHold_whenRsiIsNull() {
        var result = strategy.evaluate(ctx(null));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
        assertThat(result.reason()).contains("not available");
    }

    @Test
    void evaluate_returnsHold_whenRsiIsNegative() {
        var result = strategy.evaluate(ctx(new BigDecimal("-1")));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
        assertThat(result.reason()).contains("out of expected range");
    }

    @Test
    void evaluate_returnsHold_whenRsiAbove100() {
        var result = strategy.evaluate(ctx(new BigDecimal("101")));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
    }

    @Test
    void evaluate_returnsBuy_whenRsiOversold() {
        // rsi=20 → (30-20)/30 = 10/30 ≈ 0.3333
        var result = strategy.evaluate(ctx(new BigDecimal("20")));
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(new BigDecimal("0.3333"));
    }

    @Test
    void evaluate_returnsBuy_justBelowThreshold() {
        var result = strategy.evaluate(ctx(new BigDecimal("29.99")));
        assertThat(result.signal()).isEqualTo(Signal.BUY);
    }

    @Test
    void evaluate_returnsHold_atLowerThreshold() {
        var result = strategy.evaluate(ctx(new BigDecimal("30")));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
    }

    @Test
    void evaluate_returnsHold_atUpperThreshold() {
        var result = strategy.evaluate(ctx(new BigDecimal("70")));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
    }

    @Test
    void evaluate_returnsSell_whenRsiOverbought() {
        // rsi=85 → (85-70)/30 = 15/30 = 0.50
        var result = strategy.evaluate(ctx(new BigDecimal("85")));
        assertThat(result.signal()).isEqualTo(Signal.SELL);
        assertThat(result.confidence()).isEqualByComparingTo(new BigDecimal("0.5000"));
    }

    @Test
    void evaluate_returnsBuy_withFullConfidence_atZero() {
        // rsi=0 → (30-0)/30 = 1.00
        var result = strategy.evaluate(ctx(BigDecimal.ZERO));
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void evaluate_returnsSell_withFullConfidence_atHundred() {
        // rsi=100 → (100-70)/30 = 1.00
        var result = strategy.evaluate(ctx(new BigDecimal("100")));
        assertThat(result.signal()).isEqualTo(Signal.SELL);
        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ONE);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private StrategyContext ctx(BigDecimal rsi) {
        var ind = new TechnicalIndicators("MSFT", BarInterval.D1, Instant.now(),
                null, null, null, null, null, null,
                null, null, null, rsi, null, null, null);
        return new StrategyContext("MSFT", BarInterval.D1, null, ind);
    }
}
