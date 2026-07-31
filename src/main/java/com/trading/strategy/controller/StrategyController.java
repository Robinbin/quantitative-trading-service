package com.trading.strategy.controller;

import com.trading.marketdata.domain.BarInterval;
import com.trading.strategy.domain.StrategyDefinition;
import com.trading.strategy.domain.StrategyResult;
import com.trading.strategy.service.StrategyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Strategy", description = "Quantitative trading strategy evaluation")
@Validated
@RestController
@RequestMapping("/api/v1/strategy")
public class StrategyController {

    private final StrategyService service;

    public StrategyController(StrategyService service) {
        this.service = service;
    }

    @Operation(summary = "List available strategies", description = "Returns the name and description of all registered strategies")
    @ApiResponse(responseCode = "200", description = "Strategy list returned successfully")
    @GetMapping
    public ResponseEntity<List<StrategyDefinition>> listStrategies() {
        return ResponseEntity.ok(service.listStrategies());
    }

    @Operation(summary = "Evaluate a single strategy", description = "Runs the named strategy on the given symbol and interval")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Strategy evaluated successfully"),
        @ApiResponse(responseCode = "400", description = "Unknown strategy name"),
        @ApiResponse(responseCode = "502", description = "Market data provider error")
    })
    @GetMapping("/{symbol}/evaluate")
    public ResponseEntity<StrategyResult> evaluate(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String symbol,
            @Parameter(description = "Strategy name, e.g. RSI, MACD, MA_CROSS, BOLLINGER, MULTI")
                @RequestParam String strategy,
            @Parameter(description = "Bar interval: M1, M5, H1, D1")
                @RequestParam(defaultValue = "D1") BarInterval interval) {
        return ResponseEntity.ok(service.evaluate(symbol.toUpperCase(), strategy, interval));
    }

    @Operation(summary = "Evaluate all strategies", description = "Runs every registered strategy on the given symbol and interval")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "All strategies evaluated successfully"),
        @ApiResponse(responseCode = "502", description = "Market data provider error")
    })
    @GetMapping("/{symbol}/evaluate-all")
    public ResponseEntity<List<StrategyResult>> evaluateAll(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String symbol,
            @Parameter(description = "Bar interval: M1, M5, H1, D1")
                @RequestParam(defaultValue = "D1") BarInterval interval) {
        return ResponseEntity.ok(service.evaluateAll(symbol.toUpperCase(), interval));
    }

    @Operation(summary = "Scan watch-list", description = "Runs all strategies on every symbol in the configured watch-list")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Scan completed; symbols that failed return empty lists"),
        @ApiResponse(responseCode = "502", description = "Market data provider error")
    })
    @GetMapping("/scan")
    public ResponseEntity<Map<String, List<StrategyResult>>> scan(
            @Parameter(description = "Bar interval: M1, M5, H1, D1")
                @RequestParam(defaultValue = "D1") BarInterval interval) {
        return ResponseEntity.ok(service.scan(interval));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleError(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", e.getMessage()));
    }
}
