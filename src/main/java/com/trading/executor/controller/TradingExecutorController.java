package com.trading.executor.controller;

import com.trading.executor.domain.Order;
import com.trading.executor.domain.OrderRequest;
import com.trading.executor.domain.Position;
import com.trading.executor.domain.TradingPortfolio;
import com.trading.executor.service.TradingExecutorService;
import com.trading.marketdata.domain.BarInterval;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/trading", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Trading Executor", description = "Paper-trading order submission, position tracking, and auto-execution")
public class TradingExecutorController {

    private final TradingExecutorService tradingExecutorService;

    public TradingExecutorController(TradingExecutorService tradingExecutorService) {
        this.tradingExecutorService = tradingExecutorService;
    }

    // ------------------------------------------------------------------
    // Orders
    // ------------------------------------------------------------------

    @PostMapping("/orders")
    @Operation(summary = "Submit order", description = "Submit a MARKET or LIMIT paper-trading order.")
    public Order submitOrder(@Valid @RequestBody OrderRequest request) {
        return tradingExecutorService.submitOrder(request);
    }

    @GetMapping("/orders")
    @Operation(summary = "List orders", description = "Return all orders submitted in this session, newest first.")
    public List<Order> listOrders() {
        return tradingExecutorService.listOrders();
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Get order", description = "Return a single order by ID.")
    public Order getOrder(
            @PathVariable @Parameter(description = "Order UUID") String id) {
        return tradingExecutorService.getOrder(id);
    }

    @DeleteMapping("/orders/{id}")
    @Operation(summary = "Cancel order", description = "Cancel a PENDING order.")
    public Order cancelOrder(
            @PathVariable @Parameter(description = "Order UUID") String id) {
        return tradingExecutorService.cancelOrder(id);
    }

    // ------------------------------------------------------------------
    // Positions & portfolio
    // ------------------------------------------------------------------

    @GetMapping("/positions")
    @Operation(summary = "List positions", description = "Return all open positions enriched with live price.")
    public List<Position> listPositions() {
        return tradingExecutorService.listPositions();
    }

    @GetMapping("/portfolio")
    @Operation(summary = "Portfolio snapshot", description = "Return cash balance + positions + aggregate P/L.")
    public TradingPortfolio getPortfolio() {
        return tradingExecutorService.getPortfolio();
    }

    // ------------------------------------------------------------------
    // Auto-execute
    // ------------------------------------------------------------------

    @PostMapping("/auto-execute")
    @Operation(
            summary = "Auto-execute strategy signals",
            description = "Run the strategy scan for the given interval and submit MARKET orders when the " +
                          "configured consensus threshold is met.")
    public List<Order> autoExecute(
            @RequestParam(defaultValue = "D1")
            @Parameter(description = "Bar interval for strategy evaluation") BarInterval interval) {
        return tradingExecutorService.autoExecute(interval);
    }

    // ------------------------------------------------------------------
    // Exception handlers
    // ------------------------------------------------------------------

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<Map<String, String>> handleValidation(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleError(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
