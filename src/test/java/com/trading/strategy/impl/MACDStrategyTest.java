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

class MACDStrategyTest {

    private MACDStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MACDStrategy();
    }

    @Test
    void evaluate_returnsHold_whenHistogramIsNull() {
        var result = strategy.evaluate(ctx(null));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
        assertThat(result.reason()).contains("not available");
    }

    @Test
    void evaluate_returnsHold_whenHistogramIsZero() {
        var result = strategy.evaluate(ctx(BigDecimal.ZERO));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
        assertThat(result.reason()).contains("at zero");
    }

    @Test
    void evaluate_returnsBuy_whenHistogramPositive() {
        // 0.25 / 0.50 = 0.50
        var result = strategy.evaluate(ctx(new BigDecimal("0.25")));
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(new BigDecimal("0.5000"));
    }

    @Test
    void evaluate_returnsBuy_withFullConfidence() {
        var result = strategy.evaluate(ctx(new BigDecimal("0.50")));
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void evaluate_returnsSell_whenHistogramNegative() {
        var result = strategy.evaluate(ctx(new BigDecimal("-0.25")));
        assertThat(result.signal()).isEqualTo(Signal.SELL);
        assertThat(result.confidence()).isEqualByComparingTo(new BigDecimal("0.5000"));
    }

    @Test
    void evaluate_confidenceCappedAtOne() {
        var result = strategy.evaluate(ctx(new BigDecimal("2.00")));
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ONE);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private StrategyContext ctx(BigDecimal histogram) {
        var ind = new TechnicalIndicators("TSLA", BarInterval.D1, Instant.now(),
                null, null, null, null, null, null,
                null, null, histogram, null, null, null, null);
        return new StrategyContext("TSLA", BarInterval.D1, null, ind);
    }
}
