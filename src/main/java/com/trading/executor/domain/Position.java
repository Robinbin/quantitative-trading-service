package com.trading.executor.domain;

import java.math.BigDecimal;

/**
 * A position enriched with live market price at the time of the query.
 */
public record Position(
        String     symbol,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedPnlPct
) {}
