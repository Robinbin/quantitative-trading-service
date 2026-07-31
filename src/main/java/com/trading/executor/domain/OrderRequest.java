package com.trading.executor.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Input DTO for submitting a new order.
 *
 * @param symbol     ticker symbol (required, e.g. "AAPL")
 * @param side       BUY or SELL
 * @param type       MARKET or LIMIT
 * @param quantity   number of shares (must be positive)
 * @param limitPrice limit price; required for LIMIT orders, ignored for MARKET
 */
public record OrderRequest(
        @NotBlank String     symbol,
        @NotNull  OrderSide  side,
        @NotNull  OrderType  type,
        @NotNull @DecimalMin(value = "0.0001", message = "quantity must be positive") BigDecimal quantity,
        BigDecimal           limitPrice
) {}
