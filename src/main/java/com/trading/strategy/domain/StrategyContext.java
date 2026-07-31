package com.trading.strategy.domain;

import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.TechnicalIndicators;
import com.trading.marketdata.domain.TickQuote;

public record StrategyContext(
        String               symbol,
        BarInterval          interval,
        TickQuote            tick,        // may be null
        TechnicalIndicators  indicators   // never null; individual fields may be null
) {}
