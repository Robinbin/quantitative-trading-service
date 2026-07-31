package com.trading.aianalysis.controller;

import com.trading.aianalysis.domain.AnalysisResult;
import com.trading.aianalysis.service.AIAnalysisService;
import com.trading.marketdata.domain.BarInterval;
import com.trading.strategy.domain.Signal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AIAnalysisController.class)
class AIAnalysisControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AIAnalysisService aiAnalysisService;

    // ------------------------------------------------------------------
    // GET /{symbol}/analyze
    // ------------------------------------------------------------------

    @Test
    void analyzeSymbol_returns200WithAnalysisResult() throws Exception {
        when(aiAnalysisService.analyzeSymbol("AAPL", BarInterval.D1)).thenReturn(sampleResult("AAPL", Signal.BUY));

        mockMvc.perform(get("/api/v1/ai/AAPL/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.recommendation").value("BUY"))
                .andExpect(jsonPath("$.confidence").value(0.75));
    }

    @Test
    void analyzeSymbol_upcasesSymbol() throws Exception {
        when(aiAnalysisService.analyzeSymbol(eq("AAPL"), any())).thenReturn(sampleResult("AAPL", Signal.HOLD));

        mockMvc.perform(get("/api/v1/ai/aapl/analyze"))
                .andExpect(status().isOk());

        verify(aiAnalysisService).analyzeSymbol("AAPL", BarInterval.D1);
    }

    @Test
    void analyzeSymbol_usesDefaultIntervalD1() throws Exception {
        when(aiAnalysisService.analyzeSymbol("AAPL", BarInterval.D1)).thenReturn(sampleResult("AAPL", Signal.HOLD));

        mockMvc.perform(get("/api/v1/ai/AAPL/analyze"))
                .andExpect(status().isOk());

        verify(aiAnalysisService).analyzeSymbol("AAPL", BarInterval.D1);
    }

    @Test
    void analyzeSymbol_passesIntervalParam() throws Exception {
        when(aiAnalysisService.analyzeSymbol("AAPL", BarInterval.H1)).thenReturn(sampleResult("AAPL", Signal.HOLD));

        mockMvc.perform(get("/api/v1/ai/AAPL/analyze").param("interval", "H1"))
                .andExpect(status().isOk());

        verify(aiAnalysisService).analyzeSymbol("AAPL", BarInterval.H1);
    }

    @Test
    void analyzeSymbol_returns502OnServiceError() throws Exception {
        when(aiAnalysisService.analyzeSymbol(any(), any()))
                .thenThrow(new RuntimeException("AI provider error"));

        mockMvc.perform(get("/api/v1/ai/AAPL/analyze"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("AI provider error"));
    }

    // ------------------------------------------------------------------
    // GET /scan
    // ------------------------------------------------------------------

    @Test
    void scan_returns200WithMap() throws Exception {
        when(aiAnalysisService.scanAnalysis(BarInterval.D1))
                .thenReturn(Map.of(
                        "AAPL", sampleResult("AAPL", Signal.BUY),
                        "TSLA", sampleResult("TSLA", Signal.HOLD)));

        mockMvc.perform(get("/api/v1/ai/scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AAPL.symbol").value("AAPL"))
                .andExpect(jsonPath("$.TSLA.symbol").value("TSLA"));
    }

    @Test
    void scan_passesIntervalParam() throws Exception {
        when(aiAnalysisService.scanAnalysis(BarInterval.H1)).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/ai/scan").param("interval", "H1"))
                .andExpect(status().isOk());

        verify(aiAnalysisService).scanAnalysis(BarInterval.H1);
    }

    @Test
    void scan_returns502OnServiceError() throws Exception {
        when(aiAnalysisService.scanAnalysis(any()))
                .thenThrow(new RuntimeException("scan failure"));

        mockMvc.perform(get("/api/v1/ai/scan"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("scan failure"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private AnalysisResult sampleResult(String symbol, Signal signal) {
        return new AnalysisResult(symbol, BarInterval.D1,
                "Market analysis summary.", signal,
                BigDecimal.valueOf(0.75),
                List.of("RSI oversold", "MACD bullish cross", "Low volatility"),
                Instant.now());
    }
}
