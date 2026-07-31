package com.trading.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "trading.market-data")
public record MarketDataProperties(
        List<String> watchList,
        int tickRefreshSeconds,
        int ohlcvRefreshMinutes
) {
    public MarketDataProperties {
        if (watchList == null) watchList = List.of();
        if (tickRefreshSeconds <= 0) tickRefreshSeconds = 30;
        if (ohlcvRefreshMinutes <= 0) ohlcvRefreshMinutes = 5;
    }
}
