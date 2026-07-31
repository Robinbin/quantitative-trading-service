package com.trading.aianalysis.controller;

import com.trading.aianalysis.domain.AnalysisResult;
import com.trading.aianalysis.service.AIAnalysisService;
import com.trading.marketdata.domain.BarInterval;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/ai", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "AI Analysis", description = "GPT-4o powered market analysis and trading recommendations")
public class AIAnalysisController {

    private final AIAnalysisService aiAnalysisService;

    public AIAnalysisController(AIAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @GetMapping("/{symbol}/analyze")
    @Operation(
            summary = "Analyse symbol",
            description = "Uses GPT-4o to synthesise technical indicators, strategy signals, and risk metrics " +
                          "into a structured trading recommendation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analysis result returned"),
            @ApiResponse(responseCode = "502", description = "Upstream data or AI provider error")
    })
    public AnalysisResult analyzeSymbol(
            @PathVariable @Parameter(description = "Ticker symbol, e.g. AAPL") String symbol,
            @RequestParam(defaultValue = "D1")
            @Parameter(description = "Bar interval for indicator context") BarInterval interval) {
        return aiAnalysisService.analyzeSymbol(symbol.toUpperCase(), interval);
    }

    @GetMapping("/scan")
    @Operation(
            summary = "Scan watch-list",
            description = "Run AI analysis for every symbol in the configured watch-list. " +
                          "Failing symbols return a fallback HOLD result.")
    @ApiResponse(responseCode = "200", description = "Analysis map returned")
    public Map<String, AnalysisResult> scan(
            @RequestParam(defaultValue = "D1")
            @Parameter(description = "Bar interval used for all symbols") BarInterval interval) {
        return aiAnalysisService.scanAnalysis(interval);
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
