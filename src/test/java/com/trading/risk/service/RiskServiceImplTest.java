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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskServiceImplTest {

    @Mock MarketDataService    marketDataService;
    @Mock RiskCalculator       riskCalculator;
    @Mock MarketDataProperties marketDataProps;

    private RiskProperties   riskProps;
    private RiskServiceImpl  service;

    @BeforeEach
    void setUp() {
        riskProps = new RiskProperties(0.05, 0.15, 0.05,
                new RiskProperties.VolatilityThresholds(0.20, 0.40, 0.60));
        service = new RiskServiceImpl(marketDataService, riskCalculator, riskProps, marketDataProps);
    }

    // ------------------------------------------------------------------
    // assessSymbol
    // ------------------------------------------------------------------

    @Test
    void assessSymbol_returnsRiskMetrics_withBeta() {
        stubMarketData("AAPL", BigDecimal.valueOf(180));
        stubFundamental("AAPL", BigDecimal.valueOf(1.2));
        stubCalculations(BigDecimal.valueOf(0.25), BigDecimal.valueOf(5.0),
                BigDecimal.valueOf(0.15), BigDecimal.valueOf(1.2));

        RiskMetrics metrics = service.assessSymbol("AAPL", BarInterval.D1);

        assertThat(metrics.symbol()).isEqualTo("AAPL");
        assertThat(metrics.currentPrice()).isEqualByComparingTo(BigDecimal.valueOf(180));
        assertThat(metrics.beta()).isEqualByComparingTo(BigDecimal.valueOf(1.2));
        assertThat(metrics.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(metrics.calculatedAt()).isNotNull();
    }

    @Test
    void assessSymbol_handlesNullFundamentals() {
        stubMarketData("TSLA", BigDecimal.valueOf(200));
        when(marketDataService.getFundamental("TSLA")).thenReturn(null);
        stubCalculations(BigDecimal.valueOf(0.60), BigDecimal.valueOf(8.0),
                BigDecimal.valueOf(0.30), BigDecimal.valueOf(1.5));

        RiskMetrics metrics = service.assessSymbol("TSLA", BarInterval.D1);

        assertThat(metrics.beta()).isNull();
        assertThat(metrics.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void assessSymbol_fetchesOhlcvAndTick() {
        stubMarketData("AAPL", BigDecimal.valueOf(180));
        stubFundamental("AAPL", null);
        stubCalculations(BigDecimal.valueOf(0.10), BigDecimal.valueOf(2.0),
                BigDecimal.valueOf(0.05), BigDecimal.valueOf(0.8));

        service.assessSymbol("AAPL", BarInterval.H1);

        verify(marketDataService).getTick("AAPL");
        verify(marketDataService).getOhlcv("AAPL", BarInterval.H1, 100);
        verify(marketDataService).getFundamental("AAPL");
    }

    // ------------------------------------------------------------------
    // assessPosition
    // ------------------------------------------------------------------

    @Test
    void assessPosition_computesMarketValueAndPnl() {
        // currentPrice=200, entry=150, qty=10
        stubMarketData("AAPL", BigDecimal.valueOf(200));
        stubFundamental("AAPL", BigDecimal.valueOf(1.1));
        stubCalculations(BigDecimal.valueOf(0.25), BigDecimal.valueOf(5.0),
                BigDecimal.valueOf(0.15), BigDecimal.valueOf(1.2));

        PositionRisk risk = service.assessPosition("AAPL", BigDecimal.valueOf(10), BigDecimal.valueOf(150));

        assertThat(risk.marketValue()).isEqualByComparingTo(BigDecimal.valueOf(2000).setScale(4));
        assertThat(risk.unrealizedPnl()).isEqualByComparingTo(BigDecimal.valueOf(500).setScale(4));
        assertThat(risk.unrealizedPnlPct().doubleValue()).isGreaterThan(0.30);
        assertThat(risk.warnings()).isEmpty(); // no warnings for a profitable position with MEDIUM risk
    }

    @Test
    void assessPosition_computesStopLossAndTakeProfit() {
        stubMarketData("AAPL", BigDecimal.valueOf(200));
        stubFundamental("AAPL", BigDecimal.valueOf(1.0));
        stubCalculations(BigDecimal.valueOf(0.10), BigDecimal.valueOf(3.0),
                BigDecimal.valueOf(0.05), BigDecimal.valueOf(0.9));

        PositionRisk risk = service.assessPosition("AAPL", BigDecimal.valueOf(5), BigDecimal.valueOf(100));

        // stopLoss = 100 × (1 − 0.05) = 95
        assertThat(risk.stopLossPrice().doubleValue()).isCloseTo(95.0, org.assertj.core.api.Assertions.within(0.001));
        // takeProfit = 100 × (1 + 0.15) = 115
        assertThat(risk.takeProfitPrice().doubleValue()).isCloseTo(115.0, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void assessPosition_addsWarning_forCriticalRisk() {
        stubMarketData("AAPL", BigDecimal.valueOf(100));
        stubFundamental("AAPL", BigDecimal.valueOf(2.0));
        // high vol triggers CRITICAL
        stubCalculations(BigDecimal.valueOf(0.70), BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(0.40), BigDecimal.valueOf(1.5));

        PositionRisk risk = service.assessPosition("AAPL", BigDecimal.valueOf(1), BigDecimal.valueOf(100));

        assertThat(risk.warnings()).anyMatch(w -> w.toLowerCase().contains("critical"));
    }

    @Test
    void assessPosition_addsWarning_forLargeUnrealizedLoss() {
        // currentPrice=75, entry=100 → pnl=-25% < -10% threshold
        stubMarketData("AAPL", BigDecimal.valueOf(75));
        stubFundamental("AAPL", null);
        stubCalculations(BigDecimal.valueOf(0.30), BigDecimal.valueOf(4.0),
                BigDecimal.valueOf(0.20), BigDecimal.valueOf(1.1));

        PositionRisk risk = service.assessPosition("AAPL", BigDecimal.valueOf(1), BigDecimal.valueOf(100));

        assertThat(risk.warnings()).anyMatch(w -> w.contains("10%"));
    }

    // ------------------------------------------------------------------
    // assessPortfolio
    // ------------------------------------------------------------------

    @Test
    void assessPortfolio_aggregatesMultiplePositions() {
        // AAPL: price=200, qty=5, entry=150
        // TSLA: price=300, qty=2, entry=250
        stubMarketDataForSymbol("AAPL", BigDecimal.valueOf(200));
        stubMarketDataForSymbol("TSLA", BigDecimal.valueOf(300));
        when(marketDataService.getFundamental("AAPL")).thenReturn(fundamental("AAPL", BigDecimal.valueOf(1.1)));
        when(marketDataService.getFundamental("TSLA")).thenReturn(fundamental("TSLA", BigDecimal.valueOf(1.8)));
        when(riskCalculator.calculateVolatility(any())).thenReturn(BigDecimal.valueOf(0.25));
        when(riskCalculator.calculateDailyVar95(any(), any())).thenReturn(BigDecimal.valueOf(5.0));
        when(riskCalculator.calculateMaxDrawdown(any())).thenReturn(BigDecimal.valueOf(0.15));
        when(riskCalculator.calculateSharpeRatio(any(), anyDouble())).thenReturn(BigDecimal.valueOf(1.2));
        when(riskCalculator.determineRiskLevel(any(), any())).thenReturn(RiskLevel.MEDIUM);

        List<PositionRequest> positions = List.of(
                new PositionRequest("AAPL", BigDecimal.valueOf(5), BigDecimal.valueOf(150)),
                new PositionRequest("TSLA", BigDecimal.valueOf(2), BigDecimal.valueOf(250)));

        PortfolioRisk portfolio = service.assessPortfolio(positions);

        // totalMarketValue = 200×5 + 300×2 = 1000 + 600 = 1600
        assertThat(portfolio.totalMarketValue().doubleValue()).isCloseTo(1600.0, org.assertj.core.api.Assertions.within(0.01));
        // totalCostBasis = 150×5 + 250×2 = 750 + 500 = 1250
        // unrealizedPnl = 1600 - 1250 = 350
        assertThat(portfolio.totalUnrealizedPnl().doubleValue()).isCloseTo(350.0, org.assertj.core.api.Assertions.within(0.01));
        assertThat(portfolio.positions()).hasSize(2);
        assertThat(portfolio.overallRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(portfolio.evaluatedAt()).isNotNull();
    }

    @Test
    void assessPortfolio_computesConcentrationWarning_forSinglePosition() {
        stubMarketData("AAPL", BigDecimal.valueOf(100));
        stubFundamental("AAPL", BigDecimal.valueOf(1.0));
        stubCalculations(BigDecimal.valueOf(0.10), BigDecimal.valueOf(2.0),
                BigDecimal.valueOf(0.05), BigDecimal.valueOf(0.9));

        PortfolioRisk portfolio = service.assessPortfolio(
                List.of(new PositionRequest("AAPL", BigDecimal.valueOf(10), BigDecimal.valueOf(100))));

        // HHI=1.0 for single position → concentration warning
        assertThat(portfolio.warnings()).anyMatch(w -> w.toLowerCase().contains("concentration") ||
                                                        w.toLowerCase().contains("single"));
    }

    // ------------------------------------------------------------------
    // scanRisk
    // ------------------------------------------------------------------

    @Test
    void scanRisk_evaluatesWatchListSymbols() {
        when(marketDataProps.watchList()).thenReturn(List.of("AAPL", "TSLA"));
        stubMarketDataForSymbol("AAPL", BigDecimal.valueOf(180));
        stubMarketDataForSymbol("TSLA", BigDecimal.valueOf(250));
        when(marketDataService.getFundamental(anyString())).thenReturn(null);
        when(riskCalculator.calculateVolatility(any())).thenReturn(BigDecimal.valueOf(0.25));
        when(riskCalculator.calculateDailyVar95(any(), any())).thenReturn(BigDecimal.valueOf(5.0));
        when(riskCalculator.calculateMaxDrawdown(any())).thenReturn(BigDecimal.valueOf(0.12));
        when(riskCalculator.calculateSharpeRatio(any(), anyDouble())).thenReturn(BigDecimal.valueOf(1.0));
        when(riskCalculator.determineRiskLevel(any(), any())).thenReturn(RiskLevel.MEDIUM);

        Map<String, RiskMetrics> result = service.scanRisk(BarInterval.D1);

        assertThat(result).containsKeys("AAPL", "TSLA");
    }

    @Test
    void scanRisk_omitsFailingSymbols() {
        when(marketDataProps.watchList()).thenReturn(List.of("AAPL", "BAD"));
        stubMarketDataForSymbol("AAPL", BigDecimal.valueOf(180));
        when(marketDataService.getFundamental("AAPL")).thenReturn(null);
        when(marketDataService.getTick("BAD")).thenThrow(new RuntimeException("network error"));
        when(riskCalculator.calculateVolatility(any())).thenReturn(BigDecimal.valueOf(0.20));
        when(riskCalculator.calculateDailyVar95(any(), any())).thenReturn(BigDecimal.valueOf(4.0));
        when(riskCalculator.calculateMaxDrawdown(any())).thenReturn(BigDecimal.valueOf(0.10));
        when(riskCalculator.calculateSharpeRatio(any(), anyDouble())).thenReturn(BigDecimal.valueOf(0.8));
        when(riskCalculator.determineRiskLevel(any(), any())).thenReturn(RiskLevel.LOW);

        Map<String, RiskMetrics> result = service.scanRisk(BarInterval.D1);

        assertThat(result).containsKey("AAPL");
        assertThat(result).doesNotContainKey("BAD");
    }

    @Test
    void scanRisk_returnsEmptyMap_forEmptyWatchList() {
        when(marketDataProps.watchList()).thenReturn(List.of());

        assertThat(service.scanRisk(BarInterval.D1)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void stubMarketData(String symbol, BigDecimal price) {
        when(marketDataService.getTick(symbol)).thenReturn(tick(symbol, price));
        when(marketDataService.getOhlcv(anyString(), any(), anyInt())).thenReturn(List.of());
    }

    private void stubMarketDataForSymbol(String symbol, BigDecimal price) {
        when(marketDataService.getTick(symbol)).thenReturn(tick(symbol, price));
        when(marketDataService.getOhlcv(eq(symbol), any(), anyInt())).thenReturn(List.of());
    }

    private void stubFundamental(String symbol, BigDecimal beta) {
        when(marketDataService.getFundamental(symbol))
                .thenReturn(beta == null ? null : fundamental(symbol, beta));
    }

    private void stubCalculations(BigDecimal vol, BigDecimal var95, BigDecimal sharpe, BigDecimal drawdown) {
        when(riskCalculator.calculateVolatility(any())).thenReturn(vol);
        when(riskCalculator.calculateDailyVar95(any(), any())).thenReturn(var95);
        when(riskCalculator.calculateSharpeRatio(any(), anyDouble())).thenReturn(sharpe);
        when(riskCalculator.calculateMaxDrawdown(any())).thenReturn(drawdown);
        when(riskCalculator.determineRiskLevel(any(), any()))
                .thenAnswer(inv -> {
                    double v = ((BigDecimal) inv.getArgument(0)).doubleValue();
                    if (v < 0.20) return RiskLevel.LOW;
                    if (v < 0.40) return RiskLevel.MEDIUM;
                    if (v < 0.60) return RiskLevel.HIGH;
                    return RiskLevel.CRITICAL;
                });
    }

    private TickQuote tick(String symbol, BigDecimal price) {
        return new TickQuote(symbol, Instant.now(), price,
                null, null, null, null, null, null, 0L, null, "USD", "NMS");
    }

    private FundamentalData fundamental(String symbol, BigDecimal beta) {
        return new FundamentalData(symbol, "Company", "Tech", "Software",
                BigDecimal.valueOf(25), BigDecimal.valueOf(22),
                BigDecimal.valueOf(5), BigDecimal.valueOf(8),
                BigDecimal.valueOf(1_000_000_000L), BigDecimal.valueOf(100_000_000L),
                BigDecimal.valueOf(3.5), BigDecimal.valueOf(0.01), beta,
                BigDecimal.valueOf(200), BigDecimal.valueOf(100), LocalDate.now());
    }
}
