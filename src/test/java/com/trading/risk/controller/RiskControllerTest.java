package com.trading.risk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.marketdata.domain.BarInterval;
import com.trading.risk.domain.PortfolioRisk;
import com.trading.risk.domain.PositionRequest;
import com.trading.risk.domain.PositionRisk;
import com.trading.risk.domain.RiskLevel;
import com.trading.risk.domain.RiskMetrics;
import com.trading.risk.service.RiskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskController.class)
class RiskControllerTest {

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean RiskService riskService;

    // ------------------------------------------------------------------
    // GET /{symbol}/metrics
    // ------------------------------------------------------------------

    @Test
    void getMetrics_returns200WithRiskMetrics() throws Exception {
        when(riskService.assessSymbol("AAPL", BarInterval.D1)).thenReturn(sampleMetrics("AAPL"));

        mockMvc.perform(get("/api/v1/risk/AAPL/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"));
    }

    @Test
    void getMetrics_passesIntervalParam() throws Exception {
        when(riskService.assessSymbol("AAPL", BarInterval.H1)).thenReturn(sampleMetrics("AAPL"));

        mockMvc.perform(get("/api/v1/risk/AAPL/metrics").param("interval", "H1"))
                .andExpect(status().isOk());
    }

    @Test
    void getMetrics_returns502OnServiceError() throws Exception {
        when(riskService.assessSymbol(anyString(), any()))
                .thenThrow(new RuntimeException("upstream error"));

        mockMvc.perform(get("/api/v1/risk/AAPL/metrics"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("upstream error"));
    }

    // ------------------------------------------------------------------
    // POST /position
    // ------------------------------------------------------------------

    @Test
    void assessPosition_returns200WithPositionRisk() throws Exception {
        when(riskService.assessPosition(anyString(), any(), any())).thenReturn(samplePositionRisk("AAPL"));

        mockMvc.perform(post("/api/v1/risk/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PositionRequest("AAPL", BigDecimal.TEN, BigDecimal.valueOf(150)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"));
    }

    @Test
    void assessPosition_returns400_forMissingSymbol() throws Exception {
        mockMvc.perform(post("/api/v1/risk/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":10,\"entryPrice\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assessPosition_returns400_forNegativeQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/risk/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"quantity\":-1,\"entryPrice\":100}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // POST /portfolio
    // ------------------------------------------------------------------

    @Test
    void assessPortfolio_returns200WithPortfolioRisk() throws Exception {
        when(riskService.assessPortfolio(any())).thenReturn(samplePortfolioRisk());

        List<PositionRequest> positions = List.of(
                new PositionRequest("AAPL", BigDecimal.TEN, BigDecimal.valueOf(150)),
                new PositionRequest("TSLA", BigDecimal.valueOf(5), BigDecimal.valueOf(200)));

        mockMvc.perform(post("/api/v1/risk/portfolio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(positions)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallRiskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.positions").isArray());
    }

    @Test
    void assessPortfolio_returns400_forEmptyList() throws Exception {
        mockMvc.perform(post("/api/v1/risk/portfolio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // GET /scan
    // ------------------------------------------------------------------

    @Test
    void scanRisk_returns200WithMap() throws Exception {
        when(riskService.scanRisk(BarInterval.D1))
                .thenReturn(Map.of("AAPL", sampleMetrics("AAPL"), "TSLA", sampleMetrics("TSLA")));

        mockMvc.perform(get("/api/v1/risk/scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AAPL.symbol").value("AAPL"))
                .andExpect(jsonPath("$.TSLA.symbol").value("TSLA"));
    }

    @Test
    void scanRisk_passesIntervalParam() throws Exception {
        when(riskService.scanRisk(BarInterval.H1)).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/risk/scan").param("interval", "H1"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RiskMetrics sampleMetrics(String symbol) {
        return new RiskMetrics(symbol, BarInterval.D1,
                BigDecimal.valueOf(180),
                BigDecimal.valueOf(0.25),
                BigDecimal.valueOf(1.1),
                BigDecimal.valueOf(1.2),
                BigDecimal.valueOf(0.15),
                BigDecimal.valueOf(5.0),
                RiskLevel.MEDIUM,
                Instant.now());
    }

    private PositionRisk samplePositionRisk(String symbol) {
        return new PositionRisk(symbol,
                BigDecimal.TEN,
                BigDecimal.valueOf(150),
                BigDecimal.valueOf(180),
                BigDecimal.valueOf(1800),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(0.20),
                BigDecimal.valueOf(142.5),
                BigDecimal.valueOf(172.5),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(0.25),
                BigDecimal.valueOf(1.1),
                RiskLevel.MEDIUM,
                List.of(),
                Instant.now());
    }

    private PortfolioRisk samplePortfolioRisk() {
        return new PortfolioRisk(
                List.of(samplePositionRisk("AAPL"), samplePositionRisk("TSLA")),
                BigDecimal.valueOf(2600),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(0.13),
                BigDecimal.valueOf(70),
                BigDecimal.valueOf(1.4),
                BigDecimal.valueOf(0.52),
                RiskLevel.MEDIUM,
                List.of(),
                Instant.now());
    }
}
