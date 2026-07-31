package com.trading.risk.calculator;

import com.trading.marketdata.domain.OhlcvBar;
import com.trading.risk.config.RiskProperties;
import com.trading.risk.domain.RiskLevel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-function risk calculations.  No external I/O — safe to call from tests without
 * mocking any infrastructure.
 */
@Component
public class RiskCalculator {

    private static final double TRADING_DAYS_PER_YEAR = 252.0;

    /** 95th-percentile z-score for parametric VaR. */
    private static final double Z_95 = 1.6449;

    private static final int SCALE = 6;

    // ------------------------------------------------------------------
    // Volatility
    // ------------------------------------------------------------------

    /**
     * Annualised historical volatility computed as the standard deviation of daily
     * log-returns multiplied by √252.
     *
     * @param bars time-ordered OHLCV bars (at least 2 required; returns ZERO otherwise)
     * @return annualised volatility as a fraction (e.g. 0.3 = 30%)
     */
    public BigDecimal calculateVolatility(List<OhlcvBar> bars) {
        if (bars == null || bars.size() < 2) return BigDecimal.ZERO;

        List<Double> logReturns = new ArrayList<>(bars.size() - 1);
        for (int i = 1; i < bars.size(); i++) {
            double prev = bars.get(i - 1).close().doubleValue();
            double curr = bars.get(i).close().doubleValue();
            if (prev > 0 && curr > 0) {
                logReturns.add(Math.log(curr / prev));
            }
        }

        if (logReturns.isEmpty()) return BigDecimal.ZERO;

        double mean = logReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = logReturns.stream()
                .mapToDouble(r -> (r - mean) * (r - mean))
                .average()
                .orElse(0.0);

        double annualisedVol = Math.sqrt(variance) * Math.sqrt(TRADING_DAYS_PER_YEAR);
        return BigDecimal.valueOf(annualisedVol).setScale(SCALE, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Value at Risk
    // ------------------------------------------------------------------

    /**
     * Parametric 1-day VaR at 95% confidence per unit of stock.
     *
     * <p>Formula: price × (annualisedVol / √252) × Z₉₅
     *
     * @param price                current price per share
     * @param volatilityAnnualized annualised volatility (fraction)
     * @return 1-day VaR per share
     */
    public BigDecimal calculateDailyVar95(BigDecimal price, BigDecimal volatilityAnnualized) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        if (volatilityAnnualized == null || volatilityAnnualized.compareTo(BigDecimal.ZERO) <= 0)
            return BigDecimal.ZERO;

        double dailyVol = volatilityAnnualized.doubleValue() / Math.sqrt(TRADING_DAYS_PER_YEAR);
        double var = price.doubleValue() * dailyVol * Z_95;
        return BigDecimal.valueOf(var).setScale(SCALE, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Maximum Drawdown
    // ------------------------------------------------------------------

    /**
     * Maximum drawdown as a positive fraction: max((peak − trough) / peak) over all bars.
     *
     * @param bars time-ordered OHLCV bars
     * @return maximum drawdown fraction (e.g. 0.25 = 25%)
     */
    public BigDecimal calculateMaxDrawdown(List<OhlcvBar> bars) {
        if (bars == null || bars.isEmpty()) return BigDecimal.ZERO;

        double peak = Double.MIN_VALUE;
        double maxDrawdown = 0.0;

        for (OhlcvBar bar : bars) {
            double price = bar.close().doubleValue();
            if (price > peak) peak = price;
            if (peak > 0) {
                double drawdown = (peak - price) / peak;
                if (drawdown > maxDrawdown) maxDrawdown = drawdown;
            }
        }

        return BigDecimal.valueOf(maxDrawdown).setScale(SCALE, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Sharpe Ratio
    // ------------------------------------------------------------------

    /**
     * Annualised Sharpe ratio: (mean_annual_return − risk_free_rate) / annual_std_dev.
     * Uses simple daily returns (not log-returns) for the Sharpe computation.
     *
     * @param bars         time-ordered OHLCV bars (at least 2 required)
     * @param riskFreeRate annual risk-free rate as a fraction (e.g. 0.05 = 5%)
     * @return annualised Sharpe ratio; ZERO if insufficient data
     */
    public BigDecimal calculateSharpeRatio(List<OhlcvBar> bars, double riskFreeRate) {
        if (bars == null || bars.size() < 2) return BigDecimal.ZERO;

        List<Double> returns = new ArrayList<>(bars.size() - 1);
        for (int i = 1; i < bars.size(); i++) {
            double prev = bars.get(i - 1).close().doubleValue();
            double curr = bars.get(i).close().doubleValue();
            if (prev > 0) returns.add((curr - prev) / prev);
        }

        if (returns.isEmpty()) return BigDecimal.ZERO;

        double meanDaily = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
                .mapToDouble(r -> (r - meanDaily) * (r - meanDaily))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        if (stdDev == 0.0) return BigDecimal.ZERO;

        double annualReturn = meanDaily * TRADING_DAYS_PER_YEAR;
        double annualStdDev = stdDev * Math.sqrt(TRADING_DAYS_PER_YEAR);
        double sharpe = (annualReturn - riskFreeRate) / annualStdDev;

        return BigDecimal.valueOf(sharpe).setScale(SCALE, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Risk Level
    // ------------------------------------------------------------------

    /**
     * Map annualised volatility to a categorical {@link RiskLevel} using configurable thresholds.
     *
     * @param volatilityAnnualized annualised volatility fraction
     * @param props                risk configuration (volatility thresholds)
     * @return categorical risk level
     */
    public RiskLevel determineRiskLevel(BigDecimal volatilityAnnualized, RiskProperties props) {
        double vol = (volatilityAnnualized == null) ? 0.0 : volatilityAnnualized.doubleValue();
        RiskProperties.VolatilityThresholds t = props.volatilityThresholds();
        if (vol < t.low())    return RiskLevel.LOW;
        if (vol < t.medium()) return RiskLevel.MEDIUM;
        if (vol < t.high())   return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }
}
