package com.trading.marketdata.controller;

import com.trading.marketdata.domain.*;
import com.trading.marketdata.service.MarketDataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/market-data")
public class MarketDataController {

    private final MarketDataService service;

    public MarketDataController(MarketDataService service) {
        this.service = service;
    }

    @GetMapping("/{symbol}/ohlcv")
    public ResponseEntity<List<OhlcvBar>> getOhlcv(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "D1") BarInterval interval,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(service.getOhlcv(symbol.toUpperCase(), interval, limit));
    }

    @GetMapping("/{symbol}/tick")
    public ResponseEntity<TickQuote> getTick(@PathVariable String symbol) {
        TickQuote tick = service.getTick(symbol.toUpperCase());
        return tick != null ? ResponseEntity.ok(tick) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{symbol}/indicators")
    public ResponseEntity<TechnicalIndicators> getIndicators(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "D1") BarInterval interval) {
        return ResponseEntity.ok(service.getIndicators(symbol.toUpperCase(), interval));
    }

    @GetMapping("/{symbol}/fundamental")
    public ResponseEntity<FundamentalData> getFundamental(@PathVariable String symbol) {
        FundamentalData fd = service.getFundamental(symbol.toUpperCase());
        return fd != null ? ResponseEntity.ok(fd) : ResponseEntity.notFound().build();
    }

    @GetMapping("/batch/tick")
    public ResponseEntity<Map<String, TickQuote>> getBatchTick(
            @RequestParam List<String> symbols) {
        List<String> upper = symbols.stream().map(String::toUpperCase).collect(Collectors.toList());
        return ResponseEntity.ok(service.getBatchTick(upper));
    }

    @PostMapping("/{symbol}/refresh")
    public ResponseEntity<Void> refresh(@PathVariable String symbol) {
        service.refresh(symbol.toUpperCase());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleError(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", e.getMessage()));
    }
}
