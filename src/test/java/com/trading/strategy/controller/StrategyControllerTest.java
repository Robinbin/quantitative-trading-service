package com.trading.strategy.controller;

import com.trading.marketdata.domain.BarInterval;
import com.trading.strategy.domain.Signal;
import com.trading.strategy.domain.StrategyDefinition;
import com.trading.strategy.domain.StrategyResult;
import com.trading.strategy.service.StrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StrategyController.class)
class StrategyControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    StrategyService service;

    @Test
    void listStrategies_shouldReturn200WithDefinitions() throws Exception {
        when(service.listStrategies()).thenReturn(List.of(
                new StrategyDefinition("RSI", "RSI strategy"),
                new StrategyDefinition("MACD", "MACD strategy")));

        mockMvc.perform(get("/api/v1/strategy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("RSI"))
                .andExpect(jsonPath("$[1].name").value("MACD"));
    }

    @Test
    void evaluate_shouldReturn200WithResult() throws Exception {
        when(service.evaluate("AAPL", "RSI", BarInterval.D1)).thenReturn(sampleResult("RSI"));

        mockMvc.perform(get("/api/v1/strategy/AAPL/evaluate")
                        .param("strategy", "RSI")
                        .param("interval", "D1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyName").value("RSI"))
                .andExpect(jsonPath("$.signal").value("HOLD"));
    }

    @Test
    void evaluate_shouldUpcaseSymbol() throws Exception {
        when(service.evaluate(eq("AAPL"), any(), any())).thenReturn(sampleResult("RSI"));

        mockMvc.perform(get("/api/v1/strategy/aapl/evaluate")
                        .param("strategy", "RSI"))
                .andExpect(status().isOk());

        verify(service).evaluate("AAPL", "RSI", BarInterval.D1);
    }

    @Test
    void evaluate_shouldUseDefaultInterval() throws Exception {
        when(service.evaluate("AAPL", "RSI", BarInterval.D1)).thenReturn(sampleResult("RSI"));

        mockMvc.perform(get("/api/v1/strategy/AAPL/evaluate")
                        .param("strategy", "RSI"))
                .andExpect(status().isOk());

        verify(service).evaluate("AAPL", "RSI", BarInterval.D1);
    }

    @Test
    void evaluate_shouldReturn400ForUnknownStrategy() throws Exception {
        when(service.evaluate(any(), eq("UNKNOWN"), any()))
                .thenThrow(new IllegalArgumentException("Unknown strategy: UNKNOWN"));

        mockMvc.perform(get("/api/v1/strategy/AAPL/evaluate")
                        .param("strategy", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown strategy: UNKNOWN"));
    }

    @Test
    void evaluateAll_shouldReturn200WithList() throws Exception {
        when(service.evaluateAll("AAPL", BarInterval.D1))
                .thenReturn(List.of(sampleResult("RSI"), sampleResult("MACD")));

        mockMvc.perform(get("/api/v1/strategy/AAPL/evaluate-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].strategyName").value("RSI"))
                .andExpect(jsonPath("$[1].strategyName").value("MACD"));
    }

    @Test
    void scan_shouldReturn200WithMap() throws Exception {
        when(service.scan(BarInterval.D1))
                .thenReturn(Map.of("AAPL", List.of(sampleResult("RSI"))));

        mockMvc.perform(get("/api/v1/strategy/scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AAPL[0].strategyName").value("RSI"));
    }

    @Test
    void shouldReturn502WhenServiceThrows() throws Exception {
        when(service.evaluateAll(any(), any()))
                .thenThrow(new RuntimeException("upstream failure"));

        mockMvc.perform(get("/api/v1/strategy/ERROR/evaluate-all"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("upstream failure"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private StrategyResult sampleResult(String strategyName) {
        return new StrategyResult(strategyName, "AAPL", Signal.HOLD,
                BigDecimal.ZERO, "test reason", Instant.now());
    }
}
