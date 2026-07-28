package com.trading.marketdata.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataPropertiesTest {

    @Test
    void shouldApplyDefaultsWhenNullOrNegative() {
        var props = new MarketDataProperties(null, -1, 0);

        assertThat(props.watchList()).isEmpty();
        assertThat(props.tickRefreshSeconds()).isEqualTo(30);
        assertThat(props.ohlcvRefreshMinutes()).isEqualTo(5);
    }

    @Test
    void shouldPreserveValidValues() {
        var props = new MarketDataProperties(List.of("AAPL", "TSLA"), 60, 10);

        assertThat(props.watchList()).containsExactly("AAPL", "TSLA");
        assertThat(props.tickRefreshSeconds()).isEqualTo(60);
        assertThat(props.ohlcvRefreshMinutes()).isEqualTo(10);
    }

    @Test
    void shouldNotApplyDefaultsWhenValuesArePositive() {
        var props = new MarketDataProperties(List.of("MSFT"), 1, 1);

        assertThat(props.tickRefreshSeconds()).isEqualTo(1);
        assertThat(props.ohlcvRefreshMinutes()).isEqualTo(1);
    }
}
