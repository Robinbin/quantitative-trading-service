package com.trading.risk.service;

import com.trading.marketdata.config.MarketDataProperties;
import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.FundamentalData;
import com.trading.marketdata.domain.OhlcvBar;
import com.trading.marketdata.domain.TickQuote;
import com.trading.marketdata.service.MarketDataService;
import com.trading.risk.calculator.RiskCalculator;
import com.trading.risk.config.RiskProperties;
import com.trading.risk.domain.PortfolioRisk;
import com.trading.risk.domain.PositionRequest;
import com.trading.risk.domain.PositionRisk;
import com.trading.risk.domain.RiskLevel;
import com.trading.risk.domain.RiskMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RiskServiceImpl implements RiskService {

    private static final Logger log = LoggerFactory.getLogger(RiskServiceImpl.class);

    /** Number of OHLCV bars to fetch for risk calculations. */
    private static final int OHLCV_LIMIT = 100;

    /** Scale used for monetary BigDecimal values. */
    private static final int MONEY_SCALE = 4;

    private final MarketDataService    marketDataService;
    private final RiskCalculator       riskCalculator;
    private final RiskProperties       props;
    private final MarketDataProperties marketDataProps;

    public RiskServiceImpl(MarketDataService marketDataService,
                           RiskCalculator riskCalculator,
                           RiskProperties props,
                           MarketDataProperties marketDataProps) {
        this.marketDataService = marketDataService;
        this.riskCalculator    = riskCalculator;
        this.props             = props;
        this.marketDataProps   = marketDataProps;
    }

    // ------------------------------------------------------------------
    // RiskService implementation
    // ------------------------------------------------------------------

    @Override
    public RiskMetrics assessSymbol(String symbol, BarInterval interval) {
        TickQuote       tick        = marketDataService.getTick(symbol);
        List<OhlcvBar>  bars        = marketDataService.getOhlcv(symbol, interval, OHLCV_LIMIT);
        FundamentalData fundamental = marketDataService.getFundamental(symbol);

        BigDecimal volatility  = riskCalculator.calculateVolatility(bars);
        BigDecimal var95       = riskCalculator.calculateDailyVar95(tick.price(), volatility);
        BigDecimal maxDrawdown = riskCalculator.calculateMaxDrawdown(bars);
        BigDecimal sharpe      = riskCalculator.calculateSharpeRatio(bars, props.riskFreeRate());
        BigDecimal beta        = (fundamental != null) ? fundamental.beta() : null;
        RiskLevel  riskLevel   = riskCalculator.determineRiskLevel(volatility, props);

        return new RiskMetrics(
                symbol, interval, tick.price(),
                volatility, beta, sharpe, maxDrawdown, var95,
                riskLevel, Instant.now());
    }

    @Override
    public PositionRisk assessPosition(String symbol, BigDecimal quantity, BigDecimal entryPrice) {
        RiskMetrics metrics = assessSymbol(symbol, BarInterval.D1);

        BigDecimal currentPrice  = metrics.currentPrice();
        BigDecimal marketValue   = currentPrice.multiply(quantity).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal costBasis     = entryPrice.multiply(quantity).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal unrealizedPnl = marketValue.subtract(costBasis).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal unrealizedPnlPct = costBasis.compareTo(BigDecimal.ZERO) != 0
                ? unrealizedPnl.divide(costBasis, MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal stopLoss   = entryPrice
                .multiply(BigDecimal.valueOf(1.0 - props.stopLossPct()))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal takeProfit = entryPrice
                .multiply(BigDecimal.valueOf(1.0 + props.takeProfitPct()))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal positionVar = metrics.var95Daily()
                .multiply(quantity)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        List<String> warnings = buildPositionWarnings(metrics.riskLevel(), unrealizedPnlPct);

        return new PositionRisk(
                symbol, quantity, entryPrice, currentPrice,
                marketValue, unrealizedPnl, unrealizedPnlPct,
                stopLoss, takeProfit, positionVar,
                metrics.volatilityAnnualized(), metrics.beta(),
                metrics.riskLevel(), List.copyOf(warnings),
                Instant.now());
    }

    @Override
    public PortfolioRisk assessPortfolio(List<PositionRequest> positions) {
        List<PositionRisk> assessed = positions.stream()
                .map(p -> assessPosition(p.symbol(), p.quantity(), p.entryPrice()))
                .toList();

        BigDecimal totalMarketValue = assessed.stream()
                .map(PositionRisk::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalCostBasis = assessed.stream()
                .map(p -> p.entryPrice().multiply(p.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalUnrealizedPnl = totalMarketValue.subtract(totalCostBasis)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalUnrealizedPnlPct = totalCostBasis.compareTo(BigDecimal.ZERO) != 0
                ? totalUnrealizedPnl.divide(totalCostBasis, MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Portfolio VaR: √(Σ VaR_i²)  — zero-correlation lower-bound estimate
        double sumVarSquared = assessed.stream()
                .mapToDouble(p -> p.positionVar95().doubleValue())
                .map(v -> v * v)
                .sum();
        BigDecimal portfolioVar = BigDecimal.valueOf(Math.sqrt(sumVarSquared))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // Market-value-weighted beta (positions without beta are excluded from the average)
        BigDecimal weightedBeta = computeWeightedBeta(assessed, totalMarketValue);

        // Herfindahl-Hirschman Index for concentration
        BigDecimal hhi = computeHHI(assessed, totalMarketValue);

        RiskLevel overallLevel = assessed.stream()
                .map(PositionRisk::riskLevel)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(RiskLevel.LOW);

        List<String> warnings = buildPortfolioWarnings(hhi, overallLevel, assessed.size());

        return new PortfolioRisk(
                assessed, totalMarketValue, totalUnrealizedPnl, totalUnrealizedPnlPct,
                portfolioVar, weightedBeta, hhi,
                overallLevel, List.copyOf(warnings), Instant.now());
    }

    @Override
    public Map<String, RiskMetrics> scanRisk(BarInterval interval) {
        ConcurrentHashMap<String, RiskMetrics> result = new ConcurrentHashMap<>();
        marketDataProps.watchList().parallelStream().forEach(symbol -> {
            try {
                result.put(symbol, assessSymbol(symbol, interval));
            } catch (Exception e) {
                log.warn("Risk scan failed for {}: {}", symbol, e.getMessage());
            }
        });
        return new TreeMap<>(result);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private List<String> buildPositionWarnings(RiskLevel level, BigDecimal unrealizedPnlPct) {
        List<String> warnings = new ArrayList<>();
        if (level == RiskLevel.CRITICAL) {
            warnings.add("Extremely high volatility — position carries critical risk");
        } else if (level == RiskLevel.HIGH) {
            warnings.add("High volatility detected — consider reducing position size");
        }
        if (unrealizedPnlPct.compareTo(BigDecimal.valueOf(-0.10)) < 0) {
            warnings.add("Position is down more than 10% — review stop-loss level");
        }
        if (unrealizedPnlPct.compareTo(BigDecimal.valueOf(-0.20)) < 0) {
            warnings.add("Position is down more than 20% — consider cutting losses");
        }
        return warnings;
    }

    private List<String> buildPortfolioWarnings(BigDecimal hhi, RiskLevel overallLevel, int positionCount) {
        List<String> warnings = new ArrayList<>();
        if (hhi.compareTo(BigDecimal.valueOf(0.25)) > 0) {
            warnings.add(String.format(
                    "High portfolio concentration (HHI=%.4f) — consider diversification", hhi.doubleValue()));
        }
        if (overallLevel == RiskLevel.CRITICAL) {
            warnings.add("One or more positions carry critical risk");
        }
        if (positionCount == 1) {
            warnings.add("Single-position portfolio — no diversification benefit");
        }
        return warnings;
    }

    private BigDecimal computeWeightedBeta(List<PositionRisk> positions, BigDecimal totalMarketValue) {
        if (totalMarketValue.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        BigDecimal betaSum = positions.stream()
                .filter(p -> p.beta() != null)
                .map(p -> p.beta().multiply(p.marketValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal betaWeightedTotal = positions.stream()
                .filter(p -> p.beta() != null)
                .map(PositionRisk::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (betaWeightedTotal.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return betaSum.divide(betaWeightedTotal, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal computeHHI(List<PositionRisk> positions, BigDecimal totalMarketValue) {
        if (positions.isEmpty() || totalMarketValue.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return positions.stream()
                .map(p -> {
                    BigDecimal w = p.marketValue().divide(totalMarketValue, 8, RoundingMode.HALF_UP);
                    return w.multiply(w);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
