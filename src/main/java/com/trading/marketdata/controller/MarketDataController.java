package com.trading.marketdata.controller;

import com.trading.marketdata.domain.*;
import com.trading.marketdata.service.MarketDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "Market Data", description = "Real-time quotes, OHLCV bars, technical indicators and fundamental data")
@Validated
@RestController
@RequestMapping("/api/v1/market-data")
public class MarketDataController {

    private final MarketDataService service;

    public MarketDataController(MarketDataService service) {
        this.service = service;
    }

    @Operation(summary = "Get OHLCV bars", description = "Returns candlestick bars for the given symbol, interval and limit")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bars returned successfully"),
        @ApiResponse(responseCode = "502", description = "Upstream data provider error")
    })
    @GetMapping("/{symbol}/ohlcv")
    public ResponseEntity<List<OhlcvBar>> getOhlcv(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String symbol,
            @Parameter(description = "Bar interval: M1, M5, H1, D1") @RequestParam(defaultValue = "D1") BarInterval interval,
            @Parameter(description = "Max number of bars to return (1–500)") @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(service.getOhlcv(symbol.toUpperCase(), interval, limit));
    }

    @Operation(summary = "Get real-time tick quote", description = "Returns the latest quote for a single symbol")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quote returned successfully"),
        @ApiResponse(responseCode = "404", description = "Symbol not found"),
        @ApiResponse(responseCode = "502", description = "Upstream data provider error")
    })
    @GetMapping("/{symbol}/tick")
    public ResponseEntity<TickQuote> getTick(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String symbol) {
        TickQuote tick = service.getTick(symbol.toUpperCase());
        return tick != null ? ResponseEntity.ok(tick) : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Get technical indicators", description = "Returns MA, EMA, MACD, RSI and Bollinger Bands for the given symbol and interval")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Indicators returned successfully"),
        @ApiResponse(responseCode = "502", description = "Upstream data provider error")
    })
    @GetMapping("/{symbol}/indicators")
    public ResponseEntity<TechnicalIndicators> getIndicators(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String symbol,
            @Parameter(description = "Bar interval: M1, M5, H1, D1") @RequestParam(defaultValue = "D1") BarInterval interval) {
        return ResponseEntity.ok(service.getIndicators(symbol.toUpperCase(), interval));
    }

    @Operation(summary = "Get fundamental data", description = "Returns P/E, P/B, EPS, revenue and other fundamental metrics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fundamental data returned successfully"),
        @ApiResponse(responseCode = "404", description = "Symbol not found"),
        @ApiResponse(responseCode = "502", description = "Upstream data provider error")
    })
    @GetMapping("/{symbol}/fundamental")
    public ResponseEntity<FundamentalData> getFundamental(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String symbol) {
        FundamentalData fd = service.getFundamental(symbol.toUpperCase());
        return fd != null ? ResponseEntity.ok(fd) : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Batch get tick quotes", description = "Returns quotes for multiple symbols in one request (1–50 symbols)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quotes returned; symbols not found are omitted from the map"),
        @ApiResponse(responseCode = "400", description = "Invalid symbols parameter"),
        @ApiResponse(responseCode = "502", description = "Upstream data provider error")
    })
    @GetMapping("/batch/tick")
    public ResponseEntity<Map<String, TickQuote>> getBatchTick(
            @Parameter(description = "Comma-separated ticker symbols, e.g. AAPL,TSLA,MSFT")
            @RequestParam @Size(min = 1, max = 50, message = "symbols must contain between 1 and 50 entries") List<String> symbols) {
        Set<String> upper = Set.copyOf(symbols.stream().map(String::toUpperCase).toList());
        return ResponseEntity.ok(service.getBatchTick(upper));
    }

    @Operation(summary = "Refresh market data", description = "Evicts cache and pre-warms all data for the given symbol")
    @ApiResponse(responseCode = "204", description = "Refresh triggered successfully")
    @PostMapping("/{symbol}/refresh")
    public ResponseEntity<Void> refresh(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String symbol) {
        service.refresh(symbol.toUpperCase());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParam(
            org.springframework.web.bind.MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(jakarta.validation.ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst().orElse(e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleError(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", e.getMessage()));
    }
}
