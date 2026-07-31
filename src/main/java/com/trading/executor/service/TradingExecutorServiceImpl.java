package com.trading.executor.service;

import com.trading.executor.config.TradingExecutorProperties;
import com.trading.executor.domain.Order;
import com.trading.executor.domain.OrderRequest;
import com.trading.executor.domain.OrderSide;
import com.trading.executor.domain.OrderStatus;
import com.trading.executor.domain.OrderType;
import com.trading.executor.domain.Position;
import com.trading.executor.domain.TradingPortfolio;
import com.trading.marketdata.config.MarketDataProperties;
import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.service.MarketDataService;
import com.trading.risk.domain.RiskLevel;
import com.trading.risk.service.RiskService;
import com.trading.strategy.domain.Signal;
import com.trading.strategy.domain.StrategyResult;
import com.trading.strategy.service.StrategyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TradingExecutorServiceImpl implements TradingExecutorService {

    private static final Logger log = LoggerFactory.getLogger(TradingExecutorServiceImpl.class);
    private static final int    MONEY_SCALE = 4;

    private final MarketDataService         marketDataService;
    private final StrategyService           strategyService;
    private final RiskService               riskService;
    private final TradingExecutorProperties props;
    private final MarketDataProperties      marketDataProps;

    // ------------------------------------------------------------------
    // In-memory paper-trading state
    // ------------------------------------------------------------------

    /** All orders ever submitted, keyed by orderId. */
    private final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();

    /** Open positions keyed by symbol. */
    private final ConcurrentHashMap<String, PositionState> positionStore = new ConcurrentHashMap<>();

    /** Cash balance. */
    private final AtomicReference<BigDecimal> cashBalance;

    /** Compact internal record to hold mutable position state. */
    private record PositionState(BigDecimal quantity, BigDecimal averageCost) {}

    public TradingExecutorServiceImpl(MarketDataService marketDataService,
                                      StrategyService strategyService,
                                      RiskService riskService,
                                      TradingExecutorProperties props,
                                      MarketDataProperties marketDataProps) {
        this.marketDataService = marketDataService;
        this.strategyService   = strategyService;
        this.riskService       = riskService;
        this.props             = props;
        this.marketDataProps   = marketDataProps;
        this.cashBalance       = new AtomicReference<>(BigDecimal.valueOf(props.initialCash()));
    }

    // ------------------------------------------------------------------
    // TradingExecutorService implementation
    // ------------------------------------------------------------------

    @Override
    public synchronized Order submitOrder(OrderRequest request) {
        String    symbol = request.symbol().toUpperCase();
        var       tick   = marketDataService.getTick(symbol);
        if (tick == null) {
            throw new IllegalStateException("No market data available for symbol: " + symbol);
        }
        BigDecimal currentPrice = tick.price();
        Instant    now          = Instant.now();

        // Cross-field validation: LIMIT orders require a limitPrice
        if (request.type() == OrderType.LIMIT && request.limitPrice() == null) {
            throw new IllegalArgumentException("limitPrice is required for LIMIT orders");
        }

        // Determine effective fill price (null = condition not yet met → PENDING)
        BigDecimal fillPrice = determineFillPrice(request, currentPrice);

        Order order;
        if (fillPrice != null) {
            // Attempt to fill
            if (request.side() == OrderSide.BUY) {
                order = executeBuy(symbol, request, fillPrice, now);
            } else {
                order = executeSell(symbol, request, fillPrice, now);
            }
        } else {
            // Limit condition not met — place as PENDING
            order = buildOrder(symbol, request, OrderStatus.PENDING, null, null, null, now);
        }

        orders.put(order.orderId(), order);
        return order;
    }

    @Override
    public synchronized List<Order> listOrders() {
        return orders.values().stream()
                .sorted(Comparator.comparing(Order::submittedAt).reversed())
                .toList();
    }

    @Override
    public Order getOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found: " + orderId);
        return order;
    }

    @Override
    public synchronized Order cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found: " + orderId);
        if (order.status() != OrderStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Cannot cancel order in status: " + order.status() + " (orderId=" + orderId + ")");
        }
        Order cancelled = new Order(order.orderId(), order.symbol(), order.side(), order.type(),
                order.quantity(), order.limitPrice(), OrderStatus.CANCELLED,
                order.submittedAt(), null, null, null);
        orders.put(orderId, cancelled);
        return cancelled;
    }

    @Override
    public synchronized List<Position> listPositions() {
        return positionStore.entrySet().stream()
                .map(e -> enrichPosition(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public TradingPortfolio getPortfolio() {
        List<Position> positions = listPositions();
        BigDecimal totalMarketValue = positions.stream()
                .map(Position::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalPnl = positions.stream()
                .map(Position::unrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal cash       = cashBalance.get();
        BigDecimal totalValue = cash.add(totalMarketValue).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new TradingPortfolio(cash, positions, totalMarketValue, totalPnl, totalValue, Instant.now());
    }

    @Override
    public synchronized List<Order> autoExecute(BarInterval interval) {
        Map<String, List<StrategyResult>> scanResults = strategyService.scan(interval);
        List<Order> executed = new ArrayList<>();

        for (String symbol : marketDataProps.watchList()) {
            try {
                List<StrategyResult> results = scanResults.getOrDefault(symbol, List.of());
                processAutoTradeSignals(symbol, interval, results, executed);
            } catch (Exception e) {
                log.warn("Auto-execute failed for {}: {}", symbol, e.getMessage());
            }
        }
        return executed;
    }

    // ------------------------------------------------------------------
    // Private helpers — order execution
    // ------------------------------------------------------------------

    private BigDecimal determineFillPrice(OrderRequest req, BigDecimal currentPrice) {
        if (req.type() == OrderType.MARKET) return currentPrice;
        // LIMIT BUY: fill when market price <= limit price
        if (req.side() == OrderSide.BUY && currentPrice.compareTo(req.limitPrice()) <= 0) return currentPrice;
        // LIMIT SELL: fill when market price >= limit price
        if (req.side() == OrderSide.SELL && currentPrice.compareTo(req.limitPrice()) >= 0) return currentPrice;
        return null; // condition not met → PENDING
    }

    private Order executeBuy(String symbol, OrderRequest req, BigDecimal fillPrice, Instant now) {
        BigDecimal cashRequired = fillPrice.multiply(req.quantity())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal cash = cashBalance.get();
        if (cashRequired.compareTo(cash) > 0) {
            return buildOrder(symbol, req, OrderStatus.REJECTED, null, null,
                    "Insufficient cash: required " + cashRequired + ", available " + cash, now);
        }
        // Deduct cash atomically — same pattern as executeSell
        cashBalance.updateAndGet(c -> c.subtract(cashRequired).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        // Update position
        positionStore.merge(symbol,
                new PositionState(req.quantity(), fillPrice),
                (existing, incoming) -> {
                    BigDecimal newQty = existing.quantity().add(incoming.quantity());
                    BigDecimal newAvgCost = existing.quantity().multiply(existing.averageCost())
                            .add(incoming.quantity().multiply(incoming.averageCost()))
                            .divide(newQty, MONEY_SCALE, RoundingMode.HALF_UP);
                    return new PositionState(newQty, newAvgCost);
                });
        return buildOrder(symbol, req, OrderStatus.FILLED, now, fillPrice, null, now);
    }

    private Order executeSell(String symbol, OrderRequest req, BigDecimal fillPrice, Instant now) {
        PositionState existing = positionStore.get(symbol);
        if (existing == null || existing.quantity().compareTo(req.quantity()) < 0) {
            return buildOrder(symbol, req, OrderStatus.REJECTED, null, null,
                    "Insufficient shares: have "
                    + (existing != null ? existing.quantity() : "0")
                    + ", selling " + req.quantity(), now);
        }
        // Add proceeds
        BigDecimal proceeds = fillPrice.multiply(req.quantity())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        cashBalance.updateAndGet(c -> c.add(proceeds).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        // Reduce or remove position
        int cmp = existing.quantity().compareTo(req.quantity());
        if (cmp == 0) {
            positionStore.remove(symbol);
        } else {
            BigDecimal remaining = existing.quantity().subtract(req.quantity());
            positionStore.put(symbol, new PositionState(remaining, existing.averageCost()));
        }
        return buildOrder(symbol, req, OrderStatus.FILLED, now, fillPrice, null, now);
    }

    private Order buildOrder(String symbol, OrderRequest req, OrderStatus status,
                             Instant filledAt, BigDecimal filledPrice,
                             String rejectionReason, Instant submittedAt) {
        return new Order(
                UUID.randomUUID().toString(),
                symbol,
                req.side(), req.type(), req.quantity(), req.limitPrice(),
                status, submittedAt, filledAt, filledPrice, rejectionReason);
    }

    // ------------------------------------------------------------------
    // Private helpers — position enrichment
    // ------------------------------------------------------------------

    private Position enrichPosition(String symbol, PositionState state) {
        BigDecimal currentPrice;
        try {
            var tick = marketDataService.getTick(symbol);
            if (tick == null || tick.price() == null) {
                throw new IllegalStateException("no price returned");
            }
            currentPrice = tick.price();
        } catch (Exception e) {
            log.warn("Could not fetch live price for {} during position enrichment — using avg cost", symbol);
            currentPrice = state.averageCost();
        }
        BigDecimal marketValue   = currentPrice.multiply(state.quantity())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal costBasis     = state.averageCost().multiply(state.quantity())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal unrealizedPnl = marketValue.subtract(costBasis)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pnlPct        = costBasis.compareTo(BigDecimal.ZERO) != 0
                ? unrealizedPnl.divide(costBasis, MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new Position(symbol, state.quantity(), state.averageCost(),
                currentPrice, marketValue, unrealizedPnl, pnlPct);
    }

    // ------------------------------------------------------------------
    // Private helpers — auto-execute logic
    // ------------------------------------------------------------------

    private void processAutoTradeSignals(String symbol, BarInterval interval,
                                         List<StrategyResult> results, List<Order> executed) {
        long buyCount  = results.stream().filter(r -> r.signal() == Signal.BUY).count();
        long sellCount = results.stream().filter(r -> r.signal() == Signal.SELL).count();

        double avgBuyConf = results.stream()
                .filter(r -> r.signal() == Signal.BUY)
                .mapToDouble(r -> r.confidence().doubleValue())
                .average().orElse(0.0);

        double avgSellConf = results.stream()
                .filter(r -> r.signal() == Signal.SELL)
                .mapToDouble(r -> r.confidence().doubleValue())
                .average().orElse(0.0);

        // --- BUY decision ---
        if (buyCount >= props.minStrategiesAgree() && avgBuyConf >= props.minConfidence()) {
            RiskLevel riskLevel = riskService.assessSymbol(symbol, interval).riskLevel();
            if (riskLevel != RiskLevel.CRITICAL && !positionStore.containsKey(symbol)) {
                var        tickData = marketDataService.getTick(symbol);
                if (tickData == null) {
                    log.warn("No market data for {} — skipping auto-execute BUY", symbol);
                    return;
                }
                BigDecimal price  = tickData.price();
                BigDecimal budget = cashBalance.get()
                        .min(BigDecimal.valueOf(props.maxOrderValue()));
                BigDecimal qty    = budget.divide(price, MONEY_SCALE, RoundingMode.HALF_UP);
                if (qty.compareTo(BigDecimal.ZERO) > 0) {
                    Order order = submitOrder(new OrderRequest(symbol, OrderSide.BUY, OrderType.MARKET, qty, null));
                    executed.add(order);
                    log.info("Auto-execute BUY {} qty={} (buyCount={}, avgConf={})",
                            symbol, qty, buyCount, avgBuyConf);
                }
            }
        }

        // --- SELL decision ---
        if (sellCount >= props.minStrategiesAgree() && avgSellConf >= props.minConfidence()) {
            PositionState pos = positionStore.get(symbol);
            if (pos != null) {
                Order order = submitOrder(
                        new OrderRequest(symbol, OrderSide.SELL, OrderType.MARKET, pos.quantity(), null));
                executed.add(order);
                log.info("Auto-execute SELL {} qty={} (sellCount={}, avgConf={})",
                        symbol, pos.quantity(), sellCount, avgSellConf);
            }
        }
    }
}
