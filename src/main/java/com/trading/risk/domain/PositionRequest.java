package com.trading.risk.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Input DTO for a single position to assess.
 *
 * @param symbol     ticker symbol (e.g. "AAPL")
 * @param quantity   number of shares held (must be positive)
 * @param entryPrice average cost basis per share (must be positive)
 */
public record PositionRequest(
        @NotBlank String symbol,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        @NotNull @DecimalMin("0.0001") BigDecimal entryPrice
) {}
