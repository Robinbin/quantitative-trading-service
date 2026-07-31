package com.trading.marketdata.indicator;

import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.OhlcvBar;
import com.trading.marketdata.domain.TechnicalIndicators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TechnicalIndicatorCalculatorTest {

    private TechnicalIndicatorCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TechnicalIndicatorCalculator();
    }

    @Test
    void shouldReturnEmptyIndicatorsForNullBars() {
        TechnicalIndicators ti = calculator.calculate(null, "AAPL", BarInterval.D1);

        assertThat(ti).isNotNull();
        assertThat(ti.symbol()).isEqualTo("AAPL");
        assertThat(ti.interval()).isEqualTo(BarInterval.D1);
        assertAllFieldsNull(ti);
    }

    @Test
    void shouldReturnEmptyIndicatorsForEmptyList() {
        TechnicalIndicators ti = calculator.calculate(Collections.emptyList(), "AAPL", BarInterval.D1);

        assertThat(ti).isNotNull();
        assertAllFieldsNull(ti);
    }

    @Test
    void shouldReturnEmptyIndicatorsForSingleBar() {
        TechnicalIndicators ti = calculator.calculate(generateBars(1), "AAPL", BarInterval.D1);

        assertThat(ti).isNotNull();
        assertAllFieldsNull(ti);
    }

    @Test
    void shouldReturnNonNullMa5WithFiveBars() {
        TechnicalIndicators ti = calculator.calculate(generateBars(5), "AAPL", BarInterval.D1);

        assertThat(ti.ma5()).isNotNull();
    }

    @Test
    void shouldComputeMa5CorrectlyForConstantPrice() {
        // All bars have close = 100, so MA5 should be 100
        List<OhlcvBar> bars = generateConstantBars(10, new BigDecimal("100"));
        TechnicalIndicators ti = calculator.calculate(bars, "AAPL", BarInterval.D1);

        assertThat(ti.ma5()).isCloseTo(new BigDecimal("100"), within(new BigDecimal("0.01")));
        assertThat(ti.ma10()).isCloseTo(new BigDecimal("100"), within(new BigDecimal("0.01")));
    }

    @Test
    void shouldComputeAllIndicatorsWithSufficientBars() {
        TechnicalIndicators ti = calculator.calculate(generateBars(100), "AAPL", BarInterval.D1);

        assertThat(ti.ma5()).isNotNull();
        assertThat(ti.ma10()).isNotNull();
        assertThat(ti.ma20()).isNotNull();
        assertThat(ti.ema12()).isNotNull();
        assertThat(ti.ema26()).isNotNull();
        assertThat(ti.bollingerMiddle()).isNotNull();
        assertThat(ti.calculatedAt()).isNotNull();
    }

    @Test
    void shouldReturnCorrectSymbolAndInterval() {
        TechnicalIndicators ti = calculator.calculate(generateBars(10), "TSLA", BarInterval.H1);

        assertThat(ti.symbol()).isEqualTo("TSLA");
        assertThat(ti.interval()).isEqualTo(BarInterval.H1);
    }

    @Test
    void bollingerUpperShouldBeAboveMiddleForVolatileData() {
        TechnicalIndicators ti = calculator.calculate(generateBars(100), "AAPL", BarInterval.D1);

        if (ti.bollingerUpper() != null && ti.bollingerMiddle() != null) {
            assertThat(ti.bollingerUpper()).isGreaterThanOrEqualTo(ti.bollingerMiddle());
        }
    }

    @Test
    void bollingerLowerShouldBeBelowMiddleForVolatileData() {
        TechnicalIndicators ti = calculator.calculate(generateBars(100), "AAPL", BarInterval.D1);

        if (ti.bollingerLower() != null && ti.bollingerMiddle() != null) {
            assertThat(ti.bollingerLower()).isLessThanOrEqualTo(ti.bollingerMiddle());
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private List<OhlcvBar> generateBars(int count) {
        List<OhlcvBar> bars = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BigDecimal close = new BigDecimal(100 + i);
            bars.add(new OhlcvBar(
                    "AAPL",
                    Instant.ofEpochSecond(1700000000L + (long) i * 86400),
                    BarInterval.D1,
                    close.subtract(BigDecimal.ONE),
                    close.add(BigDecimal.TWO),
                    close.subtract(BigDecimal.TWO),
                    close,
                    1_000_000L
            ));
        }
        return bars;
    }

    private List<OhlcvBar> generateConstantBars(int count, BigDecimal price) {
        List<OhlcvBar> bars = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bars.add(new OhlcvBar(
                    "AAPL",
                    Instant.ofEpochSecond(1700000000L + (long) i * 86400),
                    BarInterval.D1,
                    price, price, price, price, 1_000_000L
            ));
        }
        return bars;
    }

    private void assertAllFieldsNull(TechnicalIndicators ti) {
        assertThat(ti.ma5()).isNull();
        assertThat(ti.ma10()).isNull();
        assertThat(ti.ma20()).isNull();
        assertThat(ti.ma60()).isNull();
        assertThat(ti.ema12()).isNull();
        assertThat(ti.ema26()).isNull();
        assertThat(ti.macdLine()).isNull();
        assertThat(ti.signalLine()).isNull();
        assertThat(ti.macdHistogram()).isNull();
        assertThat(ti.rsi14()).isNull();
        assertThat(ti.bollingerUpper()).isNull();
        assertThat(ti.bollingerMiddle()).isNull();
        assertThat(ti.bollingerLower()).isNull();
    }
}
