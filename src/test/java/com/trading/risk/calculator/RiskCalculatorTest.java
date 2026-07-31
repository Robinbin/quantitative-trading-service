package com.trading.risk.calculator;

import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.OhlcvBar;
import com.trading.risk.config.RiskProperties;
import com.trading.risk.domain.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RiskCalculatorTest {

    private RiskCalculator calculator;
    private RiskProperties props;

    @BeforeEach
    void setUp() {
        calculator = new RiskCalculator();
        props = new RiskProperties(0.05, 0.15, 0.05,
                new RiskProperties.VolatilityThresholds(0.20, 0.40, 0.60));
    }

    // ------------------------------------------------------------------
    // Volatility
    // ------------------------------------------------------------------

    @Test
    void calculateVolatility_returnsZero_forNullInput() {
        assertThat(calculator.calculateVolatility(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateVolatility_returnsZero_forSingleBar() {
        assertThat(calculator.calculateVolatility(List.of(bar(100)))).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateVolatility_returnsZero_forEmptyList() {
        assertThat(calculator.calculateVolatility(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateVolatility_returnsZero_forConstantPrices() {
        List<OhlcvBar> bars = List.of(bar(100), bar(100), bar(100), bar(100), bar(100));
        assertThat(calculator.calculateVolatility(bars)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateVolatility_isPositive_forVolatileSeries() {
        // 10% daily moves → annualised vol ≈ 10% × √252 ≈ 158%
        List<OhlcvBar> bars = List.of(
                bar(100), bar(110), bar(99), bar(109), bar(98),
                bar(108), bar(97), bar(107), bar(96), bar(106));
        BigDecimal vol = calculator.calculateVolatility(bars);
        assertThat(vol).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void calculateVolatility_isHigherForMoreVolatileSeries() {
        List<OhlcvBar> stable = List.of(bar(100), bar(101), bar(100), bar(101), bar(100));
        List<OhlcvBar> volatile_ = List.of(bar(100), bar(120), bar(80), bar(130), bar(70));
        assertThat(calculator.calculateVolatility(volatile_))
                .isGreaterThan(calculator.calculateVolatility(stable));
    }

    // ------------------------------------------------------------------
    // VaR
    // ------------------------------------------------------------------

    @Test
    void calculateDailyVar95_returnsZero_forZeroPrice() {
        assertThat(calculator.calculateDailyVar95(BigDecimal.ZERO, BigDecimal.valueOf(0.3)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateDailyVar95_returnsZero_forZeroVolatility() {
        assertThat(calculator.calculateDailyVar95(BigDecimal.valueOf(100), BigDecimal.ZERO))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateDailyVar95_returnsPositiveValue() {
        // price=100, vol=30% → dailyVol=30%/√252≈1.89% → VaR≈1.89%×1.6449×100≈3.10
        BigDecimal var = calculator.calculateDailyVar95(
                BigDecimal.valueOf(100), BigDecimal.valueOf(0.30));
        assertThat(var).isGreaterThan(BigDecimal.ZERO);
        // sanity bound: should be roughly 3.10
        assertThat(var.doubleValue()).isCloseTo(3.10, within(0.20));
    }

    @Test
    void calculateDailyVar95_scalesLinearly_withPrice() {
        BigDecimal var100 = calculator.calculateDailyVar95(BigDecimal.valueOf(100), BigDecimal.valueOf(0.20));
        BigDecimal var200 = calculator.calculateDailyVar95(BigDecimal.valueOf(200), BigDecimal.valueOf(0.20));
        assertThat(var200.doubleValue()).isCloseTo(var100.doubleValue() * 2, within(0.001));
    }

    // ------------------------------------------------------------------
    // Max Drawdown
    // ------------------------------------------------------------------

    @Test
    void calculateMaxDrawdown_returnsZero_forEmptyList() {
        assertThat(calculator.calculateMaxDrawdown(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateMaxDrawdown_returnsZero_forMonotonicallyIncreasing() {
        List<OhlcvBar> bars = List.of(bar(100), bar(110), bar(120), bar(130));
        assertThat(calculator.calculateMaxDrawdown(bars)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateMaxDrawdown_computesCorrectly() {
        // peak=120 at index 2, trough=80 at index 3 → drawdown=(120-80)/120 ≈ 0.3333
        List<OhlcvBar> bars = List.of(bar(100), bar(110), bar(120), bar(80), bar(90));
        BigDecimal dd = calculator.calculateMaxDrawdown(bars);
        assertThat(dd.doubleValue()).isCloseTo(1.0 / 3.0, within(0.0001));
    }

    @Test
    void calculateMaxDrawdown_handlesMultiplePeaks() {
        // peak1=120→trough=100 (16.7%), peak2=150→trough=90 (40%)
        List<OhlcvBar> bars = List.of(bar(100), bar(120), bar(100), bar(150), bar(90));
        BigDecimal dd = calculator.calculateMaxDrawdown(bars);
        // max drawdown is 40% (150→90)
        assertThat(dd.doubleValue()).isCloseTo(0.40, within(0.001));
    }

    // ------------------------------------------------------------------
    // Sharpe Ratio
    // ------------------------------------------------------------------

    @Test
    void calculateSharpeRatio_returnsZero_forNullInput() {
        assertThat(calculator.calculateSharpeRatio(null, 0.05)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateSharpeRatio_returnsZero_forSingleBar() {
        assertThat(calculator.calculateSharpeRatio(List.of(bar(100)), 0.05))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateSharpeRatio_returnsZero_forConstantPrices() {
        List<OhlcvBar> bars = List.of(bar(100), bar(100), bar(100), bar(100));
        assertThat(calculator.calculateSharpeRatio(bars, 0.05)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateSharpeRatio_isPositive_forUpwardTrend() {
        // Steadily increasing prices → positive mean return, positive Sharpe
        List<OhlcvBar> bars = List.of(bar(100), bar(102), bar(104), bar(106), bar(108));
        assertThat(calculator.calculateSharpeRatio(bars, 0.0)).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void calculateSharpeRatio_isNegative_forDownwardTrend() {
        List<OhlcvBar> bars = List.of(bar(100), bar(98), bar(96), bar(94), bar(92));
        assertThat(calculator.calculateSharpeRatio(bars, 0.0)).isLessThan(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // Risk Level
    // ------------------------------------------------------------------

    @Test
    void determineRiskLevel_low_belowLowThreshold() {
        assertThat(calculator.determineRiskLevel(BigDecimal.valueOf(0.10), props))
                .isEqualTo(RiskLevel.LOW);
    }

    @Test
    void determineRiskLevel_medium_betweenLowAndMedium() {
        assertThat(calculator.determineRiskLevel(BigDecimal.valueOf(0.30), props))
                .isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void determineRiskLevel_high_betweenMediumAndHigh() {
        assertThat(calculator.determineRiskLevel(BigDecimal.valueOf(0.50), props))
                .isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void determineRiskLevel_critical_aboveHighThreshold() {
        assertThat(calculator.determineRiskLevel(BigDecimal.valueOf(0.80), props))
                .isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void determineRiskLevel_handlesNull() {
        assertThat(calculator.determineRiskLevel(null, props)).isEqualTo(RiskLevel.LOW);
    }

    // ------------------------------------------------------------------
    // RiskLevel helper
    // ------------------------------------------------------------------

    @Test
    void riskLevel_isHigherThan_comparesOrdinals() {
        assertThat(RiskLevel.CRITICAL.isHigherThan(RiskLevel.HIGH)).isTrue();
        assertThat(RiskLevel.LOW.isHigherThan(RiskLevel.MEDIUM)).isFalse();
        assertThat(RiskLevel.MEDIUM.isHigherThan(RiskLevel.MEDIUM)).isFalse();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private OhlcvBar bar(double close) {
        return new OhlcvBar("TEST", Instant.now(), BarInterval.D1,
                BigDecimal.valueOf(close), BigDecimal.valueOf(close),
                BigDecimal.valueOf(close), BigDecimal.valueOf(close), 1_000_000L);
    }
}
