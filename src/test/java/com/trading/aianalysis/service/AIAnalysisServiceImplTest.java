package com.trading.aianalysis.service;

import com.trading.aianalysis.domain.AnalysisResult;
import com.trading.marketdata.config.MarketDataProperties;
import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.TechnicalIndicators;
import com.trading.marketdata.domain.TickQuote;
import com.trading.marketdata.service.MarketDataService;
import com.trading.risk.domain.RiskLevel;
import com.trading.risk.domain.RiskMetrics;
import com.trading.risk.service.RiskService;
import com.trading.strategy.domain.Signal;
import com.trading.strategy.domain.StrategyResult;
import com.trading.strategy.service.StrategyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIAnalysisServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;

    @Mock MarketDataService    marketDataService;
    @Mock StrategyService      strategyService;
    @Mock RiskService          riskService;
    @Mock MarketDataProperties marketDataProps;

    private AIAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AIAnalysisServiceImpl(chatClient, marketDataService,
                strategyService, riskService, marketDataProps);
    }

    // ------------------------------------------------------------------
    // analyzeSymbol — happy path
    // ------------------------------------------------------------------

    @Test
    void analyzeSymbol_returnsCorrectFields_forValidJsonResponse() {
        stubDependencies("AAPL");
        stubChatClient("""
                {"summary":"Strong uptrend on high volume.","recommendation":"BUY","confidence":0.82,"keyFactors":["RSI oversold","MACD cross","Low volatility"]}
                """);

        AnalysisResult result = service.analyzeSymbol("AAPL", BarInterval.D1);

        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.recommendation()).isEqualTo(Signal.BUY);
        assertThat(result.confidence().doubleValue()).isEqualTo(0.82);
        assertThat(result.keyFactors()).containsExactly("RSI oversold", "MACD cross", "Low volatility");
        assertThat(result.summary()).contains("uptrend");
        assertThat(result.generatedAt()).isNotNull();
    }

    @Test
    void analyzeSymbol_returnsFallback_whenJsonIsUnparseable() {
        stubDependencies("AAPL");
        stubChatClient("not-valid-json {{");

        AnalysisResult result = service.analyzeSymbol("AAPL", BarInterval.D1);

        assertThat(result.recommendation()).isEqualTo(Signal.HOLD);
        assertThat(result.confidence().doubleValue()).isEqualTo(0.50);
        assertThat(result.keyFactors()).contains("Data unavailable");
    }

    @Test
    void analyzeSymbol_returnsFallback_whenChatClientThrows() {
        stubDependencies("AAPL");
        when(chatClient.prompt().user(any(String.class)).call().content())
                .thenThrow(new RuntimeException("AI service unavailable"));

        AnalysisResult result = service.analyzeSymbol("AAPL", BarInterval.D1);

        assertThat(result.recommendation()).isEqualTo(Signal.HOLD);
        assertThat(result.summary()).contains("unavailable");
    }

    @Test
    void analyzeSymbol_defaultsToHold_whenRecommendationEnumIsInvalid() {
        stubDependencies("AAPL");
        stubChatClient("""
                {"summary":"Mixed signals.","recommendation":"STRONG_BUY","confidence":0.9,"keyFactors":["test"]}
                """);

        AnalysisResult result = service.analyzeSymbol("AAPL", BarInterval.D1);

        assertThat(result.recommendation()).isEqualTo(Signal.HOLD);
    }

    @Test
    void analyzeSymbol_clampsConfidence_whenOutOfRange() {
        stubDependencies("AAPL");
        stubChatClient("""
                {"summary":"Test.","recommendation":"SELL","confidence":1.5,"keyFactors":[]}
                """);

        AnalysisResult result = service.analyzeSymbol("AAPL", BarInterval.D1);

        assertThat(result.confidence().doubleValue()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void analyzeSymbol_handlesNullIndicatorFields_withoutNpe() {
        when(marketDataService.getTick("AAPL")).thenReturn(sampleTick("AAPL", BigDecimal.valueOf(180)));
        when(marketDataService.getIndicators("AAPL", BarInterval.D1)).thenReturn(
                // All BigDecimal fields null
                new TechnicalIndicators("AAPL", BarInterval.D1, Instant.now(),
                        null, null, null, null, null, null,
                        null, null, null, null, null, null, null));
        when(strategyService.evaluateAll("AAPL", BarInterval.D1)).thenReturn(List.of());
        when(riskService.assessSymbol("AAPL", BarInterval.D1)).thenReturn(sampleRiskMetrics("AAPL"));
        stubChatClient("""
                {"summary":"Analysis.","recommendation":"HOLD","confidence":0.5,"keyFactors":["n/a"]}
                """);

        AnalysisResult result = service.analyzeSymbol("AAPL", BarInterval.D1);
        assertThat(result).isNotNull();
    }

    @Test
    void analyzeSymbol_returnsFallback_whenMarketDataThrows() {
        when(marketDataService.getTick("ERR")).thenThrow(new RuntimeException("network error"));

        AnalysisResult result = service.analyzeSymbol("ERR", BarInterval.D1);

        assertThat(result.recommendation()).isEqualTo(Signal.HOLD);
        assertThat(result.symbol()).isEqualTo("ERR");
    }

    // ------------------------------------------------------------------
    // scanAnalysis
    // ------------------------------------------------------------------

    @Test
    void scanAnalysis_returnsResultsForAllWatchListSymbols() {
        when(marketDataProps.watchList()).thenReturn(List.of("AAPL", "TSLA"));
        stubDependenciesForSymbol("AAPL");
        stubDependenciesForSymbol("TSLA");
        when(chatClient.prompt().user(any(String.class)).call().content())
                .thenReturn("{\"summary\":\"ok\",\"recommendation\":\"HOLD\",\"confidence\":0.5,\"keyFactors\":[]}");

        var result = service.scanAnalysis(BarInterval.D1);

        assertThat(result).containsKeys("AAPL", "TSLA");
    }

    @Test
    void scanAnalysis_returnsFallbackForFailingSymbol() {
        when(marketDataProps.watchList()).thenReturn(List.of("AAPL", "BAD"));
        stubDependenciesForSymbol("AAPL");
        when(marketDataService.getTick("BAD")).thenThrow(new RuntimeException("not found"));
        when(chatClient.prompt().user(any(String.class)).call().content())
                .thenReturn("{\"summary\":\"ok\",\"recommendation\":\"BUY\",\"confidence\":0.8,\"keyFactors\":[\"f1\"]}");

        var result = service.scanAnalysis(BarInterval.D1);

        assertThat(result).containsKey("AAPL");
        assertThat(result).containsKey("BAD");
        assertThat(result.get("BAD").recommendation()).isEqualTo(Signal.HOLD);
    }

    @Test
    void scanAnalysis_returnsEmptyMap_forEmptyWatchList() {
        when(marketDataProps.watchList()).thenReturn(List.of());

        assertThat(service.scanAnalysis(BarInterval.D1)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void stubDependencies(String symbol) {
        when(marketDataService.getTick(symbol)).thenReturn(sampleTick(symbol, BigDecimal.valueOf(180)));
        when(marketDataService.getIndicators(symbol, BarInterval.D1)).thenReturn(sampleIndicators(symbol));
        when(strategyService.evaluateAll(symbol, BarInterval.D1)).thenReturn(
                List.of(new StrategyResult("RSI", symbol, Signal.BUY, BigDecimal.valueOf(0.7), "RSI oversold", Instant.now())));
        when(riskService.assessSymbol(symbol, BarInterval.D1)).thenReturn(sampleRiskMetrics(symbol));
    }

    private void stubDependenciesForSymbol(String symbol) {
        when(marketDataService.getTick(symbol)).thenReturn(sampleTick(symbol, BigDecimal.valueOf(180)));
        when(marketDataService.getIndicators(symbol, BarInterval.D1)).thenReturn(sampleIndicators(symbol));
        when(strategyService.evaluateAll(symbol, BarInterval.D1)).thenReturn(List.of());
        when(riskService.assessSymbol(symbol, BarInterval.D1)).thenReturn(sampleRiskMetrics(symbol));
    }

    private void stubChatClient(String json) {
        when(chatClient.prompt().user(any(String.class)).call().content()).thenReturn(json);
    }

    private TickQuote sampleTick(String symbol, BigDecimal price) {
        return new TickQuote(symbol, Instant.now(), price,
                null, null, null, null, null, null, 0L, null, "USD", "NMS");
    }

    private TechnicalIndicators sampleIndicators(String symbol) {
        return new TechnicalIndicators(symbol, BarInterval.D1, Instant.now(),
                BigDecimal.valueOf(150), BigDecimal.valueOf(160), BigDecimal.valueOf(170), BigDecimal.valueOf(165),
                BigDecimal.valueOf(168), BigDecimal.valueOf(162),
                BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.3), BigDecimal.valueOf(0.2),
                BigDecimal.valueOf(35.0),
                BigDecimal.valueOf(185), BigDecimal.valueOf(175), BigDecimal.valueOf(165));
    }

    private RiskMetrics sampleRiskMetrics(String symbol) {
        return new RiskMetrics(symbol, BarInterval.D1,
                BigDecimal.valueOf(180), BigDecimal.valueOf(0.25), BigDecimal.valueOf(1.1),
                BigDecimal.valueOf(1.2), BigDecimal.valueOf(0.15), BigDecimal.valueOf(5.0),
                RiskLevel.MEDIUM, Instant.now());
    }
}
