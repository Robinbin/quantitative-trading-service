package com.trading.aianalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.aianalysis.domain.AnalysisResult;
import com.trading.marketdata.config.MarketDataProperties;
import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.TechnicalIndicators;
import com.trading.marketdata.domain.TickQuote;
import com.trading.marketdata.service.MarketDataService;
import com.trading.risk.domain.RiskMetrics;
import com.trading.risk.service.RiskService;
import com.trading.strategy.domain.Signal;
import com.trading.strategy.domain.StrategyResult;
import com.trading.strategy.service.StrategyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class AIAnalysisServiceImpl implements AIAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AIAnalysisServiceImpl.class);
    private static final int    CONF_SCALE = 2;

    private final ChatClient           chatClient;
    private final MarketDataService    marketDataService;
    private final StrategyService      strategyService;
    private final RiskService          riskService;
    private final MarketDataProperties marketDataProps;
    private final ObjectMapper         objectMapper;

    public AIAnalysisServiceImpl(ChatClient chatClient,
                                 MarketDataService marketDataService,
                                 StrategyService strategyService,
                                 RiskService riskService,
                                 MarketDataProperties marketDataProps) {
        this.chatClient        = chatClient;
        this.marketDataService = marketDataService;
        this.strategyService   = strategyService;
        this.riskService       = riskService;
        this.marketDataProps   = marketDataProps;
        this.objectMapper      = new ObjectMapper();
    }

    // ------------------------------------------------------------------
    // AIAnalysisService implementation
    // ------------------------------------------------------------------

    @Override
    public AnalysisResult analyzeSymbol(String symbol, BarInterval interval) {
        try {
            TickQuote           tick       = marketDataService.getTick(symbol);
            TechnicalIndicators indicators = marketDataService.getIndicators(symbol, interval);
            List<StrategyResult> signals   = strategyService.evaluateAll(symbol, interval);
            RiskMetrics         risk       = riskService.assessSymbol(symbol, interval);

            String prompt      = buildPrompt(symbol, interval, tick, indicators, signals, risk);
            String responseJson = chatClient.prompt().user(prompt).call().content();
            return parseResponse(responseJson, symbol, interval);
        } catch (Exception e) {
            log.warn("AI analysis failed for {}: {}", symbol, e.getMessage());
            return fallbackResult(symbol, interval);
        }
    }

    @Override
    public Map<String, AnalysisResult> scanAnalysis(BarInterval interval) {
        return marketDataProps.watchList().parallelStream()
                .collect(Collectors.toMap(
                        symbol -> symbol,
                        symbol -> {
                            try {
                                return analyzeSymbol(symbol, interval);
                            } catch (Exception e) {
                                log.warn("AI scan failed for {}: {}", symbol, e.getMessage());
                                return fallbackResult(symbol, interval);
                            }
                        },
                        (a, b) -> a,
                        TreeMap::new
                ));
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private String buildPrompt(String symbol,
                                BarInterval interval,
                                TickQuote tick,
                                TechnicalIndicators ind,
                                List<StrategyResult> signals,
                                RiskMetrics risk) {
        String price    = tick != null ? safeStr(tick.price()) : "N/A";
        String currency = tick != null && tick.currency() != null ? tick.currency() : "USD";

        return """
                You are an expert quantitative financial analyst. Analyze the following data for %s and provide a concise trading recommendation.

                Current Price: %s %s
                Interval: %s

                Technical Indicators:
                - RSI(14): %s
                - MACD: line=%s, signal=%s, histogram=%s
                - Bollinger Bands: upper=%s, middle=%s, lower=%s
                - Moving Averages: MA5=%s, MA20=%s, MA60=%s

                Strategy Signals (%d strategies evaluated):
                %s

                Risk Assessment:
                - Annualized Volatility: %s%%
                - 1-day VaR 95%%: %s
                - Max Drawdown: %s%%
                - Sharpe Ratio: %s
                - Risk Level: %s

                Respond with ONLY a JSON object (no markdown, no explanation) in this exact format:
                {"summary":"2-3 sentence market analysis","recommendation":"BUY|HOLD|SELL","confidence":0.75,"keyFactors":["factor1","factor2","factor3"]}
                """.formatted(
                symbol, price, currency, interval.name(),
                ind != null ? safeStr(ind.rsi14()) : "N/A",
                ind != null ? safeStr(ind.macdLine()) : "N/A",
                ind != null ? safeStr(ind.signalLine()) : "N/A",
                ind != null ? safeStr(ind.macdHistogram()) : "N/A",
                ind != null ? safeStr(ind.bollingerUpper()) : "N/A",
                ind != null ? safeStr(ind.bollingerMiddle()) : "N/A",
                ind != null ? safeStr(ind.bollingerLower()) : "N/A",
                ind != null ? safeStr(ind.ma5()) : "N/A",
                ind != null ? safeStr(ind.ma20()) : "N/A",
                ind != null ? safeStr(ind.ma60()) : "N/A",
                signals.size(),
                formatStrategyResults(signals),
                safeStr(risk.volatilityAnnualized()),
                safeStr(risk.var95Daily()),
                safeStr(risk.maxDrawdown()),
                safeStr(risk.sharpeRatio()),
                risk.riskLevel().name()
        );
    }

    private AnalysisResult parseResponse(String json, String symbol, BarInterval interval) {
        try {
            JsonNode root = objectMapper.readTree(json);

            String       summary = root.path("summary").asText("No summary available.");
            String       recStr  = root.path("recommendation").asText("HOLD").toUpperCase().trim();
            Signal       rec;
            try {
                rec = Signal.valueOf(recStr);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown recommendation '{}' from AI for {} — defaulting to HOLD", recStr, symbol);
                rec = Signal.HOLD;
            }

            double rawConf = root.path("confidence").asDouble(0.5);
            BigDecimal confidence = BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, rawConf)))
                    .setScale(CONF_SCALE, RoundingMode.HALF_UP);

            List<String> keyFactors = StreamSupport
                    .stream(root.path("keyFactors").spliterator(), false)
                    .map(JsonNode::asText)
                    .toList();

            return new AnalysisResult(symbol, interval, summary, rec, confidence, keyFactors, Instant.now());

        } catch (Exception e) {
            log.warn("Failed to parse AI response for {}: {}", symbol, e.getMessage());
            return fallbackResult(symbol, interval);
        }
    }

    private AnalysisResult fallbackResult(String symbol, BarInterval interval) {
        return new AnalysisResult(
                symbol, interval,
                "Analysis unavailable — please try again later.",
                Signal.HOLD,
                BigDecimal.valueOf(0.50).setScale(CONF_SCALE, RoundingMode.HALF_UP),
                List.of("Data unavailable"),
                Instant.now());
    }

    private String formatStrategyResults(List<StrategyResult> results) {
        if (results.isEmpty()) return "  (no strategy results available)";
        return results.stream()
                .map(r -> "  - " + r.strategyName() + ": " + r.signal()
                        + " (confidence=" + r.confidence() + ", reason=" + r.reason() + ")")
                .collect(Collectors.joining("\n"));
    }

    private String safeStr(BigDecimal val) {
        return val != null ? val.toPlainString() : "N/A";
    }
}
