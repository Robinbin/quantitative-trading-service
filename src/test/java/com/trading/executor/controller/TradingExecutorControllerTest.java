package com.trading.executor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.executor.domain.Order;
import com.trading.executor.domain.OrderRequest;
import com.trading.executor.domain.OrderSide;
import com.trading.executor.domain.OrderStatus;
import com.trading.executor.domain.OrderType;
import com.trading.executor.domain.Position;
import com.trading.executor.domain.TradingPortfolio;
import com.trading.executor.service.TradingExecutorService;
import com.trading.marketdata.domain.BarInterval;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradingExecutorController.class)
class TradingExecutorControllerTest {

    @Autowired MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean TradingExecutorService tradingExecutorService;

    // ------------------------------------------------------------------
    // POST /orders
    // ------------------------------------------------------------------

    @Test
    void submitOrder_returns200WithFilledOrder() throws Exception {
        when(tradingExecutorService.submitOrder(any())).thenReturn(sampleOrder(OrderStatus.FILLED));

        mockMvc.perform(post("/api/v1/trading/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                                        BigDecimal.valueOf(10), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.status").value("FILLED"));
    }

    @Test
    void submitOrder_returns400_forBlankSymbol() throws Exception {
        mockMvc.perform(post("/api/v1/trading/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"\",\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitOrder_returns400_forNullSide() throws Exception {
        mockMvc.perform(post("/api/v1/trading/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"type\":\"MARKET\",\"quantity\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitOrder_returns400_forZeroQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/trading/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitOrder_returns400_forIllegalArgumentException() throws Exception {
        when(tradingExecutorService.submitOrder(any()))
                .thenThrow(new IllegalArgumentException("limitPrice is required"));

        mockMvc.perform(post("/api/v1/trading/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT,
                                        BigDecimal.valueOf(10), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("limitPrice is required"));
    }

    // ------------------------------------------------------------------
    // GET /orders
    // ------------------------------------------------------------------

    @Test
    void listOrders_returns200WithList() throws Exception {
        when(tradingExecutorService.listOrders())
                .thenReturn(List.of(sampleOrder(OrderStatus.FILLED), sampleOrder(OrderStatus.PENDING)));

        mockMvc.perform(get("/api/v1/trading/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ------------------------------------------------------------------
    // GET /orders/{id}
    // ------------------------------------------------------------------

    @Test
    void getOrder_returns200() throws Exception {
        when(tradingExecutorService.getOrder("test-id")).thenReturn(sampleOrder(OrderStatus.FILLED));

        mockMvc.perform(get("/api/v1/trading/orders/test-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    void getOrder_returns400_forUnknownId() throws Exception {
        when(tradingExecutorService.getOrder(anyString()))
                .thenThrow(new IllegalArgumentException("Order not found: xyz"));

        mockMvc.perform(get("/api/v1/trading/orders/xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Order not found: xyz"));
    }

    // ------------------------------------------------------------------
    // DELETE /orders/{id}
    // ------------------------------------------------------------------

    @Test
    void cancelOrder_returns200WithCancelledOrder() throws Exception {
        when(tradingExecutorService.cancelOrder("test-id")).thenReturn(sampleOrder(OrderStatus.CANCELLED));

        mockMvc.perform(delete("/api/v1/trading/orders/test-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ------------------------------------------------------------------
    // GET /positions
    // ------------------------------------------------------------------

    @Test
    void listPositions_returns200WithList() throws Exception {
        when(tradingExecutorService.listPositions()).thenReturn(List.of(samplePosition()));

        mockMvc.perform(get("/api/v1/trading/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));
    }

    // ------------------------------------------------------------------
    // GET /portfolio
    // ------------------------------------------------------------------

    @Test
    void getPortfolio_returns200() throws Exception {
        when(tradingExecutorService.getPortfolio()).thenReturn(samplePortfolio());

        mockMvc.perform(get("/api/v1/trading/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(99000.0));
    }

    // ------------------------------------------------------------------
    // POST /auto-execute
    // ------------------------------------------------------------------

    @Test
    void autoExecute_returns200WithExecutedOrders() throws Exception {
        when(tradingExecutorService.autoExecute(BarInterval.D1))
                .thenReturn(List.of(sampleOrder(OrderStatus.FILLED)));

        mockMvc.perform(post("/api/v1/trading/auto-execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FILLED"));
    }

    @Test
    void autoExecute_returns200WithIntervalParam() throws Exception {
        when(tradingExecutorService.autoExecute(BarInterval.H1)).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/trading/auto-execute").param("interval", "H1"))
                .andExpect(status().isOk());
    }

    @Test
    void autoExecute_returns502OnServiceError() throws Exception {
        when(tradingExecutorService.autoExecute(any()))
                .thenThrow(new RuntimeException("strategy service unavailable"));

        mockMvc.perform(post("/api/v1/trading/auto-execute"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("strategy service unavailable"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Order sampleOrder(OrderStatus status) {
        return new Order(
                "test-id", "AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(10), null,
                status, Instant.now(),
                status == OrderStatus.FILLED ? Instant.now() : null,
                status == OrderStatus.FILLED ? BigDecimal.valueOf(150) : null,
                status == OrderStatus.REJECTED ? "reason" : null);
    }

    private Position samplePosition() {
        return new Position(
                "AAPL", BigDecimal.valueOf(10), BigDecimal.valueOf(150),
                BigDecimal.valueOf(160), BigDecimal.valueOf(1600),
                BigDecimal.valueOf(100), BigDecimal.valueOf(0.067));
    }

    private TradingPortfolio samplePortfolio() {
        return new TradingPortfolio(
                BigDecimal.valueOf(99_000),
                List.of(samplePosition()),
                BigDecimal.valueOf(1600),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(100_600),
                Instant.now());
    }
}
