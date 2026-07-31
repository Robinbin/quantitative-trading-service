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

class MovingAverageCrossStrategyTest {

    private MovingAverageCrossStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MovingAverageCrossStrategy();
    }

    @Test
    void evaluate_returnsHold_whenMa5IsNull() {
        var result = strategy.evaluate(ctx(null, new BigDecimal("100")));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.reason()).contains("MA5 or MA20");
    }

    @Test
    void evaluate_returnsHold_whenMa20IsNull() {
        var result = strategy.evaluate(ctx(new BigDecimal("100"), null));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
    }

    @Test
    void evaluate_returnsHold_whenMa20IsZero() {
        var result = strategy.evaluate(ctx(new BigDecimal("100"), BigDecimal.ZERO));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
        assertThat(result.reason()).contains("MA20 is zero");
    }

    @Test
    void evaluate_returnsHold_whenMa5EqualsMa20() {
        var result = strategy.evaluate(ctx(new BigDecimal("100"), new BigDecimal("100")));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
        assertThat(result.reason()).contains("equals");
    }

    @Test
    void evaluate_returnsBuy_whenMa5AboveMa20_fullConfidence() {
        // 5% separation → confidence = 1.00
        var result = strategy.evaluate(ctx(new BigDecimal("105"), new BigDecimal("100")));
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void evaluate_returnsBuy_withPartialConfidence() {
        // 1% separation → confidence = 0.20
        var result = strategy.evaluate(ctx(new BigDecimal("101"), new BigDecimal("100")));
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(new BigDecimal("0.2000"));
    }

    @Test
    void evaluate_returnsSell_whenMa5BelowMa20() {
        // -5% separation
        var result = strategy.evaluate(ctx(new BigDecimal("95"), new BigDecimal("100")));
        assertThat(result.signal()).isEqualTo(Signal.SELL);
        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void evaluate_confidenceCappedAtOne() {
        // 100% separation, way above 5% threshold
        var result = strategy.evaluate(ctx(new BigDecimal("200"), new BigDecimal("100")));
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void evaluate_resultHasCorrectMetadata() {
        var result = strategy.evaluate(ctx(new BigDecimal("105"), new BigDecimal("100")));
        assertThat(result.strategyName()).isEqualTo("MA_CROSS");
        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.evaluatedAt()).isNotNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private StrategyContext ctx(BigDecimal ma5, BigDecimal ma20) {
        var ind = new TechnicalIndicators("AAPL", BarInterval.D1, Instant.now(),
                ma5, null, ma20, null, null, null,
                null, null, null, null, null, null, null);
        return new StrategyContext("AAPL", BarInterval.D1, null, ind);
    }
}
