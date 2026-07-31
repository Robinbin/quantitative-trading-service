package com.trading.marketdata.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BarIntervalTest {

    @Test
    void shouldHaveFourValues() {
        assertThat(BarInterval.values()).hasSize(4);
    }

    @Test
    void m1ShouldHaveCorrectProperties() {
        assertThat(BarInterval.M1.getYahooInterval()).isEqualTo("1m");
        assertThat(BarInterval.M1.getYahooRange()).isEqualTo("1d");
        assertThat(BarInterval.M1.getSeconds()).isEqualTo(60L);
    }

    @Test
    void m5ShouldHaveCorrectProperties() {
        assertThat(BarInterval.M5.getYahooInterval()).isEqualTo("5m");
        assertThat(BarInterval.M5.getYahooRange()).isEqualTo("5d");
        assertThat(BarInterval.M5.getSeconds()).isEqualTo(300L);
    }

    @Test
    void h1ShouldHaveCorrectProperties() {
        assertThat(BarInterval.H1.getYahooInterval()).isEqualTo("1h");
        assertThat(BarInterval.H1.getYahooRange()).isEqualTo("1mo");
        assertThat(BarInterval.H1.getSeconds()).isEqualTo(3600L);
    }

    @Test
    void d1ShouldHaveCorrectProperties() {
        assertThat(BarInterval.D1.getYahooInterval()).isEqualTo("1d");
        assertThat(BarInterval.D1.getYahooRange()).isEqualTo("1y");
        assertThat(BarInterval.D1.getSeconds()).isEqualTo(86400L);
    }

    @Test
    void secondsShouldIncreaseWithInterval() {
        assertThat(BarInterval.M1.getSeconds())
                .isLessThan(BarInterval.M5.getSeconds())
                .isLessThan(BarInterval.H1.getSeconds())
                .isLessThan(BarInterval.D1.getSeconds());
    }
}
