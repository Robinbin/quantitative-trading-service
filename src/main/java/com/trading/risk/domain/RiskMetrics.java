package com.trading.risk.domain;

import com.trading.marketdata.domain.BarInterval;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Risk metrics for a single symbol computed from historical OHLCV data.
 *
 * @param symbol                ticker symbol
 * @param interval              bar interval used for computation
 * @param currentPrice          latest price at evaluation time
 * @param volatilityAnnualized  annualized historical volatility (log-return std dev × √252)
 * @param beta                  market beta from fundamental data; null if unavailable
 * @param sharpeRatio           annualized Sharpe ratio
 * @param maxDrawdown           maximum drawdown as a positive fraction (e.g. 0.15 = 15%)
 * @param var95Daily            parametric 1-day Value-at-Risk at 95% confidence per unit of stock
 * @param riskLevel             categorical risk level derived from volatility
 * @param calculatedAt          evaluation timestamp
 */
public record RiskMetrics(
        String symbol,
        BarInterval interval,
        BigDecimal currentPrice,
        BigDecimal volatilityAnnualized,
        BigDecimal beta,
        BigDecimal sharpeRatio,
        BigDecimal maxDrawdown,
        BigDecimal var95Daily,
        RiskLevel riskLevel,
        Instant calculatedAt
) {}
