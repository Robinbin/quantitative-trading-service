package com.trading.executor.service;

import com.trading.executor.domain.Order;
import com.trading.executor.domain.OrderRequest;
import com.trading.executor.domain.Position;
import com.trading.executor.domain.TradingPortfolio;
import com.trading.marketdata.domain.BarInterval;

import java.util.List;

public interface TradingExecutorService {

    /**
     * Submit a paper-trading order.  MARKET orders fill immediately at the current market price.
     * LIMIT orders are placed as PENDING if the price condition is not met.
     *
     * @throws IllegalArgumentException if the order fails cross-field validation (e.g. LIMIT without limitPrice)
     */
    Order submitOrder(OrderRequest request);

    /** Return all orders ever submitted in this session, newest first. */
    List<Order> listOrders();

    /**
     * @throws IllegalArgumentException if the orderId is unknown
     */
    Order getOrder(String orderId);

    /**
     * Cancel a PENDING order.
     *
     * @throws IllegalArgumentException if the order is not found or is not in PENDING status
     */
    Order cancelOrder(String orderId);

    /** Return all open positions enriched with the current live price. */
    List<Position> listPositions();

    /** Return the complete portfolio snapshot (cash + positions). */
    TradingPortfolio getPortfolio();

    /**
     * Run the full strategy scan and automatically submit MARKET orders when the configured
     * consensus threshold is reached.  Symbols with exceptions are skipped silently.
     *
     * @return list of all orders submitted during this invocation
     */
    List<Order> autoExecute(BarInterval interval);
}
