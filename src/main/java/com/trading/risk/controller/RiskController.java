package com.trading.risk.controller;

import com.trading.marketdata.domain.BarInterval;
import com.trading.risk.domain.PortfolioRisk;
import com.trading.risk.domain.PositionRequest;
import com.trading.risk.domain.PositionRisk;
import com.trading.risk.domain.RiskMetrics;
import com.trading.risk.service.RiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
@RequestMapping(value = "/api/v1/risk", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "Risk Management", description = "Volatility, VaR, drawdown, and portfolio concentration analysis")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    // ------------------------------------------------------------------
    // Symbol metrics
    // ------------------------------------------------------------------

    @GetMapping("/{symbol}/metrics")
    @Operation(
            summary = "Symbol risk metrics",
            description = "Compute annualised volatility, VaR, max drawdown, Sharpe ratio, and risk level for a symbol.")
    public RiskMetrics getMetrics(
            @PathVariable @Parameter(description = "Ticker symbol, e.g. AAPL") String symbol,
            @RequestParam(defaultValue = "D1")
            @Parameter(description = "Bar interval for OHLCV history") BarInterval interval) {
        return riskService.assessSymbol(symbol, interval);
    }

    // ------------------------------------------------------------------
    // Position risk
    // ------------------------------------------------------------------

    @PostMapping("/position")
    @Operation(
            summary = "Assess position risk",
            description = "Evaluate risk for a single equity position given symbol, quantity, and entry price.")
    public PositionRisk assessPosition(@Valid @RequestBody PositionRequest request) {
        return riskService.assessPosition(request.symbol(), request.quantity(), request.entryPrice());
    }

    // ------------------------------------------------------------------
    // Portfolio risk
    // ------------------------------------------------------------------

    @PostMapping("/portfolio")
    @Operation(
            summary = "Assess portfolio risk",
            description = "Aggregate risk metrics across multiple positions, including diversified VaR, " +
                          "weighted beta, and concentration index (HHI).")
    public PortfolioRisk assessPortfolio(
            @NotEmpty @Valid @RequestBody List<PositionRequest> positions) {
        return riskService.assessPortfolio(positions);
    }

    // ------------------------------------------------------------------
    // Watch-list scan
    // ------------------------------------------------------------------

    @GetMapping("/scan")
    @Operation(
            summary = "Scan watch-list for risk",
            description = "Run symbol risk assessment for every symbol in the configured watch-list.")
    public Map<String, RiskMetrics> scanRisk(
            @RequestParam(defaultValue = "D1")
            @Parameter(description = "Bar interval used for all symbols") BarInterval interval) {
        return riskService.scanRisk(interval);
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
