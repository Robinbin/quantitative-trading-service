package com.trading.executor.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Aggregated portfolio snapshot.
 *
 * @param cashBalance        uninvested cash
 * @param positions          list of open positions (enriched with live price)
 * @param totalMarketValue   sum of all position market values
 * @param totalUnrealizedPnl total unrealised P/L across all positions
 * @param totalValue         cashBalance + totalMarketValue
 * @param evaluatedAt        snapshot timestamp
 */
public record TradingPortfolio(
        BigDecimal     cashBalance,
        List<Position> positions,
        BigDecimal     totalMarketValue,
        BigDecimal     totalUnrealizedPnl,
        BigDecimal     totalValue,
        Instant        evaluatedAt
) {}
