package com.trading.marketdata.controller;

import com.trading.marketdata.domain.*;
import com.trading.marketdata.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.anySet;

@WebMvcTest(MarketDataController.class)
class MarketDataControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MarketDataService service;

    // ── GET /ohlcv ────────────────────────────────────────────────────────────

    @Test
    void getOhlcv_shouldReturn200WithBars() throws Exception {
        when(service.getOhlcv("AAPL", BarInterval.D1, 100))
                .thenReturn(List.of(sampleBar()));

        mockMvc.perform(get("/api/v1/market-data/AAPL/ohlcv")
                        .param("interval", "D1")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].interval").value("D1"));
    }

    @Test
    void getOhlcv_shouldUseLowercaseSymbolAsUppercase() throws Exception {
        when(service.getOhlcv("AAPL", BarInterval.D1, 100)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/market-data/aapl/ohlcv"))
                .andExpect(status().isOk());

        verify(service).getOhlcv("AAPL", BarInterval.D1, 100);
    }

    @Test
    void getOhlcv_shouldUseDefaultIntervalAndLimit() throws Exception {
        when(service.getOhlcv("AAPL", BarInterval.D1, 100)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/market-data/AAPL/ohlcv"))
                .andExpect(status().isOk());

        verify(service).getOhlcv("AAPL", BarInterval.D1, 100);
    }

    // ── GET /tick ─────────────────────────────────────────────────────────────

    @Test
    void getTick_shouldReturn200WithQuote() throws Exception {
        when(service.getTick("AAPL")).thenReturn(sampleTick());

        mockMvc.perform(get("/api/v1/market-data/AAPL/tick"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getTick_shouldReturn404WhenServiceReturnsNull() throws Exception {
        when(service.getTick("UNKNOWN")).thenReturn(null);

        mockMvc.perform(get("/api/v1/market-data/UNKNOWN/tick"))
                .andExpect(status().isNotFound());
    }

    // ── GET /batch/tick ───────────────────────────────────────────────────────

    @Test
    void getBatchTick_shouldReturn200WithResultMap() throws Exception {
        when(service.getBatchTick(Set.of("AAPL", "TSLA")))
                .thenReturn(Map.of("AAPL", sampleTick(), "TSLA", sampleTick()));

        mockMvc.perform(get("/api/v1/market-data/batch/tick")
                        .param("symbols", "AAPL", "TSLA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AAPL.symbol").value("AAPL"));
    }

    @Test
    void getBatchTick_shouldReturn200WithEmptyMapWhenNoTicksFound() throws Exception {
        when(service.getBatchTick(Set.of("UNKNOWN"))).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/market-data/batch/tick")
                        .param("symbols", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(content().string("{}"));
    }

    @Test
    void getBatchTick_shouldConvertSymbolsToUppercase() throws Exception {
        when(service.getBatchTick(Set.of("AAPL"))).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/market-data/batch/tick")
                        .param("symbols", "aapl"))
                .andExpect(status().isOk());

        verify(service).getBatchTick(Set.of("AAPL"));
    }

    @Test
    void getBatchTick_shouldReturn400WhenSymbolsIsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/market-data/batch/tick"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /indicators ───────────────────────────────────────────────────────

    @Test
    void getIndicators_shouldReturn200WithIndicators() throws Exception {
        when(service.getIndicators("AAPL", BarInterval.D1)).thenReturn(sampleIndicators());

        mockMvc.perform(get("/api/v1/market-data/AAPL/indicators")
                        .param("interval", "D1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.interval").value("D1"));
    }

    @Test
    void getIndicators_shouldReturn200WithDefaultInterval() throws Exception {
        when(service.getIndicators("AAPL", BarInterval.D1)).thenReturn(sampleIndicators());

        mockMvc.perform(get("/api/v1/market-data/AAPL/indicators"))
                .andExpect(status().isOk());

        verify(service).getIndicators("AAPL", BarInterval.D1);
    }

    // ── GET /fundamental ──────────────────────────────────────────────────────

    @Test
    void getFundamental_shouldReturn200WithData() throws Exception {
        when(service.getFundamental("AAPL")).thenReturn(sampleFundamental());

        mockMvc.perform(get("/api/v1/market-data/AAPL/fundamental"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.companyName").value("Apple Inc."));
    }

    @Test
    void getFundamental_shouldReturn404WhenServiceReturnsNull() throws Exception {
        when(service.getFundamental("UNKNOWN")).thenReturn(null);

        mockMvc.perform(get("/api/v1/market-data/UNKNOWN/fundamental"))
                .andExpect(status().isNotFound());
    }

    // ── POST /refresh ─────────────────────────────────────────────────────────

    @Test
    void refresh_shouldReturn204() throws Exception {
        doNothing().when(service).refresh("AAPL");

        mockMvc.perform(post("/api/v1/market-data/AAPL/refresh"))
                .andExpect(status().isNoContent());

        verify(service).refresh("AAPL");
    }

    // ── exception handler ─────────────────────────────────────────────────────

    @Test
    void shouldReturn502WhenServiceThrows() throws Exception {
        when(service.getTick("ERROR")).thenThrow(new RuntimeException("upstream failure"));

        mockMvc.perform(get("/api/v1/market-data/ERROR/tick"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("upstream failure"));
    }

    // ── sample data ───────────────────────────────────────────────────────────

    private OhlcvBar sampleBar() {
        return new OhlcvBar("AAPL", Instant.now(), BarInterval.D1,
                new BigDecimal("180"), new BigDecimal("185"),
                new BigDecimal("178"), new BigDecimal("183"), 50_000_000L);
    }

    private TickQuote sampleTick() {
        return new TickQuote("AAPL", Instant.now(),
                new BigDecimal("183"), new BigDecimal("181"), new BigDecimal("185"),
                new BigDecimal("181"), new BigDecimal("181"), new BigDecimal("2"),
                new BigDecimal("0.011"), 50_000_000L, null, "USD", "NMS");
    }

    private TechnicalIndicators sampleIndicators() {
        return new TechnicalIndicators("AAPL", BarInterval.D1, Instant.now(),
                new BigDecimal("182"), null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private FundamentalData sampleFundamental() {
        return new FundamentalData("AAPL", "Apple Inc.", "Technology", "Consumer Electronics",
                new BigDecimal("28.5"), null, null, null, null, null,
                new BigDecimal("6.43"), null, new BigDecimal("1.26"),
                new BigDecimal("199.62"), new BigDecimal("124.17"), LocalDate.now());
    }
}
