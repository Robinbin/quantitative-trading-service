package com.trading.strategy.impl;

import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.TechnicalIndicators;
import com.trading.marketdata.domain.TickQuote;
import com.trading.strategy.domain.Signal;
import com.trading.strategy.domain.StrategyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BollingerBandStrategyTest {

    private BollingerBandStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new BollingerBandStrategy();
    }

    @Test
    void evaluate_returnsHold_whenTickIsNull() {
        var result = strategy.evaluate(ctxWithPrice(null, "95", "100", "105"));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
        assertThat(result.reason()).contains("Current price not available");
    }

    @Test
    void evaluate_returnsHold_whenBandsNotAvailable() {
        var result = strategy.evaluate(ctxWithPrice("100", null, null, null));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
        assertThat(result.reason()).contains("Bollinger Bands not available");
    }

    @Test
    void evaluate_returnsHold_whenBandWidthIsZero() {
        var result = strategy.evaluate(ctxWithPrice("100", "100", "100", "100"));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
        assertThat(result.reason()).contains("zero or inverted");
    }

    @Test
    void evaluate_returnsBuy_whenPriceAtLowerBand() {
        // price == lower → penetration = 0 → confidence = 0.00, still BUY
        var result = strategy.evaluate(ctxWithPrice("95", "95", "100", "105"));
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void evaluate_returnsBuy_whenPriceBelowLowerBand() {
        // price=93, lower=95, upper=105 → bandWidth=10, halfBand=5
        // penetration = 95 - 93 = 2, confidence = 2/5 = 0.40
        var result = strategy.evaluate(ctxWithPrice("93", "95", "100", "105"));
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(new BigDecimal("0.4000"));
    }

    @Test
    void evaluate_returnsSell_whenPriceAtUpperBand() {
        var result = strategy.evaluate(ctxWithPrice("105", "95", "100", "105"));
        assertThat(result.signal()).isEqualTo(Signal.SELL);
        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void evaluate_returnsSell_whenPriceAboveUpperBand() {
        // price=108, lower=95, upper=105 → halfBand=5, penetration=3, confidence=0.60
        var result = strategy.evaluate(ctxWithPrice("108", "95", "100", "105"));
        assertThat(result.signal()).isEqualTo(Signal.SELL);
        assertThat(result.confidence()).isEqualByComparingTo(new BigDecimal("0.6000"));
    }

    @Test
    void evaluate_returnsHold_whenPriceInsideBands() {
        var result = strategy.evaluate(ctxWithPrice("100", "95", "100", "105"));
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
    }

    @Test
    void evaluate_confidenceCappedAtOne() {
        // price=80, lower=95, upper=105 → penetration=15, halfBand=5, raw=3.00 → capped at 1.00
        var result = strategy.evaluate(ctxWithPrice("80", "95", "100", "105"));
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void evaluate_resultHasCorrectStrategyName() {
        var result = strategy.evaluate(ctxWithPrice("100", "95", "100", "105"));
        assertThat(result.strategyName()).isEqualTo("BOLLINGER");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * @param price  current price (null means tick is null)
     * @param lower  bollingerLower
     * @param middle bollingerMiddle
     * @param upper  bollingerUpper
     */
    private StrategyContext ctxWithPrice(String price, String lower, String middle, String upper) {
        TickQuote tick = price == null ? null : tick(price);
        var ind = new TechnicalIndicators("AAPL", BarInterval.D1, Instant.now(),
                null, null, null, null, null, null,
                null, null, null, null,
                upper  == null ? null : new BigDecimal(upper),
                middle == null ? null : new BigDecimal(middle),
                lower  == null ? null : new BigDecimal(lower));
        return new StrategyContext("AAPL", BarInterval.D1, tick, ind);
    }

    private TickQuote tick(String price) {
        return new TickQuote("AAPL", Instant.now(),
                new BigDecimal(price), null, null, null, null, null, null,
                0L, null, "USD", "NMS");
    }
}
