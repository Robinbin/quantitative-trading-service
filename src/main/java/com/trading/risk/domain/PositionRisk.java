package com.trading.risk.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Risk assessment for a single stock position.
 *
 * @param symbol                 ticker symbol
 * @param quantity               shares held
 * @param entryPrice             average entry (cost basis) per share
 * @param currentPrice           current market price per share
 * @param marketValue            current market value (currentPrice × quantity)
 * @param unrealizedPnl          unrealized profit/loss in currency units
 * @param unrealizedPnlPct       unrealized P/L as fraction of cost basis (e.g. 0.05 = +5%)
 * @param stopLossPrice          suggested stop-loss price (entryPrice × (1 − stopLossPct))
 * @param takeProfitPrice        suggested take-profit price (entryPrice × (1 + takeProfitPct))
 * @param positionVar95          1-day VaR at 95% confidence for the full position size
 * @param volatilityAnnualized   annualized historical volatility
 * @param beta                   market beta; null if unavailable
 * @param riskLevel              categorical risk level
 * @param warnings               human-readable risk warnings
 * @param evaluatedAt            evaluation timestamp
 */
public record PositionRisk(
        String symbol,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedPnlPct,
        BigDecimal stopLossPrice,
        BigDecimal takeProfitPrice,
        BigDecimal positionVar95,
        BigDecimal volatilityAnnualized,
        BigDecimal beta,
        RiskLevel riskLevel,
        List<String> warnings,
        Instant evaluatedAt
) {}
