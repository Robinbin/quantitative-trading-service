package com.trading.risk.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Aggregated risk assessment for a multi-position portfolio.
 *
 * @param positions            individual position risk assessments
 * @param totalMarketValue     sum of all position market values
 * @param totalUnrealizedPnl   total unrealized P/L across all positions
 * @param totalUnrealizedPnlPct total P/L as fraction of total cost basis
 * @param portfolioVar95       diversified portfolio 1-day VaR (√Σ VaR_i²), assuming zero correlation
 * @param weightedBeta         market-value-weighted average beta
 * @param concentrationIndex   Herfindahl-Hirschman Index [0,1]; closer to 1 = more concentrated
 * @param overallRiskLevel     highest risk level among all positions
 * @param warnings             portfolio-level risk warnings
 * @param evaluatedAt          evaluation timestamp
 */
public record PortfolioRisk(
        List<PositionRisk> positions,
        BigDecimal totalMarketValue,
        BigDecimal totalUnrealizedPnl,
        BigDecimal totalUnrealizedPnlPct,
        BigDecimal portfolioVar95,
        BigDecimal weightedBeta,
        BigDecimal concentrationIndex,
        RiskLevel overallRiskLevel,
        List<String> warnings,
        Instant evaluatedAt
) {}
