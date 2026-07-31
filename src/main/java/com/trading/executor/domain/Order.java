package com.trading.executor.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable snapshot of an order at a point in time.
 */
public record Order(
        String      orderId,
        String      symbol,
        OrderSide   side,
        OrderType   type,
        BigDecimal  quantity,
        BigDecimal  limitPrice,      // null for MARKET orders
        OrderStatus status,
        Instant     submittedAt,
        Instant     filledAt,        // null unless FILLED
        BigDecimal  filledPrice,     // null unless FILLED
        String      rejectionReason  // null unless REJECTED
) {}
