package com.trading.risk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Risk-management configuration, bound from {@code trading.risk.*} in application.yml.
 *
 * @param stopLossPct          suggested stop-loss distance from entry as a fraction (default 0.05 = 5%)
 * @param takeProfitPct        suggested take-profit distance from entry as a fraction (default 0.15 = 15%)
 * @param riskFreeRate         annual risk-free rate used in Sharpe calculation (default 0.05 = 5%)
 * @param volatilityThresholds thresholds that map annualised volatility to {@link com.trading.risk.domain.RiskLevel}
 */
@ConfigurationProperties(prefix = "trading.risk")
public record RiskProperties(
        double stopLossPct,
        double takeProfitPct,
        double riskFreeRate,
        VolatilityThresholds volatilityThresholds
) {
    public RiskProperties {
        if (stopLossPct  <= 0) stopLossPct  = 0.05;
        if (takeProfitPct <= 0) takeProfitPct = 0.15;
        if (riskFreeRate  <  0) riskFreeRate  = 0.05;
        if (volatilityThresholds == null)
            volatilityThresholds = new VolatilityThresholds(0.20, 0.40, 0.60);
    }

    /**
     * Annualised volatility thresholds (fractions) used to categorise risk.
     *
     * <ul>
     *   <li>{@code vol < low}    → LOW</li>
     *   <li>{@code vol < medium} → MEDIUM</li>
     *   <li>{@code vol < high}   → HIGH</li>
     *   <li>otherwise            → CRITICAL</li>
     * </ul>
     */
    public record VolatilityThresholds(double low, double medium, double high) {
        public VolatilityThresholds {
            if (low    <= 0) low    = 0.20;
            if (medium <= 0) medium = 0.40;
            if (high   <= 0) high   = 0.60;
        }
    }
}
