package com.trading.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record TickQuote(
        String     symbol,
        Instant    timestamp,
        BigDecimal price,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePercent,
        long       volume,
        BigDecimal marketCap,
        String     currency,
        String     exchangeName
) {}
