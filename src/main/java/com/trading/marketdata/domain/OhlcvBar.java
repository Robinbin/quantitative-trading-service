package com.trading.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record OhlcvBar(
        String      symbol,
        Instant     timestamp,
        BarInterval interval,
        BigDecimal  open,
        BigDecimal  high,
        BigDecimal  low,
        BigDecimal  close,
        long        volume
) {}
